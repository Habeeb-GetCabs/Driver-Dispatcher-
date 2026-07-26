package com.example.data.remote

import android.util.Log
import com.example.data.model.DispatchOrder
import com.example.data.model.DispatchStatus
import com.example.data.model.DriverProfile
import com.example.data.model.DriverUnit
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    private const val FIREBASE_URL = "https://drivetechsoft-default-rtdb.asia-southeast1.firebasedatabase.app"

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

    /**
     * Publish or update driver profile to Firebase Realtime Database so other devices can see it in Dispatcher fleet.
     */
    fun syncDriverProfile(profile: DriverProfile) {
        if (profile.driverId.isBlank()) return
        val driverMap = mapOf(
            "driverId" to profile.driverId,
            "driverName" to profile.driverName,
            "phoneNumber" to profile.phoneNumber,
            "vehiclePlate" to profile.vehiclePlate,
            "vehicleModel" to profile.vehicleModel,
            "status" to profile.status,
            "isOnline" to profile.isOnline,
            "lastLocationName" to profile.lastLocationName,
            "batteryPercent" to profile.batteryPercent,
            "fleetNetworkCode" to profile.fleetNetworkCode,
            "lastUpdatedTimestamp" to System.currentTimeMillis()
        )
        driversRef.child(profile.driverId).setValue(driverMap)
            .addOnSuccessListener {
                Log.d(TAG, "Driver profile successfully synced to Firebase: ${profile.driverId}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync driver profile to Firebase: ${e.message}", e)
            }
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
                        val location = child.child("lastLocationName").getValue(String::class.java) ?: "Active Depot"

                        driversList.add(
                            DriverUnit(
                                driverId = driverId,
                                driverName = name,
                                vehiclePlate = plate,
                                status = status,
                                batteryPercent = battery,
                                lastLocation = location
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
            "status" to order.status.name,
            "timestamp" to order.timestamp
        )
        ordersRef.child(order.orderId).setValue(orderMap)
    }

    fun updateOrderStatus(orderId: String, status: DispatchStatus) {
        ordersRef.child(orderId).child("status").setValue(status.name)
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
}
