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
        val existingId = prefs[KEY_DRIVER_ID] ?: ""
        DriverProfile(
            driverId = existingId.ifBlank { "DRV-0011" },
            driverName = prefs[KEY_DRIVER_NAME] ?: "Driver ${existingId.takeLast(4).ifBlank { "0011" }}",
            phoneNumber = prefs[KEY_PHONE_NUMBER] ?: "+1 555-0100",
            vehiclePlate = prefs[KEY_VEHICLE_PLATE] ?: "TAX-${(100..999).random()}",
            vehicleModel = prefs[KEY_VEHICLE_MODEL] ?: "Sedan Taxi",
            photoUri = prefs[KEY_PHOTO_URI] ?: "",
            isOnline = prefs[KEY_IS_ONLINE] ?: true,
            status = if (prefs[KEY_IS_ONLINE] != false) "AVAILABLE" else "OFFLINE",
            fleetNetworkCode = prefs[KEY_FLEET_CODE] ?: "GET-TAXI-NETWORK-1"
        )
    }

    suspend fun ensureDriverIdAssigned(): DriverProfile {
        val prefs = context.driverDataStore.data.first()
        val existingId = prefs[KEY_DRIVER_ID]
        val phoneNumber = prefs[KEY_PHONE_NUMBER] ?: ""
        if (existingId.isNullOrBlank() || existingId == "DRV-0011") {
            val firebaseId = if (phoneNumber.isNotBlank() && phoneNumber != "+1 555-0100") {
                com.example.data.remote.FirebaseSyncManager.findExistingDriverIdByPhoneOrUid(phoneNumber)
            } else {
                null
            }
            val finalId = if (!firebaseId.isNullOrBlank()) {
                firebaseId
            } else {
                com.example.data.remote.FirebaseSyncManager.getOrGenerateSequentialDriverId()
            }
            val defaultName = if (prefs[KEY_DRIVER_NAME].isNullOrBlank() || prefs[KEY_DRIVER_NAME]?.startsWith("Driver ") == true) {
                "Driver ${finalId.takeLast(4)}"
            } else {
                prefs[KEY_DRIVER_NAME]!!
            }
            context.driverDataStore.edit { p ->
                p[KEY_DRIVER_ID] = finalId
                p[KEY_DRIVER_NAME] = defaultName
            }
        }
        return driverProfileFlow.first()
    }

    val adminPinFlow: Flow<String> = context.driverDataStore.data.map { prefs ->
        prefs[KEY_ADMIN_PIN] ?: "1403"
    }

    suspend fun saveProfile(profile: DriverProfile) {
        var resolvedId = profile.driverId
        if (resolvedId.isBlank() || resolvedId == "DRV-0011") {
            val firebaseId = if (profile.phoneNumber.isNotBlank() && profile.phoneNumber != "+1 555-0100") {
                com.example.data.remote.FirebaseSyncManager.findExistingDriverIdByPhoneOrUid(profile.phoneNumber)
            } else {
                null
            }
            resolvedId = if (!firebaseId.isNullOrBlank()) {
                firebaseId
            } else {
                com.example.data.remote.FirebaseSyncManager.getOrGenerateSequentialDriverId()
            }
        } else {
            if (profile.phoneNumber.isNotBlank() && profile.phoneNumber != "+1 555-0100") {
                val existingFirebaseId = com.example.data.remote.FirebaseSyncManager.findExistingDriverIdByPhoneOrUid(profile.phoneNumber)
                if (!existingFirebaseId.isNullOrBlank() && existingFirebaseId != resolvedId) {
                    resolvedId = existingFirebaseId
                }
            }
        }

        val updatedProfile = profile.copy(driverId = resolvedId)

        context.driverDataStore.edit { prefs ->
            prefs[KEY_DRIVER_ID] = updatedProfile.driverId
            prefs[KEY_DRIVER_NAME] = updatedProfile.driverName
            prefs[KEY_PHONE_NUMBER] = updatedProfile.phoneNumber
            prefs[KEY_VEHICLE_PLATE] = updatedProfile.vehiclePlate
            prefs[KEY_VEHICLE_MODEL] = updatedProfile.vehicleModel
            prefs[KEY_PHOTO_URI] = updatedProfile.photoUri
            prefs[KEY_IS_ONLINE] = updatedProfile.isOnline
            prefs[KEY_FLEET_CODE] = updatedProfile.fleetNetworkCode
        }
        try {
            com.example.data.remote.FirebaseSyncManager.syncDriverProfile(updatedProfile)
        } catch (e: Exception) {
            android.util.Log.e("DriverProfileRepository", "Failed to sync profile to Firebase", e)
        }
    }

    suspend fun updateAdminPin(pin: String) {
        context.driverDataStore.edit { prefs ->
            prefs[KEY_ADMIN_PIN] = pin
        }
    }

    suspend fun clearProfile() {
        context.driverDataStore.edit { prefs ->
            prefs.remove(KEY_DRIVER_ID)
            prefs.remove(KEY_DRIVER_NAME)
            prefs.remove(KEY_PHONE_NUMBER)
            prefs.remove(KEY_VEHICLE_PLATE)
        }
    }
    suspend fun getOrInitProfile(): DriverProfile {
        val current = driverProfileFlow.first()
        // Save initial values if needed
        saveProfile(current)
        return current
    }
}
