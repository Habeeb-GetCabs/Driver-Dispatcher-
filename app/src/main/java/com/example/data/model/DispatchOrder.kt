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
    val status: DispatchStatus = DispatchStatus.DISPATCHED,
    val timestamp: Long = System.currentTimeMillis()
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
    val lastLocation: String = "Downtown Central"
)
