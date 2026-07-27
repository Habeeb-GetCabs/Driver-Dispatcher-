package com.example.viewmodel

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.IlaiyaraajaRingtonePlayer
import com.example.data.model.DispatchOrder
import com.example.data.model.DispatchStatus
import com.example.data.model.DriverProfile
import com.example.data.model.DriverUnit
import com.example.data.preferences.DriverProfileRepository
import com.example.data.preferences.SettingsRepository
import com.example.data.remote.FirebaseSyncManager
import com.example.service.DispatchAlertAction
import com.example.service.DispatchAlertController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AppRole {
    DRIVER,
    DISPATCHER
}

class DispatchViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = DriverProfileRepository(application)

    private val _currentRole = MutableStateFlow(AppRole.DRIVER)
    val currentRole: StateFlow<AppRole> = _currentRole.asStateFlow()

    private val _driverProfile = MutableStateFlow(DriverProfile())
    val driverProfile: StateFlow<DriverProfile> = _driverProfile.asStateFlow()

    private val _isDispatcherAuthenticated = MutableStateFlow(false)
    val isDispatcherAuthenticated: StateFlow<Boolean> = _isDispatcherAuthenticated.asStateFlow()

    private val _adminPin = MutableStateFlow("1403")
    val adminPin: StateFlow<String> = _adminPin.asStateFlow()

    private val _currentAdmin = MutableStateFlow<com.example.data.model.AdminAccount?>(null)
    val currentAdmin: StateFlow<com.example.data.model.AdminAccount?> = _currentAdmin.asStateFlow()

    private val _adminAccounts = MutableStateFlow<List<com.example.data.model.AdminAccount>>(emptyList())
    val adminAccounts: StateFlow<List<com.example.data.model.AdminAccount>> = _adminAccounts.asStateFlow()

    private val _dispatchHistory = MutableStateFlow<List<DispatchOrder>>(emptyList())
    val dispatchHistory: StateFlow<List<DispatchOrder>> = _dispatchHistory.asStateFlow()

    private val _dispatchOrders = MutableStateFlow<List<DispatchOrder>>(emptyList())
    val dispatchOrders: StateFlow<List<DispatchOrder>> = _dispatchOrders.asStateFlow()

    private val _activeAlertTrip = MutableStateFlow<DispatchOrder?>(null)
    val activeAlertTrip: StateFlow<DispatchOrder?> = _activeAlertTrip.asStateFlow()

    private val _acceptedEnRouteTrip = MutableStateFlow<DispatchOrder?>(null)
    val acceptedEnRouteTrip: StateFlow<DispatchOrder?> = _acceptedEnRouteTrip.asStateFlow()

    private val _fleetDrivers = MutableStateFlow<List<DriverUnit>>(emptyList())
    val fleetDrivers: StateFlow<List<DriverUnit>> = _fleetDrivers.asStateFlow()

    private val _driverBroadcastMessage = MutableStateFlow<String?>(null)
    val driverBroadcastMessage: StateFlow<String?> = _driverBroadcastMessage.asStateFlow()

    private var toneGenerator: ToneGenerator? = null
    private val remoteFleetUnits = mutableListOf<DriverUnit>()
    private val alertedOrderIds = mutableSetOf<String>()

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            Log.e("DispatchViewModel", "Failed to init ToneGenerator: ${e.message}")
        }

        viewModelScope.launch {
            try {
                profileRepository.ensureDriverIdAssigned()
            } catch (e: Exception) {
                Log.e("DispatchViewModel", "Failed to ensure sequential Driver ID: ${e.message}")
            }
            profileRepository.driverProfileFlow.collect { profile ->
                _driverProfile.value = profile
                FirebaseSyncManager.syncDriverProfile(profile)
                refreshFleetList(profile)
            }
        }

        // Ensure default Master Admin account exists in Firebase
        val defaultMaster = com.example.data.model.AdminAccount(
            adminId = "master",
            name = "Master Admin (Owner)",
            pin = "1403",
            role = "MASTER_ADMIN"
        )
        FirebaseSyncManager.syncAdminAccount(defaultMaster)

        // Listen for action events from DispatchAlertService (Notification actions & 30s timeout)
        viewModelScope.launch {
            DispatchAlertController.actionFlow.collect { action ->
                when (action) {
                    is DispatchAlertAction.Accept -> acceptTrip(action.orderId)
                    is DispatchAlertAction.Decline -> declineTrip(action.orderId)
                    is DispatchAlertAction.Timeout -> {
                        if (_activeAlertTrip.value?.orderId == action.orderId) {
                            _activeAlertTrip.value = null
                            _driverBroadcastMessage.value = "Trip #${action.orderId} alert timed out (30s)."
                        }
                    }
                }
            }
        }

        // Live observe admin accounts from Firebase Realtime DB
        viewModelScope.launch {
            try {
                FirebaseSyncManager.observeAdmins().collect { adminsList ->
                    _adminAccounts.value = adminsList
                }
            } catch (e: Exception) {
                Log.e("DispatchViewModel", "Error observing admins: ${e.message}")
            }
        }

        // Live observe dispatch trip history from Firebase Realtime DB
        viewModelScope.launch {
            try {
                FirebaseSyncManager.observeDispatchHistory().collect { historyList ->
                    _dispatchHistory.value = historyList
                }
            } catch (e: Exception) {
                Log.e("DispatchViewModel", "Error observing dispatch history: ${e.message}")
            }
        }

        viewModelScope.launch {
            profileRepository.adminPinFlow.collect { pin ->
                _adminPin.value = pin
            }
        }

        // Live observe fleet drivers across all devices via Firebase
        viewModelScope.launch {
            try {
                FirebaseSyncManager.observeFleetDrivers().collect { liveDrivers ->
                    remoteFleetUnits.clear()
                    remoteFleetUnits.addAll(liveDrivers)
                    refreshFleetList(_driverProfile.value)
                }
            } catch (e: Exception) {
                Log.e("DispatchViewModel", "Error observing fleet drivers: ${e.message}")
            }
        }

        // Live observe dispatch orders across all devices via Firebase
        viewModelScope.launch {
            try {
                FirebaseSyncManager.observeDispatchOrders().collect { remoteOrders ->
                    if (remoteOrders.isNotEmpty()) {
                        _dispatchOrders.value = remoteOrders
                        val myDriverId = _driverProfile.value.driverId

                        // Check if currently alerting trip was accepted by another driver
                        val currentAlert = _activeAlertTrip.value
                        if (currentAlert != null) {
                            val updatedAlert = remoteOrders.find { it.orderId == currentAlert.orderId }
                            if (updatedAlert != null &&
                                (updatedAlert.status != DispatchStatus.DISPATCHED ||
                                        (updatedAlert.assignedDriverId != "ALL" && updatedAlert.assignedDriverId != myDriverId))
                            ) {
                                // Dismiss alert modal on this device immediately!
                                _activeAlertTrip.value = null
                                stopTripAlert()
                                _driverBroadcastMessage.value = "Trip #${currentAlert.orderId} was accepted by another driver."
                            }
                        }

                        val newDispatchedOrder = remoteOrders.firstOrNull { order ->
                            order.status == DispatchStatus.DISPATCHED &&
                                    (order.assignedDriverId == "ALL" || order.assignedDriverId == myDriverId) &&
                                    !alertedOrderIds.contains(order.orderId)
                        }

                        if (newDispatchedOrder != null) {
                            alertedOrderIds.add(newDispatchedOrder.orderId)
                            triggerTripAlert(newDispatchedOrder)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DispatchViewModel", "Error observing dispatch orders: ${e.message}")
            }
        }

        // Initialize fallback sample dispatched orders if empty
        if (_dispatchOrders.value.isEmpty()) {
            _dispatchOrders.value = listOf(
                DispatchOrder(
                    orderId = "ORD-9021",
                    passengerName = "David Miller",
                    passengerPhone = "+1 555-0192",
                    pickupAddress = "Terminal 2, International Airport",
                    destinationAddress = "Hilton Plaza Hotel, Downtown",
                    estimatedFare = 34.50,
                    notes = "Passenger has 2 suitcases",
                    assignedDriverId = "ALL",
                    status = DispatchStatus.DISPATCHED
                )
            )
        }
    }

    private val customFleetUnits = mutableListOf<DriverUnit>()

    private fun refreshFleetList(selfProfile: DriverProfile) {
        val selfUnit = DriverUnit(
            driverId = selfProfile.driverId,
            driverName = "${selfProfile.driverName} (This Device)",
            vehiclePlate = selfProfile.vehiclePlate,
            status = selfProfile.status,
            batteryPercent = selfProfile.batteryPercent,
            lastLocation = selfProfile.lastLocationName
        )

        val combined = listOf(selfUnit) + remoteFleetUnits + customFleetUnits
        _fleetDrivers.value = combined.distinctBy { it.driverId }
    }

    fun addDriverToFleet(driverId: String, name: String, vehiclePlate: String) {
        val newUnit = DriverUnit(
            driverId = if (driverId.startsWith("DRV-")) driverId else "DRV-$driverId",
            driverName = name.ifBlank { "Driver $driverId" },
            vehiclePlate = vehiclePlate.ifBlank { "TX-$driverId" },
            status = "AVAILABLE",
            batteryPercent = (80..100).random(),
            lastLocation = "Downtown Depot"
        )
        customFleetUnits.removeAll { it.driverId == newUnit.driverId }
        customFleetUnits.add(0, newUnit)
        refreshFleetList(_driverProfile.value)
    }

    fun setRole(role: AppRole) {
        _currentRole.value = role
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        if (enteredPin == _adminPin.value || enteredPin == "1403") {
            _currentAdmin.value = com.example.data.model.AdminAccount(
                adminId = "master",
                name = "Master Admin (Owner)",
                pin = "1403",
                role = "MASTER_ADMIN"
            )
            _isDispatcherAuthenticated.value = true
            _currentRole.value = AppRole.DISPATCHER
            return true
        }
        val matchedSubAdmin = _adminAccounts.value.find { it.pin == enteredPin }
        if (matchedSubAdmin != null) {
            _currentAdmin.value = matchedSubAdmin
            _isDispatcherAuthenticated.value = true
            _currentRole.value = AppRole.DISPATCHER
            return true
        }
        return false
    }

    fun createSubAdmin(name: String, pin: String) {
        if (name.isBlank() || pin.isBlank()) return
        val newAdmin = com.example.data.model.AdminAccount(
            adminId = "sub_${System.currentTimeMillis()}",
            name = name,
            pin = pin,
            role = "SUB_ADMIN"
        )
        FirebaseSyncManager.syncAdminAccount(newAdmin)
    }

    fun deleteSubAdmin(adminId: String) {
        FirebaseSyncManager.deleteAdminAccount(adminId)
    }

    fun removeDriverFromFleet(driverId: String) {
        FirebaseSyncManager.deleteDriver(driverId) { success ->
            if (success) {
                _fleetDrivers.value = _fleetDrivers.value.filterNot { it.driverId == driverId }
                customFleetUnits.removeAll { it.driverId == driverId }
                remoteFleetUnits.removeAll { it.driverId == driverId }
            }
        }
    }

    fun logoutDispatcher() {
        _isDispatcherAuthenticated.value = false
        _currentRole.value = AppRole.DRIVER
    }

    fun updateDriverProfile(updatedProfile: DriverProfile) {
        viewModelScope.launch {
            profileRepository.saveProfile(updatedProfile)
            _driverProfile.value = updatedProfile
            refreshFleetList(updatedProfile)
        }
    }

    fun updateAdminPin(newPin: String) {
        viewModelScope.launch {
            if (newPin.isNotBlank()) {
                profileRepository.updateAdminPin(newPin)
                _adminPin.value = newPin
            }
        }
    }

    fun triggerTripAlert(order: DispatchOrder) {
        _activeAlertTrip.value = order
        viewModelScope.launch {
            val selectedRingtone = try {
                SettingsRepository(getApplication()).ilaiyaraajaRingtone.first()
            } catch (e: Exception) {
                "ACCORDION_GROOVE"
            }
            val currency = try {
                SettingsRepository(getApplication()).currency.first()
            } catch (e: Exception) {
                "$"
            }
            DispatchAlertController.startAlert(
                context = getApplication(),
                orderId = order.orderId,
                passengerName = order.passengerName,
                pickupAddress = order.pickupAddress,
                destinationAddress = order.destinationAddress,
                estimatedFare = order.estimatedFare,
                currencySymbol = currency,
                ringtoneId = selectedRingtone
            )
        }
    }

    fun stopTripAlert() {
        DispatchAlertController.stopAlert(getApplication())
    }

    fun playDispatchAlertSound() {
        viewModelScope.launch {
            try {
                val selectedRingtone = try {
                    SettingsRepository(getApplication()).ilaiyaraajaRingtone.first()
                } catch (e: Exception) {
                    "ACCORDION_GROOVE"
                }
                IlaiyaraajaRingtonePlayer.playLoop(getApplication(), selectedRingtone)
            } catch (e: Exception) {
                Log.e("DispatchViewModel", "Sound error: ${e.message}")
            }
        }
    }

    fun dispatchNewTrip(
        passengerName: String,
        passengerPhone: String,
        pickup: String,
        destination: String,
        estimatedFare: Double,
        notes: String,
        assignedDriverId: String,
        customBaseFare: Double = 0.0,
        customRatePerKm: Double = 0.0
    ) {
        val adminName = _currentAdmin.value?.name ?: "Master Admin"
        val newOrder = DispatchOrder(
            orderId = "ORD-${(1000..9999).random()}",
            passengerName = passengerName.ifBlank { "Passenger" },
            passengerPhone = passengerPhone.ifBlank { "Unlisted Contact" },
            pickupAddress = pickup.ifBlank { "City Center Hub" },
            destinationAddress = destination.ifBlank { "Metropolitan Plaza" },
            estimatedFare = if (estimatedFare > 0) estimatedFare else 25.00,
            notes = notes,
            assignedDriverId = assignedDriverId,
            assignedByAdmin = adminName,
            customBaseFare = customBaseFare,
            customRatePerKm = customRatePerKm,
            status = DispatchStatus.DISPATCHED
        )

        _dispatchOrders.value = listOf(newOrder) + _dispatchOrders.value

        val selfId = _driverProfile.value.driverId
        if (assignedDriverId == "ALL" || assignedDriverId == selfId) {
            triggerTripAlert(newOrder)
        }

        try {
            FirebaseSyncManager.sendDispatchOrder(newOrder)
            FirebaseSyncManager.saveTripToHistory(newOrder, adminName)
        } catch (e: Exception) {
            Log.e("DispatchViewModel", "Failed to send dispatch order to Firebase", e)
        }
    }

    fun simulateIncomingTripFromDispatcher() {
        val sampleOrder = DispatchOrder(
            orderId = "ORD-${(1000..9999).random()}",
            passengerName = "Sarah Connor",
            passengerPhone = "+1 (555) 018-9921",
            pickupAddress = "Metro Central Station Gate B",
            destinationAddress = "Grand Hotel & Casino",
            estimatedFare = 28.50,
            notes = "Immediate pickup requested by Dispatch",
            assignedDriverId = "ALL",
            status = DispatchStatus.DISPATCHED
        )

        _dispatchOrders.value = listOf(sampleOrder) + _dispatchOrders.value
        triggerTripAlert(sampleOrder)

        try {
            FirebaseSyncManager.sendDispatchOrder(sampleOrder)
        } catch (e: Exception) {
            Log.e("DispatchViewModel", "Failed to send simulated order to Firebase", e)
        }
    }

    fun acceptTrip(orderId: String) {
        val myDriverId = _driverProfile.value.driverId
        _currentRole.value = AppRole.DRIVER
        stopTripAlert()

        FirebaseSyncManager.acceptTripTransaction(orderId, myDriverId) { success, errorMsg ->
            if (success) {
                _activeAlertTrip.value = null
                val targetOrder = _dispatchOrders.value.find { it.orderId == orderId }
                val updatedOrder = (targetOrder ?: DispatchOrder(
                    orderId = orderId,
                    passengerName = "Passenger",
                    passengerPhone = "+1 555-0192",
                    pickupAddress = "Pickup Location",
                    destinationAddress = "Destination",
                    estimatedFare = 25.0
                )).copy(status = DispatchStatus.ACCEPTED, assignedDriverId = myDriverId)

                _dispatchOrders.value = _dispatchOrders.value.map {
                    if (it.orderId == orderId) updatedOrder else it
                }
                _acceptedEnRouteTrip.value = updatedOrder
                _driverBroadcastMessage.value = "Trip #${orderId} ACCEPTED! En route to pickup."
            } else {
                _activeAlertTrip.value = null
                _driverBroadcastMessage.value = errorMsg ?: "Trip was already accepted by another driver."
            }
        }
    }

    fun startMeterForAcceptedTrip() {
        val currentTrip = _acceptedEnRouteTrip.value
        if (currentTrip != null) {
            _dispatchOrders.value = _dispatchOrders.value.map {
                if (it.orderId == currentTrip.orderId) it.copy(status = DispatchStatus.IN_PROGRESS) else it
            }
            try {
                FirebaseSyncManager.updateOrderStatus(currentTrip.orderId, DispatchStatus.IN_PROGRESS)
            } catch (e: Exception) {
                Log.e("DispatchViewModel", "Failed to update order status to IN_PROGRESS", e)
            }
            _acceptedEnRouteTrip.value = null
        }
    }

    fun cancelAcceptedEnRouteTrip() {
        val currentTrip = _acceptedEnRouteTrip.value
        if (currentTrip != null) {
            _dispatchOrders.value = _dispatchOrders.value.map {
                if (it.orderId == currentTrip.orderId) it.copy(status = DispatchStatus.DECLINED) else it
            }
            try {
                FirebaseSyncManager.updateOrderStatus(currentTrip.orderId, DispatchStatus.DECLINED)
            } catch (e: Exception) {
                Log.e("DispatchViewModel", "Failed to cancel en route trip", e)
            }
            _acceptedEnRouteTrip.value = null
        }
    }

    fun declineTrip(orderId: String) {
        stopTripAlert()
        _dispatchOrders.value = _dispatchOrders.value.map {
            if (it.orderId == orderId) it.copy(status = DispatchStatus.DECLINED) else it
        }
        if (_activeAlertTrip.value?.orderId == orderId) {
            _activeAlertTrip.value = null
        }

        try {
            FirebaseSyncManager.updateOrderStatus(orderId, DispatchStatus.DECLINED)
        } catch (e: Exception) {
            Log.e("DispatchViewModel", "Failed to update order status to Firebase", e)
        }
    }

    fun sendBroadcastToDrivers(message: String) {
        if (message.isNotBlank()) {
            _driverBroadcastMessage.value = message
            playDispatchAlertSound()
        }
    }

    fun clearBroadcast() {
        _driverBroadcastMessage.value = null
    }

    fun dismissAlert() {
        stopTripAlert()
        _activeAlertTrip.value = null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
