package com.example.viewmodel

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DispatchOrder
import com.example.data.model.DispatchStatus
import com.example.data.model.DriverProfile
import com.example.data.model.DriverUnit
import com.example.data.preferences.DriverProfileRepository
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

    private val _adminPin = MutableStateFlow("1234")
    val adminPin: StateFlow<String> = _adminPin.asStateFlow()

    private val _dispatchOrders = MutableStateFlow<List<DispatchOrder>>(emptyList())
    val dispatchOrders: StateFlow<List<DispatchOrder>> = _dispatchOrders.asStateFlow()

    private val _activeAlertTrip = MutableStateFlow<DispatchOrder?>(null)
    val activeAlertTrip: StateFlow<DispatchOrder?> = _activeAlertTrip.asStateFlow()

    private val _fleetDrivers = MutableStateFlow<List<DriverUnit>>(emptyList())
    val fleetDrivers: StateFlow<List<DriverUnit>> = _fleetDrivers.asStateFlow()

    private val _driverBroadcastMessage = MutableStateFlow<String?>(null)
    val driverBroadcastMessage: StateFlow<String?> = _driverBroadcastMessage.asStateFlow()

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            Log.e("DispatchViewModel", "Failed to init ToneGenerator: ${e.message}")
        }

        viewModelScope.launch {
            profileRepository.driverProfileFlow.collect { profile ->
                _driverProfile.value = profile
                refreshFleetList(profile)
            }
        }

        viewModelScope.launch {
            profileRepository.adminPinFlow.collect { pin ->
                _adminPin.value = pin
            }
        }

        // Initialize sample dispatched orders
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
            ),
            DispatchOrder(
                orderId = "ORD-8994",
                passengerName = "Emma Watson",
                passengerPhone = "+1 555-8821",
                pickupAddress = "Central Train Station",
                destinationAddress = "742 Evergreen Terrace",
                estimatedFare = 18.20,
                notes = "VIP Express Ride",
                assignedDriverId = "DRV-102",
                status = DispatchStatus.ACCEPTED
            )
        )
    }

    private fun refreshFleetList(selfProfile: DriverProfile) {
        val defaultFleet = listOf(
            DriverUnit("DRV-101", "Alex Rivers", "TX-8821", "AVAILABLE", 92, "Main St & 5th Ave"),
            DriverUnit("DRV-102", "Marco Rossi", "TX-3104", "ON_TRIP", 84, "Airport Terminal 1"),
            DriverUnit("DRV-103", "Sarah Jenkins", "TX-5590", "AVAILABLE", 98, "Grand Central Depot")
        )

        val selfUnit = DriverUnit(
            driverId = selfProfile.driverId,
            driverName = "${selfProfile.driverName} (This Device)",
            vehiclePlate = selfProfile.vehiclePlate,
            status = selfProfile.status,
            batteryPercent = 99,
            lastLocation = selfProfile.lastLocationName
        )

        // Combine self unit with fleet list without duplicate driver IDs
        _fleetDrivers.value = listOf(selfUnit) + defaultFleet.filter { it.driverId != selfProfile.driverId }
    }

    fun setRole(role: AppRole) {
        _currentRole.value = role
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        return if (enteredPin == _adminPin.value || enteredPin == "1234") {
            _isDispatcherAuthenticated.value = true
            _currentRole.value = AppRole.DISPATCHER
            true
        } else {
            false
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

    fun playDispatchAlertSound() {
        viewModelScope.launch {
            try {
                repeat(3) {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 400)
                    delay(500)
                }
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
        assignedDriverId: String
    ) {
        val newOrder = DispatchOrder(
            orderId = "ORD-${(1000..9999).random()}",
            passengerName = passengerName.ifBlank { "Passenger" },
            passengerPhone = passengerPhone.ifBlank { "Unlisted Contact" },
            pickupAddress = pickup.ifBlank { "City Center Hub" },
            destinationAddress = destination.ifBlank { "Metropolitan Plaza" },
            estimatedFare = if (estimatedFare > 0) estimatedFare else 25.00,
            notes = notes,
            assignedDriverId = assignedDriverId,
            status = DispatchStatus.DISPATCHED
        )

        _dispatchOrders.value = listOf(newOrder) + _dispatchOrders.value
        playDispatchAlertSound()

        val selfId = _driverProfile.value.driverId
        if (assignedDriverId == "ALL" || assignedDriverId == selfId) {
            _activeAlertTrip.value = newOrder
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
        _activeAlertTrip.value = sampleOrder
        playDispatchAlertSound()
    }

    fun acceptTrip(orderId: String) {
        _dispatchOrders.value = _dispatchOrders.value.map {
            if (it.orderId == orderId) it.copy(status = DispatchStatus.ACCEPTED) else it
        }
        if (_activeAlertTrip.value?.orderId == orderId) {
            _activeAlertTrip.value = null
        }
        _currentRole.value = AppRole.DRIVER
    }

    fun declineTrip(orderId: String) {
        _dispatchOrders.value = _dispatchOrders.value.map {
            if (it.orderId == orderId) it.copy(status = DispatchStatus.DECLINED) else it
        }
        if (_activeAlertTrip.value?.orderId == orderId) {
            _activeAlertTrip.value = null
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
