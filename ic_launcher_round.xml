package com.example.data.remote

import android.util.Log
import com.example.data.model.DispatchOrder
import com.example.data.model.DispatchStatus
import com.example.data.model.DriverProfile
import com.example.data.model.DriverUnit
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import java.util.Locale

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    const val FIREBASE_URL = "https://drivetechsoft-default-rtdb.asia-southeast1.firebasedatabase.app"

    private val database: FirebaseDatabase by lazy {
        try {
            FirebaseDatabase.getInstance(FIREBASE_URL)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FirebaseDatabase with URL, falling back to default instance", e)
            FirebaseDatabase.getInstance()
        }
    }

    private val driversRef by lazy { database.getReference("drivers") }
    private val ordersRef by lazy { database.getReference("dispatch_orders") }
    private val adminsRef by lazy { database.getReference("admins") }
    private val tripsRef by lazy { database.getReference("trips") }
    private val historyRef by lazy { database.getReference("dispatch_history") }

    /**
     * Sync or create an Admin/Sub-Admin account in Firebase under /admins.
     */
    fun syncAdminAccount(admin: com.example.data.model.AdminAccount) {
        if (admin.adminId.isBlank()) return
        val adminMap = mapOf(
            "adminId" to admin.adminId,
            "name" to admin.name,
            "pin" to admin.pin,
            "role" to admin.role
        )
        adminsRef.child(admin.adminId).setValue(adminMap)
    }

    /**
     * Delete sub-admin account from Firebase.
     */
    fun deleteAdminAccount(adminId: String) {
        if (adminId.isBlank()) return
        adminsRef.child(adminId).removeValue()
    }

    /**
     * Listen to all registered admin/sub-admin accounts from Firebase under /admins.
     */
    fun observeAdmins(): Flow<List<com.example.data.model.AdminAccount>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val adminsList = mutableListOf<com.example.data.model.AdminAccount>()
                for (child in snapshot.children) {
                    try {
                        val adminId = child.child("adminId").getValue(String::class.java) ?: child.key ?: continue
                        val name = child.child("name").getValue(String::class.java) ?: "Sub-Admin"
                        val pin = child.child("pin").getValue(String::class.java) ?: ""
                        val role = child.child("role").getValue(String::class.java) ?: "SUB_ADMIN"
                        adminsList.add(com.example.data.model.AdminAccount(adminId, name, pin, role))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing admin child: ${e.message}")
                    }
                }
                trySend(adminsList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error observeAdmins: ${error.message}")
            }
        }
        adminsRef.addValueEventListener(listener)
        awaitClose { adminsRef.removeEventListener(listener) }
    }

    /**
     * Save trip details to /trips/{tripId} and /dispatch_history/{tripId} tagged with assignedByAdmin.
     */
    fun saveTripToHistory(order: DispatchOrder, assignedByAdmin: String) {
        val tripMap = mapOf(
            "orderId" to order.orderId,
            "passengerName" to order.passengerName,
            "passengerPhone" to order.passengerPhone,
            "pickupAddress" to order.pickupAddress,
            "destinationAddress" to order.destinationAddress,
            "estimatedFare" to order.estimatedFare,
            "notes" to order.notes,
            "assignedDriverId" to order.assignedDriverId,
            "assignedByAdmin" to assignedByAdmin,
            "customBaseFare" to order.customBaseFare,
            "customRatePerKm" to order.customRatePerKm,
            "status" to order.status.name,
            "timestamp" to order.timestamp
        )

        tripsRef.child(order.orderId).setValue(tripMap)
        historyRef.child(order.orderId).setValue(tripMap)
    }

    /**
     * Observe full dispatch trip history from /dispatch_history.
     */
    fun observeDispatchHistory(): Flow<List<DispatchOrder>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val historyList = mutableListOf<DispatchOrder>()
                for (child in snapshot.children) {
                    try {
                        val orderId = child.child("orderId").getValue(String::class.java) ?: child.key ?: continue
                        val passengerName = child.child("passengerName").getValue(String::class.java) ?: ""
                        val passengerPhone = child.child("passengerPhone").getValue(String::class.java) ?: ""
                        val pickupAddress = child.child("pickupAddress").getValue(String::class.java) ?: ""
                        val destinationAddress = child.child("destinationAddress").getValue(String::class.java) ?: ""
                        val estimatedFare = child.child("estimatedFare").getValue(Double::class.java) ?: 0.0
                        val notes = child.child("notes").getValue(String::class.java) ?: ""
                        val assignedDriverId = child.child("assignedDriverId").getValue(String::class.java) ?: "ALL"
                        val assignedByAdmin = child.child("assignedByAdmin").getValue(String::class.java) ?: "Master Admin"
                        val customBaseFare = child.child("customBaseFare").getValue(Double::class.java) ?: 0.0
                        val customRatePerKm = child.child("customRatePerKm").getValue(Double::class.java) ?: 0.0
                        val statusStr = child.child("status").getValue(String::class.java) ?: DispatchStatus.DISPATCHED.name
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                        val status = try {
                            DispatchStatus.valueOf(statusStr)
                        } catch (e: Exception) {
                            DispatchStatus.DISPATCHED
                        }

                        historyList.add(
                            DispatchOrder(
                                orderId = orderId,
                                passengerName = passengerName,
                                passengerPhone = passengerPhone,
                                pickupAddress = pickupAddress,
                                destinationAddress = destinationAddress,
                                estimatedFare = estimatedFare,
                                notes = notes,
                                assignedDriverId = assignedDriverId,
                                assignedByAdmin = assignedByAdmin,
                                customBaseFare = customBaseFare,
                                customRatePerKm = customRatePerKm,
                                status = status,
                                timestamp = timestamp
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing history child: ${e.message}")
                    }
                }
                trySend(historyList.sortedByDescending { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error observeDispatchHistory: ${error.message}")
            }
        }
        historyRef.addValueEventListener(listener)
        awaitClose { historyRef.removeEventListener(listener) }
    }

    /**
     * Publish or update driver profile to Firebase Realtime Database including GPS coordinates under /drivers/{driverId}/location and photoUrl.
     */
    fun syncDriverProfile(profile: DriverProfile) {
        if (profile.driverId.isBlank()) return
        val lat = profile.latitude ?: 0.0
        val lng = profile.longitude ?: 0.0
        val driverMap = mapOf(
            "driverId" to profile.driverId,
            "driverName" to profile.driverName,
            "phoneNumber" to profile.phoneNumber,
            "vehiclePlate" to profile.vehiclePlate,
            "vehicleModel" to profile.vehicleModel,
            "status" to profile.status,
            "isOnline" to profile.isOnline,
            "latitude" to lat,
            "longitude" to lng,
            "lastLocationName" to profile.lastLocationName,
            "batteryPercent" to profile.batteryPercent,
            "fleetNetworkCode" to profile.fleetNetworkCode,
            "photoUrl" to profile.photoUri,
            "photoUri" to profile.photoUri,
            "profilePicUrl" to profile.photoUri,
            "lastUpdatedTimestamp" to System.currentTimeMillis()
        )
        
        val locationMap = mapOf(
            "latitude" to lat,
            "longitude" to lng,
            "lastLocationName" to profile.lastLocationName,
            "timestamp" to System.currentTimeMillis()
        )

        driversRef.child(profile.driverId).setValue(driverMap)
            .addOnSuccessListener {
                Log.d(TAG, "Driver profile successfully synced to Firebase: ${profile.driverId}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync driver profile to Firebase: ${e.message}", e)
            }

        driversRef.child(profile.driverId).child("location").setValue(locationMap)
    }

    /**
     * Delete driver record from Firebase database (/drivers/{driverId}).
     */
    fun deleteDriver(driverId: String, onComplete: (Boolean) -> Unit) {
        if (driverId.isBlank()) {
            onComplete(false)
            return
        }
        driversRef.child(driverId).removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "Driver $driverId removed from Firebase successfully.")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete driver $driverId from Firebase: ${e.message}", e)
                onComplete(false)
            }
    }

    /**
     * Update driver location specifically on GPS position fix.
     */
    fun updateDriverLocation(driverId: String, lat: Double, lng: Double, locationName: String) {
        if (driverId.isBlank()) return
        val updates = mapOf(
            "latitude" to lat,
            "longitude" to lng,
            "lastLocationName" to locationName,
            "lastUpdatedTimestamp" to System.currentTimeMillis()
        )
        driversRef.child(driverId).updateChildren(updates)

        val locationNode = mapOf(
            "latitude" to lat,
            "longitude" to lng,
            "lastLocationName" to locationName,
            "timestamp" to System.currentTimeMillis()
        )
        driversRef.child(driverId).child("location").setValue(locationNode)
    }

    /**
     * Listen to all active fleet drivers in real time from Firebase.
     */
    fun observeFleetDrivers(): Flow<List<DriverUnit>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val driversList = mutableListOf<DriverUnit>()
                for (child in snapshot.children) {
                    try {
                        val driverId = child.child("driverId").getValue(String::class.java) ?: child.key ?: continue
                        val name = child.child("driverName").getValue(String::class.java) ?: "Driver $driverId"
                        val plate = child.child("vehiclePlate").getValue(String::class.java) ?: "N/A"
                        val status = child.child("status").getValue(String::class.java) ?: "AVAILABLE"
                        val battery = child.child("batteryPercent").getValue(Long::class.java)?.toInt()
                            ?: child.child("batteryPercent").getValue(Int::class.java) ?: 95

                        val photoUrl = child.child("profilePicUrl").getValue(String::class.java)
                            ?: child.child("photoUrl").getValue(String::class.java)
                            ?: child.child("photoUri").getValue(String::class.java) ?: ""
                        val phone = child.child("phoneNumber").getValue(String::class.java) ?: ""

                        val lat = child.child("latitude").getValue(Double::class.java)
                            ?: child.child("location").child("latitude").getValue(Double::class.java)
                        val lng = child.child("longitude").getValue(Double::class.java)
                            ?: child.child("location").child("longitude").getValue(Double::class.java)

                        var rawLocName = child.child("lastLocationName").getValue(String::class.java)
                            ?: child.child("location").child("lastLocationName").getValue(String::class.java) ?: ""

                        if (rawLocName.isBlank() || rawLocName.contains("Depot", ignoreCase = true) || rawLocName == "GPS Active") {
                            rawLocName = if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
                                String.format(Locale.US, "GPS: %.4f, %.4f", lat, lng)
                            } else {
                                "Locating..."
                            }
                        }

                        driversList.add(
                            DriverUnit(
                                driverId = driverId,
                                driverName = name,
                                vehiclePlate = plate,
                                status = status,
                                batteryPercent = battery,
                                lastLocation = rawLocName,
                                latitude = lat,
                                longitude = lng,
                                photoUrl = photoUrl,
                                phoneNumber = phone
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing driver child: ${e.message}")
                    }
                }
                trySend(driversList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error observeFleetDrivers: ${error.message}")
            }
        }

        driversRef.addValueEventListener(listener)
        awaitClose { driversRef.removeEventListener(listener) }
    }

    /**
     * Sync dispatch orders live across all devices.
     */
    fun sendDispatchOrder(order: DispatchOrder) {
        val orderMap = mapOf(
            "orderId" to order.orderId,
            "passengerName" to order.passengerName,
            "passengerPhone" to order.passengerPhone,
            "pickupAddress" to order.pickupAddress,
            "destinationAddress" to order.destinationAddress,
            "estimatedFare" to order.estimatedFare,
            "notes" to order.notes,
            "assignedDriverId" to order.assignedDriverId,
            "assignedByAdmin" to order.assignedByAdmin,
            "customBaseFare" to order.customBaseFare,
            "customRatePerKm" to order.customRatePerKm,
            "status" to order.status.name,
            "timestamp" to order.timestamp
        )
        ordersRef.child(order.orderId).setValue(orderMap)
    }

    fun updateOrderStatus(orderId: String, status: DispatchStatus, assignedDriverId: String? = null) {
        val updates = mutableMapOf<String, Any>(
            "status" to status.name
        )
        if (assignedDriverId != null) {
            updates["assignedDriverId"] = assignedDriverId
        }
        ordersRef.child(orderId).updateChildren(updates)
    }

    /**
     * Single Acceptance Transaction to prevent race conditions when multiple drivers attempt to accept the same trip.
     */
    fun acceptTripTransaction(orderId: String, driverId: String, onResult: (Boolean, String?) -> Unit) {
        val orderNode = ordersRef.child(orderId)
        orderNode.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentStatusStr = mutableData.child("status").getValue(String::class.java)
                val currentAssigned = mutableData.child("assignedDriverId").getValue(String::class.java) ?: "ALL"

                // If already accepted or in progress or completed, cancel this driver's transaction attempt
                if (currentStatusStr == DispatchStatus.ACCEPTED.name ||
                    currentStatusStr == DispatchStatus.IN_PROGRESS.name ||
                    currentStatusStr == DispatchStatus.COMPLETED.name
                ) {
                    return Transaction.abort()
                }

                // If assigned to a specific driver that is NOT this driver, abort
                if (currentAssigned != "ALL" && currentAssigned != driverId) {
                    return Transaction.abort()
                }

                // Lock trip status to ACCEPTED and assign to this driver
                mutableData.child("status").value = DispatchStatus.ACCEPTED.name
                mutableData.child("assignedDriverId").value = driverId
                mutableData.child("acceptedByDriverId").value = driverId
                mutableData.child("acceptedTimestamp").value = System.currentTimeMillis()

                return Transaction.success(mutableData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (committed) {
                    Log.d(TAG, "Transaction successful! Order $orderId accepted by $driverId")
                    onResult(true, null)
                } else {
                    val acceptedBy = currentData?.child("acceptedByDriverId")?.getValue(String::class.java)
                        ?: currentData?.child("assignedDriverId")?.getValue(String::class.java)
                        ?: "another driver"
                    val msg = "Trip $orderId was already accepted by $acceptedBy"
                    Log.w(TAG, "Transaction failed: $msg")
                    onResult(false, msg)
                }
            }
        })
    }

    fun observeDispatchOrders(): Flow<List<DispatchOrder>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ordersList = mutableListOf<DispatchOrder>()
                for (child in snapshot.children) {
                    try {
                        val orderId = child.child("orderId").getValue(String::class.java) ?: child.key ?: continue
                        val passengerName = child.child("passengerName").getValue(String::class.java) ?: ""
                        val passengerPhone = child.child("passengerPhone").getValue(String::class.java) ?: ""
                        val pickupAddress = child.child("pickupAddress").getValue(String::class.java) ?: ""
                        val destinationAddress = child.child("destinationAddress").getValue(String::class.java) ?: ""
                        val estimatedFare = child.child("estimatedFare").getValue(Double::class.java) ?: 0.0
                        val notes = child.child("notes").getValue(String::class.java) ?: ""
                        val assignedDriverId = child.child("assignedDriverId").getValue(String::class.java) ?: "ALL"
                        val assignedByAdmin = child.child("assignedByAdmin").getValue(String::class.java) ?: "Master Admin"
                        val customBaseFare = child.child("customBaseFare").getValue(Double::class.java) ?: 0.0
                        val customRatePerKm = child.child("customRatePerKm").getValue(Double::class.java) ?: 0.0
                        val statusStr = child.child("status").getValue(String::class.java) ?: DispatchStatus.DISPATCHED.name
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                        val status = try {
                            DispatchStatus.valueOf(statusStr)
                        } catch (e: Exception) {
                            DispatchStatus.DISPATCHED
                        }

                        ordersList.add(
                            DispatchOrder(
                                orderId = orderId,
                                passengerName = passengerName,
                                passengerPhone = passengerPhone,
                                pickupAddress = pickupAddress,
                                destinationAddress = destinationAddress,
                                estimatedFare = estimatedFare,
                                notes = notes,
                                assignedDriverId = assignedDriverId,
                                assignedByAdmin = assignedByAdmin,
                                customBaseFare = customBaseFare,
                                customRatePerKm = customRatePerKm,
                                status = status,
                                timestamp = timestamp
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing order child: ${e.message}")
                    }
                }
                trySend(ordersList.sortedByDescending { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error observeDispatchOrders: ${error.message}")
            }
        }

        ordersRef.addValueEventListener(listener)
        awaitClose { ordersRef.removeEventListener(listener) }
    }

    /**
     * Find an existing driver ID by checking:
     * 1. /drivers/{uid}/driverId
     * 2. /drivers/{phoneNumber}/driverId
     * 3. Scanning children under /drivers for matching phoneNumber
     */
    suspend fun findExistingDriverIdByPhoneOrUid(phone: String, uid: String? = null): String? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        if (phone.isBlank() && uid.isNullOrBlank()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val database = FirebaseDatabase.getInstance(FIREBASE_URL)

        // Local helper to normalize phone numbers
        fun normalize(p: String): String {
            return p.replace(Regex("[^0-9]"), "")
        }

        // Local helper to search all drivers
        fun queryAllDriversByPhone() {
            if (phone.isBlank()) {
                continuation.resume(null)
                return
            }
            val driversRef = database.getReference("drivers")
            driversRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var foundId: String? = null
                    for (child in snapshot.children) {
                        val driverPhone = child.child("phoneNumber").getValue(String::class.java)
                        if (driverPhone != null && normalize(driverPhone) == normalize(phone)) {
                            val driverId = child.child("driverId").getValue(String::class.java)
                            if (!driverId.isNullOrBlank()) {
                                foundId = driverId
                                break
                            }
                        }
                    }
                    continuation.resume(foundId)
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resume(null)
                }
            })
        }

        // Local helper to search by phoneNumber child key
        fun checkByPhoneNumber() {
            if (phone.isBlank()) {
                queryAllDriversByPhone()
                return
            }
            val cleanPhoneKey = phone.replace(Regex("[^a-zA-Z0-9]"), "")
            if (cleanPhoneKey.isNotBlank()) {
                database.getReference("drivers").child(cleanPhoneKey).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val existingId = snapshot.child("driverId").getValue(String::class.java)
                        if (!existingId.isNullOrBlank()) {
                            continuation.resume(existingId)
                        } else {
                            queryAllDriversByPhone()
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        queryAllDriversByPhone()
                    }
                })
            } else {
                queryAllDriversByPhone()
            }
        }

        // Step 1: Check by UID child key if provided
        if (!uid.isNullOrBlank()) {
            val uidRef = database.getReference("drivers").child(uid)
            uidRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val existingId = snapshot.child("driverId").getValue(String::class.java)
                    if (!existingId.isNullOrBlank()) {
                        continuation.resume(existingId)
                    } else {
                        checkByPhoneNumber()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    checkByPhoneNumber()
                }
            })
        } else {
            checkByPhoneNumber()
        }
    }

    /**
     * Sequential driver ID auto-increment generator in Firebase under /metadata/lastDriverId.
     * Starting index: 11 (reserving IDs 1 through 10 exclusively for Admin/Internal testing).
     * Format: DRV-%04d (e.g., DRV-0011, DRV-0012, DRV-0013, ...)
     */
    suspend fun getOrGenerateSequentialDriverId(): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val lastIdRef = database.getReference("metadata").child("lastDriverId")
        lastIdRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentVal = mutableData.getValue(Long::class.java)
                val nextVal = if (currentVal == null) 1L else currentVal + 1L
                mutableData.value = nextVal
                return Transaction.success(mutableData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (committed && currentData != null && error == null) {
                    val assignedNum = currentData.getValue(Long::class.java) ?: 11L
                    val formattedId = String.format(Locale.US, "DRV-%04d", assignedNum)
                    if (continuation.isActive) {
                        continuation.resume(formattedId)
                    }
                } else {
                    Log.e(TAG, "Driver ID transaction failed: ${error?.message}")
                    val fallbackId = String.format(Locale.US, "DRV-%04d", (11..99).random())
                    if (continuation.isActive) {
                        continuation.resume(fallbackId)
                    }
                }
            }
        })
    }
    fun resetDriverIdCounter(onComplete: (Boolean) -> Unit) {
        val lastIdRef = database.getReference("metadata").child("lastDriverId")
        lastIdRef.setValue(0L).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Clear orphan mapping entries
                database.getReference("drivers").removeValue().addOnCompleteListener { mappingTask ->
                    onComplete(mappingTask.isSuccessful)
                }
            } else {
                onComplete(false)
            }
        }
    }
    fun purgeAllDrivers(onComplete: (Boolean) -> Unit) {
        val lastIdRef = database.getReference("metadata").child("lastDriverId")
        lastIdRef.setValue(0L).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                database.getReference("drivers").removeValue().addOnCompleteListener { mappingTask ->
                    onComplete(mappingTask.isSuccessful)
                }
            } else {
                onComplete(false)
            }
        }
    }
    fun observeDriverExists(driverId: String): Flow<Boolean> = callbackFlow {
        if (driverId.isBlank()) {
            trySend(false)
            return@callbackFlow
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.exists())
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(true) // assume exists on error to prevent unwanted purge
            }
        }
        val ref = database.getReference("drivers").child(driverId)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
