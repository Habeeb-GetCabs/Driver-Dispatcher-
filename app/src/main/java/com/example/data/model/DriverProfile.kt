package com.example.data.model

data class DriverProfile(
    val driverId: String = "DRV-104",
    val driverName: String = "Self Driver",
    val phoneNumber: String = "+1 (555) 019-2834",
    val vehiclePlate: String = "TX-1001",
    val vehicleModel: String = "Toyota Prius Hybrid",
    val photoUri: String = "",
    val isOnline: Boolean = true,
    val status: String = "AVAILABLE", // "AVAILABLE", "ON_TRIP", "BUSY", "OFFLINE"
    val latitude: Double? = 37.7749,
    val longitude: Double? = -122.4194,
    val lastLocationName: String = "Downtown Central Depot",
    val batteryPercent: Int = 98,
    val completedTripsCount: Int = 12,
    val rating: Double = 4.9,
    val fleetNetworkCode: String = "GET-TAXI-NETWORK-1",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
