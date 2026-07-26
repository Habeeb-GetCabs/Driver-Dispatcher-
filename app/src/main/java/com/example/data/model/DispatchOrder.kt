package com.example.data.model

data class DispatchOrder(
    val orderId: String,
    val passengerName: String,
    val passengerPhone: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val estimatedFare: Double,
    val notes: String = "",
    val assignedDriverId: String = "ALL",
    val assignedByAdmin: String = "Master Admin",
    val customBaseFare: Double = 0.0,
    val customRatePerKm: Double = 0.0,
    val status: DispatchStatus = DispatchStatus.DISPATCHED,
    val timestamp: Long = System.currentTimeMillis()
)

data class AdminAccount(
    val adminId: String = "",
    val name: String = "",
    val pin: String = "",
    val role: String = "SUB_ADMIN" // "MASTER_ADMIN" or "SUB_ADMIN"
)

enum class DispatchStatus {
    DISPATCHED,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    DECLINED
}

data class DriverUnit(
    val driverId: String,
    val driverName: String,
    val vehiclePlate: String,
    val status: String, // "AVAILABLE", "ON_TRIP", "BUSY", "OFFLINE"
    val batteryPercent: Int = 95,
    val lastLocation: String = "GPS Active",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photoUrl: String = "",
    val phoneNumber: String = ""
)
