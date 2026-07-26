package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DriverProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.driverDataStore: DataStore<Preferences> by preferencesDataStore(name = "driver_profile_prefs")

class DriverProfileRepository(private val context: Context) {

    companion object {
        val KEY_DRIVER_ID = stringPreferencesKey("driver_id")
        val KEY_DRIVER_NAME = stringPreferencesKey("driver_name")
        val KEY_PHONE_NUMBER = stringPreferencesKey("phone_number")
        val KEY_VEHICLE_PLATE = stringPreferencesKey("vehicle_plate")
        val KEY_VEHICLE_MODEL = stringPreferencesKey("vehicle_model")
        val KEY_PHOTO_URI = stringPreferencesKey("photo_uri")
        val KEY_IS_ONLINE = booleanPreferencesKey("is_online")
        val KEY_ADMIN_PIN = stringPreferencesKey("admin_pin")
        val KEY_FLEET_CODE = stringPreferencesKey("fleet_code")
    }

    val driverProfileFlow: Flow<DriverProfile> = context.driverDataStore.data.map { prefs ->
        val existingId = prefs[KEY_DRIVER_ID] ?: "DRV-${(105..999).random()}"
        DriverProfile(
            driverId = existingId,
            driverName = prefs[KEY_DRIVER_NAME] ?: "Driver ${existingId.takeLast(3)}",
            phoneNumber = prefs[KEY_PHONE_NUMBER] ?: "+1 555-0100",
            vehiclePlate = prefs[KEY_VEHICLE_PLATE] ?: "TAX-${(100..999).random()}",
            vehicleModel = prefs[KEY_VEHICLE_MODEL] ?: "Sedan Taxi",
            photoUri = prefs[KEY_PHOTO_URI] ?: "",
            isOnline = prefs[KEY_IS_ONLINE] ?: true,
            status = if (prefs[KEY_IS_ONLINE] != false) "AVAILABLE" else "OFFLINE",
            fleetNetworkCode = prefs[KEY_FLEET_CODE] ?: "GET-TAXI-NETWORK-1"
        )
    }

    val adminPinFlow: Flow<String> = context.driverDataStore.data.map { prefs ->
        prefs[KEY_ADMIN_PIN] ?: "1234"
    }

    suspend fun saveProfile(profile: DriverProfile) {
        context.driverDataStore.edit { prefs ->
            prefs[KEY_DRIVER_ID] = profile.driverId
            prefs[KEY_DRIVER_NAME] = profile.driverName
            prefs[KEY_PHONE_NUMBER] = profile.phoneNumber
            prefs[KEY_VEHICLE_PLATE] = profile.vehiclePlate
            prefs[KEY_VEHICLE_MODEL] = profile.vehicleModel
            prefs[KEY_PHOTO_URI] = profile.photoUri
            prefs[KEY_IS_ONLINE] = profile.isOnline
            prefs[KEY_FLEET_CODE] = profile.fleetNetworkCode
        }
    }

    suspend fun updateAdminPin(pin: String) {
        context.driverDataStore.edit { prefs ->
            prefs[KEY_ADMIN_PIN] = pin
        }
    }

    suspend fun getOrInitProfile(): DriverProfile {
        val current = driverProfileFlow.first()
        // Save initial values if needed
        saveProfile(current)
        return current
    }
}
