package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DispatchOrder
import com.example.data.model.DispatchStatus
import com.example.data.model.DriverUnit
import com.example.ui.components.TripMapView
import com.example.viewmodel.DispatchViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.window.DialogProperties

private fun getResolvedLocationName(context: android.content.Context, driver: DriverUnit): String {
    val loc = driver.lastLocation.trim()
    if (loc.isNotBlank() && loc != "GPS Active" && loc != "Locating..." && !loc.startsWith("GPS:")) {
        return loc
    }
    val lat = driver.latitude
    val lng = driver.longitude
    if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val subLocality = addr.subLocality ?: addr.featureName
                val city = addr.locality ?: addr.subAdminArea
                val parts = listOfNotNull(subLocality, city).distinct().filter { it.isNotBlank() }
                if (parts.isNotEmpty()) {
                    return parts.joinToString(", ")
                }
            }
        } catch (e: Exception) {
            // Geocoder fallback
        }
        return String.format(java.util.Locale.US, "GPS: %.4f, %.4f", lat, lng)
    }
    return if (loc.isNotBlank()) loc else "GPS Active"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatcherScreen(
    dispatchViewModel: DispatchViewModel,
    currencySymbol: String = "₹",
    onSwitchToMeterMode: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val orders by dispatchViewModel.dispatchOrders.collectAsStateWithLifecycle()
    val drivers by dispatchViewModel.fleetDrivers.collectAsStateWithLifecycle()
    val selfProfile by dispatchViewModel.driverProfile.collectAsStateWithLifecycle()
    val currentAdmin by dispatchViewModel.currentAdmin.collectAsStateWithLifecycle()
    val adminAccounts by dispatchViewModel.adminAccounts.collectAsStateWithLifecycle()
    val dispatchHistory by dispatchViewModel.dispatchHistory.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0 = Dispatch Form, 1 = Fleet Live Status, 2 = Orders Feed

    // Selected driver for inspection
    var selectedDriverForDetails by remember { mutableStateOf<DriverUnit?>(null) }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (selectedDriverForDetails != null) {
            selectedDriverForDetails = null
            return@BackHandler
        }
        if (activeTab != 0) {
            activeTab = 0
            return@BackHandler
        }

        if (currentTime - lastBackPressTime < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Form inputs
    var passengerName by remember { mutableStateOf("") }
    var passengerPhone by remember { mutableStateOf("") }
    var pickupAddress by remember { mutableStateOf("") }
    var destinationAddress by remember { mutableStateOf("") }
    var estimatedFareStr by remember { mutableStateOf("") }
    var customBaseFareStr by remember { mutableStateOf("") }
    var customRatePerKmStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedDriverId by remember { mutableStateOf("ALL") }

    var dispatchDriverSearchQuery by remember { mutableStateOf("") }

    val dispatchFilteredDrivers = remember(drivers, dispatchDriverSearchQuery) {
        if (dispatchDriverSearchQuery.isBlank()) {
            drivers
        } else {
            val q = dispatchDriverSearchQuery.trim().lowercase(java.util.Locale.ROOT)
            drivers.filter { driver ->
                val resolvedLoc = getResolvedLocationName(context, driver).lowercase(java.util.Locale.ROOT)
                driver.driverName.lowercase(java.util.Locale.ROOT).contains(q) ||
                driver.driverId.lowercase(java.util.Locale.ROOT).contains(q) ||
                "drv-${driver.driverId.lowercase(java.util.Locale.ROOT)}".contains(q) ||
                driver.vehiclePlate.lowercase(java.util.Locale.ROOT).contains(q) ||
                driver.phoneNumber.lowercase(java.util.Locale.ROOT).contains(q) ||
                driver.lastLocation.lowercase(java.util.Locale.ROOT).contains(q) ||
                resolvedLoc.contains(q)
            }
        }
    }

    var fleetSearchQuery by remember { mutableStateOf("") }

    val filteredDrivers = remember(drivers, fleetSearchQuery) {
        if (fleetSearchQuery.isBlank()) {
            drivers
        } else {
            val q = fleetSearchQuery.trim().lowercase(java.util.Locale.ROOT)
            drivers.filter { driver ->
                val resolvedLoc = getResolvedLocationName(context, driver).lowercase(java.util.Locale.ROOT)
                driver.driverName.lowercase(java.util.Locale.ROOT).contains(q) ||
                driver.driverId.lowercase(java.util.Locale.ROOT).contains(q) ||
                "drv-${driver.driverId.lowercase(java.util.Locale.ROOT)}".contains(q) ||
                driver.vehiclePlate.lowercase(java.util.Locale.ROOT).contains(q) ||
                driver.phoneNumber.lowercase(java.util.Locale.ROOT).contains(q) ||
                driver.lastLocation.lowercase(java.util.Locale.ROOT).contains(q) ||
                resolvedLoc.contains(q)
            }
        }
    }

    var broadcastText by remember { mutableStateOf("") }
    var showSuccessToast by remember { mutableStateOf(false) }

    var newDriverIdInput by remember { mutableStateOf("") }
    var newDriverNameInput by remember { mutableStateOf("") }
    var newDriverPlateInput by remember { mutableStateOf("") }
    var isAddDriverExpanded by remember { mutableStateOf(false) }
    var showPurgeDialog by remember { mutableStateOf(false) }
    var purgePinInput by remember { mutableStateOf("") }
    var showPurgeConfirm by remember { mutableStateOf(false) }
    var purgeStatusMessage by remember { mutableStateOf("") }

    var subAdminNameInput by remember { mutableStateOf("") }
    var subAdminPinInput by remember { mutableStateOf("") }
    var isSubAdminSectionExpanded by remember { mutableStateOf(false) }

    val redBrand = Color(0xFFC62828)

    // Standardized text field colors ensuring 100% dark text visibility
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color(0xFF1E1E1E),
        unfocusedTextColor = Color(0xFF1E1E1E),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = redBrand,
        unfocusedBorderColor = Color(0xFFBDBDBD),
        focusedLabelColor = redBrand,
        unfocusedLabelColor = Color(0xFF616161),
        focusedLeadingIconColor = redBrand,
        unfocusedLeadingIconColor = Color(0xFF616161),
        focusedPlaceholderColor = Color.Gray,
        unfocusedPlaceholderColor = Color.Gray
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF8A0000))
            .testTag("dispatcher_screen")
    ) {
        // Red & White Top Bar Header
        Surface(
            color = redBrand,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = "Dispatcher",
                                    tint = redBrand,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "GET TAXI DISPATCH",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "FLEET CONTROL CENTER (ADMIN)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD600)
                            )
                            Text(
                                text = "Logged in: ${currentAdmin?.name ?: "Master Admin (Owner)"}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Switch to Meter Mode Button
                        Button(
                            onClick = onSwitchToMeterMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = redBrand
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("switch_to_driver_mode_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalTaxi,
                                    contentDescription = "Meter Mode",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TAXI METER",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Lock Admin Console
                        IconButton(
                            onClick = { dispatchViewModel.logoutDispatcher() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Admin Console",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs (Form, Fleet, Orders Feed)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabPill(
                        title = "DISPATCH TRIP",
                        icon = Icons.Default.AddLocationAlt,
                        isSelected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabPill(
                        title = "FLEET (${drivers.size})",
                        icon = Icons.Default.Groups,
                        isSelected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    TabPill(
                        title = "ORDERS (${orders.size})",
                        icon = Icons.Default.History,
                        isSelected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (activeTab) {
                0 -> {
                    // DISPATCH TRIP FORM
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = redBrand
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "CREATE NEW DISPATCH ORDER",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = Color(0xFF1E1E1E)
                                        )
                                    }

                                    HorizontalDivider(color = Color(0xFFEEEEEE))

                                    // Pickup & Dropoff Text Fields
                                    OutlinedTextField(
                                        value = pickupAddress,
                                        onValueChange = { pickupAddress = it },
                                        label = { Text("Pickup Address / Location") },
                                        leadingIcon = {
                                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF2E7D32))
                                        },
                                        singleLine = true,
                                        colors = textFieldColors,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_pickup_address")
                                    )

                                    OutlinedTextField(
                                        value = destinationAddress,
                                        onValueChange = { destinationAddress = it },
                                        label = { Text("Destination / Drop-off Location") },
                                        leadingIcon = {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = redBrand)
                                        },
                                        singleLine = true,
                                        colors = textFieldColors,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_destination_address")
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = passengerName,
                                            onValueChange = { passengerName = it },
                                            label = { Text("Passenger Name") },
                                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                            singleLine = true,
                                            colors = textFieldColors,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("input_passenger_name")
                                        )

                                        OutlinedTextField(
                                            value = passengerPhone,
                                            onValueChange = { passengerPhone = it },
                                            label = { Text("Phone") },
                                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            singleLine = true,
                                            colors = textFieldColors,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("input_passenger_phone")
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = estimatedFareStr,
                                            onValueChange = { estimatedFareStr = it },
                                            label = { Text("Est. Fare ($currencySymbol)") },
                                            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            colors = textFieldColors,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("input_estimated_fare")
                                        )

                                        OutlinedTextField(
                                            value = notes,
                                            onValueChange = { notes = it },
                                            label = { Text("Dispatch Notes") },
                                            singleLine = true,
                                            colors = textFieldColors,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Optional Custom Fare Controls
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = customBaseFareStr,
                                            onValueChange = { customBaseFareStr = it },
                                            label = { Text("Custom Base Fare ($currencySymbol)") },
                                            placeholder = { Text("Default Rate") },
                                            leadingIcon = { Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = redBrand) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            colors = textFieldColors,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("input_custom_base_fare")
                                        )

                                        OutlinedTextField(
                                            value = customRatePerKmStr,
                                            onValueChange = { customRatePerKmStr = it },
                                            label = { Text("Custom Rate/KM ($currencySymbol)") },
                                            placeholder = { Text("Default Rate") },
                                            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, tint = redBrand) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            colors = textFieldColors,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("input_custom_rate_per_km")
                                        )
                                    }

                                    // Driver Selection Header
                                    Text(
                                        text = "ASSIGN TO DRIVER OR BROADCAST",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = redBrand
                                    )

                                    // Driver Search Field
                                    OutlinedTextField(
                                        value = dispatchDriverSearchQuery,
                                        onValueChange = { dispatchDriverSearchQuery = it },
                                        placeholder = { Text("Search driver by Name, ID (DRV-0011), or Location (Gandhipuram)...") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = redBrand) },
                                        trailingIcon = {
                                            if (dispatchDriverSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { dispatchDriverSearchQuery = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        colors = textFieldColors,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("dispatch_driver_search_input")
                                    )

                                    // Prominent Broadcast All Option
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedDriverId == "ALL") Color(0xFFFFEBEE) else Color(0xFFF8F9FA)
                                        ),
                                        border = if (selectedDriverId == "ALL") BorderStroke(2.dp, redBrand) else BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedDriverId = "ALL" }
                                            .testTag("broadcast_all_driver_option")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    color = if (selectedDriverId == "ALL") redBrand else Color.Gray,
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.RssFeed,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "BROADCAST TO ALL DRIVERS",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 12.sp,
                                                        color = if (selectedDriverId == "ALL") redBrand else Color(0xFF1E1E1E)
                                                    )
                                                    Text(
                                                        text = "First available driver in fleet can accept order",
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                            if (selectedDriverId == "ALL") {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = redBrand,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Filtered Drivers List
                                    if (dispatchFilteredDrivers.isEmpty()) {
                                        Text(
                                            text = "No drivers matching \"$dispatchDriverSearchQuery\"",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    } else {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            dispatchFilteredDrivers.forEach { d ->
                                                val isSelected = selectedDriverId == d.driverId
                                                val resolvedLoc = getResolvedLocationName(context, d)
                                                val formattedDriverId = if (d.driverId.startsWith("DRV-")) d.driverId else "DRV-${d.driverId}"

                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isSelected) Color(0xFFE8F5E9) else Color(0xFFFAFAFA)
                                                    ),
                                                    border = if (isSelected) BorderStroke(2.dp, Color(0xFF2E7D32)) else BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { selectedDriverId = d.driverId }
                                                        .testTag("assign_driver_item_${d.driverId}")
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Surface(
                                                                color = if (isSelected) Color(0xFF2E7D32) else redBrand,
                                                                shape = CircleShape,
                                                                modifier = Modifier.size(34.dp)
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center) {
                                                                    Text(
                                                                        text = d.driverName.take(1).uppercase(),
                                                                        color = Color.White,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 14.sp
                                                                    )
                                                                }
                                                            }
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Column {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(
                                                                        text = d.driverName,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 13.sp,
                                                                        color = Color(0xFF1E1E1E)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Surface(
                                                                        color = Color(0xFFFFD600),
                                                                        shape = RoundedCornerShape(6.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = formattedDriverId,
                                                                            fontSize = 10.sp,
                                                                            fontWeight = FontWeight.Black,
                                                                            color = Color.Black,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.LocationOn,
                                                                        contentDescription = null,
                                                                        tint = Color.Gray,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(2.dp))
                                                                    Text(
                                                                        text = resolvedLoc,
                                                                        fontSize = 11.sp,
                                                                        color = Color.Gray,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Column(horizontalAlignment = Alignment.End) {
                                                            Surface(
                                                                color = if (d.status == "AVAILABLE" || d.status == "ONLINE") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                                                shape = RoundedCornerShape(8.dp)
                                                            ) {
                                                                Text(
                                                                    text = d.status,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (d.status == "AVAILABLE" || d.status == "ONLINE") Color(0xFF2E7D32) else Color(0xFFE65100),
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                            if (isSelected) {
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                Icon(
                                                                    imageVector = Icons.Default.CheckCircle,
                                                                    contentDescription = "Assigned",
                                                                    tint = Color(0xFF2E7D32),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Dispatch Action Button
                                    Button(
                                        onClick = {
                                            val fare = estimatedFareStr.toDoubleOrNull() ?: 25.0
                                            val customBf = customBaseFareStr.toDoubleOrNull() ?: 0.0
                                            val customFk = customRatePerKmStr.toDoubleOrNull() ?: 0.0

                                            dispatchViewModel.dispatchNewTrip(
                                                passengerName = passengerName,
                                                passengerPhone = passengerPhone,
                                                pickup = pickupAddress,
                                                destination = destinationAddress,
                                                estimatedFare = fare,
                                                notes = notes,
                                                assignedDriverId = selectedDriverId,
                                                customBaseFare = customBf,
                                                customRatePerKm = customFk
                                            )
                                            passengerName = ""
                                            passengerPhone = ""
                                            pickupAddress = ""
                                            destinationAddress = ""
                                            estimatedFareStr = ""
                                            customBaseFareStr = ""
                                            customRatePerKmStr = ""
                                            notes = ""
                                            showSuccessToast = true
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = redBrand,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("dispatch_now_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Campaign, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "DISPATCH TRIP NOW (SOUND ALERT)",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }

                                    // Test Simulation button
                                    OutlinedButton(
                                        onClick = {
                                            dispatchViewModel.simulateIncomingTripFromDispatcher()
                                        },
                                        border = BorderStroke(1.dp, redBrand),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "SIMULATE INCOMING DISPATCH TO THIS DEVICE",
                                            color = redBrand,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (showSuccessToast) {
                                        Surface(
                                            color = Color(0xFFE8F5E9),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "⚡ Trip Order Dispatched & Alert Sounded!",
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // FLEET STATUS MONITOR
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            // FLEET LIVE SEARCH BAR
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Search Fleet",
                                                tint = redBrand
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "FLEET DRIVER SEARCH",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1E1E1E)
                                            )
                                        }
                                        Surface(
                                            color = Color(0xFFEDE7F6),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "${filteredDrivers.size} / ${drivers.size} DRIVERS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF512DA8),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = fleetSearchQuery,
                                        onValueChange = { fleetSearchQuery = it },
                                        placeholder = { Text("Search by Name (Ramesh), ID (112), Area (Gandhipuram)...") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                                        },
                                        trailingIcon = {
                                            if (fleetSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { fleetSearchQuery = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray)
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        colors = textFieldColors,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("fleet_search_input")
                                    )
                                }
                            }
                        }

                        item {
                            // Add Driver Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isAddDriverExpanded = !isAddDriverExpanded },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = redBrand)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "REGISTER DRIVER ID (e.g. 667, 684)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1E1E1E)
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isAddDriverExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }

                                    if (isAddDriverExpanded) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = newDriverIdInput,
                                                onValueChange = { newDriverIdInput = it },
                                                label = { Text("Driver ID (e.g. 667)") },
                                                singleLine = true,
                                                colors = textFieldColors,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = newDriverNameInput,
                                                onValueChange = { newDriverNameInput = it },
                                                label = { Text("Driver Name") },
                                                singleLine = true,
                                                colors = textFieldColors,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = newDriverPlateInput,
                                            onValueChange = { newDriverPlateInput = it },
                                            label = { Text("Vehicle Plate (e.g. TX-667)") },
                                            singleLine = true,
                                            colors = textFieldColors,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                if (newDriverIdInput.isNotBlank()) {
                                                    dispatchViewModel.addDriverToFleet(
                                                        driverId = newDriverIdInput,
                                                        name = newDriverNameInput,
                                                        vehiclePlate = newDriverPlateInput
                                                    )
                                                    newDriverIdInput = ""
                                                    newDriverNameInput = ""
                                                    newDriverPlateInput = ""
                                                    isAddDriverExpanded = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = redBrand),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("ADD DRIVER TO FLEET LIST", fontWeight = FontWeight.Black)
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(
                                            onClick = { showPurgeDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("PURGE ALL DRIVERS & RESET IDs", fontWeight = FontWeight.Black, color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            // Sub-Admin Management Card (Master Admin Only)
                            if (currentAdmin == null || currentAdmin?.role == "MASTER_ADMIN" || currentAdmin?.adminId == "master") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isSubAdminSectionExpanded = !isSubAdminSectionExpanded },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = redBrand)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "SUB-ADMIN MANAGEMENT (MASTER ADMIN)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF1E1E1E)
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isSubAdminSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = Color.Gray
                                            )
                                        }

                                        if (isSubAdminSectionExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = subAdminNameInput,
                                                    onValueChange = { subAdminNameInput = it },
                                                    label = { Text("Sub-Admin Name") },
                                                    singleLine = true,
                                                    colors = textFieldColors,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = subAdminPinInput,
                                                    onValueChange = { subAdminPinInput = it },
                                                    label = { Text("Access PIN") },
                                                    singleLine = true,
                                                    colors = textFieldColors,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    if (subAdminNameInput.isNotBlank() && subAdminPinInput.isNotBlank()) {
                                                        dispatchViewModel.createSubAdmin(subAdminNameInput, subAdminPinInput)
                                                        subAdminNameInput = ""
                                                        subAdminPinInput = ""
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("CREATE SUB-ADMIN ACCOUNT", fontWeight = FontWeight.Black)
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("Registered Sub-Admins:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                            val subAdminsList = adminAccounts.filter { it.role == "SUB_ADMIN" }
                                            if (subAdminsList.isEmpty()) {
                                                Text("No sub-admin accounts created yet.", fontSize = 11.sp, color = Color.Gray)
                                            } else {
                                                subAdminsList.forEach { sa ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(sa.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E1E1E))
                                                            Text("PIN: ${sa.pin}", fontSize = 11.sp, color = Color.Gray)
                                                        }
                                                        IconButton(onClick = { dispatchViewModel.deleteSubAdmin(sa.adminId) }) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = redBrand)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            // Broadcast box
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "BROADCAST ANNOUNCEMENT TO ALL DRIVERS",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = redBrand
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = broadcastText,
                                            onValueChange = { broadcastText = it },
                                            placeholder = { Text("e.g., Heavy traffic near Main Depot") },
                                            singleLine = true,
                                            colors = textFieldColors,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                dispatchViewModel.sendBroadcastToDrivers(broadcastText)
                                                broadcastText = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = redBrand)
                                        ) {
                                            Text("SEND")
                                        }
                                    }
                                }
                            }
                        }

                        if (filteredDrivers.isEmpty() && fleetSearchQuery.isNotBlank()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp)
                                    ) {
                                        Text(
                                            text = "No drivers found matching \"$fleetSearchQuery\"",
                                            fontSize = 13.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredDrivers) { driver ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDriverForDetails = driver }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (driver.status == "AVAILABLE") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                            shape = CircleShape,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (driver.photoUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = driver.photoUrl,
                                                        contentDescription = "Driver Avatar",
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else if (driver.driverId == selfProfile.driverId && selfProfile.photoUri.isNotBlank()) {
                                                    AsyncImage(
                                                        model = selfProfile.photoUri,
                                                        contentDescription = "Driver Avatar",
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.DirectionsCar,
                                                        contentDescription = null,
                                                        tint = if (driver.status == "AVAILABLE") Color(0xFF2E7D32) else redBrand
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "${driver.driverName} (${driver.driverId})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color(0xFF1E1E1E)
                                            )
                                            val locDisplay = if (driver.latitude != null && driver.longitude != null && (driver.latitude != 0.0 || driver.longitude != 0.0)) driver.lastLocation else "Locating..."
                                            Text(
                                                text = "Plate: ${driver.vehiclePlate} • $locDisplay",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (driver.status == "AVAILABLE") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = driver.status,
                                                color = if (driver.status == "AVAILABLE") Color(0xFF2E7D32) else redBrand,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "View Details",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // DISPATCH ORDERS & AUDIT HISTORY FEED
                    val historyOrders = remember(orders, dispatchHistory) {
                        (orders + dispatchHistory).distinctBy { it.orderId }.sortedByDescending { it.timestamp }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(historyOrders) { order ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ORDER #${order.orderId}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = redBrand
                                        )
                                        Surface(
                                            color = when (order.status) {
                                                DispatchStatus.ACCEPTED -> Color(0xFFE8F5E9)
                                                DispatchStatus.DECLINED -> Color(0xFFFFEBEE)
                                                else -> Color(0xFFFFF8E1)
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = order.status.name,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (order.status) {
                                                    DispatchStatus.ACCEPTED -> Color(0xFF2E7D32)
                                                    DispatchStatus.DECLINED -> redBrand
                                                    else -> Color(0xFFF57F17)
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Passenger: ${order.passengerName} (${order.passengerPhone})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E1E1E)
                                    )
                                    Text(
                                        text = "📍 Pickup: ${order.pickupAddress}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = "🏁 Dropoff: ${order.destinationAddress}",
                                        fontSize = 12.sp,
                                        color = redBrand
                                    )
                                    Text(
                                        text = "Fare: $currencySymbol${String.format("%.2f", order.estimatedFare)} • Driver: ${order.assignedDriverId}",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = Color(0xFFEDE7F6),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Assigned by: ${order.assignedByAdmin.ifBlank { "Master Admin" }}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF512DA8),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    if (order.customBaseFare > 0.0 || order.customRatePerKm > 0.0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            color = Color(0xFFE0F2F1),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Custom Rate: Base $currencySymbol${String.format(java.util.Locale.US, "%.2f", order.customBaseFare)} • Rate/KM $currencySymbol${String.format(java.util.Locale.US, "%.2f", order.customRatePerKm)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00695C),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DRIVER INSPECTION FULL-SCREEN VIEW
    if (selectedDriverForDetails != null) {
        val d = selectedDriverForDetails!!
        var isMapFullscreen by remember { mutableStateOf(false) }
        var showDeleteConfirmDialog by remember { mutableStateOf(false) }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { selectedDriverForDetails = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFF8FAFC)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Header Bar
                    Surface(
                        color = redBrand,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedDriverForDetails = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "DRIVER DETAILS #${d.driverId}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }

                            // DELETE DRIVER / REMOVE FROM FLEET BUTTON
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Driver", tint = redBrand, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("REMOVE DRIVER", color = redBrand, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Content Body
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Driver Info Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Circular Avatar
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFEBEE))
                                        .border(2.dp, redBrand, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (d.photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = d.photoUrl,
                                            contentDescription = "Driver Avatar",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Driver",
                                            tint = redBrand,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = d.driverName,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = Color(0xFF1E1E1E)
                                    )
                                    Text(
                                        text = "Vehicle Plate: ${d.vehiclePlate}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF424242)
                                    )
                                    if (d.phoneNumber.isNotBlank()) {
                                        Text(
                                            text = "Phone: ${d.phoneNumber}",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    val locName = if (d.latitude != null && d.longitude != null && (d.latitude != 0.0 || d.longitude != 0.0)) d.lastLocation else "Locating..."
                                    Text(
                                        text = "Location: $locName",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = if (d.status == "AVAILABLE") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = d.status,
                                                color = if (d.status == "AVAILABLE") Color(0xFF2E7D32) else redBrand,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = "🔋 ${d.batteryPercent}% Battery",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        // Live Location Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = redBrand, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE GPS TRACKING MAP",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = Color(0xFF1E1E1E)
                                )
                            }
                            Text(
                                text = "Tap map to enlarge",
                                fontSize = 11.sp,
                                color = redBrand,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // ENLARGED MAP CONTAINER
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { isMapFullscreen = true }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                TripMapView(
                                    tripState = com.example.data.model.TripState(
                                        latitude = d.latitude,
                                        longitude = d.longitude,
                                        status = com.example.data.model.TripStatus.RUNNING
                                    ),
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    color = Color.Black.copy(alpha = 0.70f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Expand", tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("FULLSCREEN MAP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // EXPANDED FULLSCREEN MAP OVERLAY
                if (isMapFullscreen) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { isMapFullscreen = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            TripMapView(
                                tripState = com.example.data.model.TripState(
                                    latitude = d.latitude,
                                    longitude = d.longitude,
                                    status = com.example.data.model.TripStatus.RUNNING
                                ),
                                modifier = Modifier.fillMaxSize()
                            )

                            FloatingActionButton(
                                onClick = { isMapFullscreen = false },
                                containerColor = redBrand,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Fullscreen")
                            }
                        }
                    }
                }

                // CONFIRM DELETE DRIVER DIALOG
                if (showPurgeDialog) {
                    AlertDialog(
                        onDismissRequest = { showPurgeDialog = false; purgePinInput = "" },
                        title = { Text("Admin Authorization") },
                        text = {
                            Column {
                                Text("Enter Admin PIN to purge fleet:")
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = purgePinInput,
                                    onValueChange = { purgePinInput = it },
                                    label = { Text("PIN") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                    singleLine = true
                                )
                                if (purgeStatusMessage.isNotEmpty()) {
                                    Text(purgeStatusMessage, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (purgePinInput == "2481") {
                                    showPurgeDialog = false
                                    purgePinInput = ""
                                    showPurgeConfirm = true
                                } else {
                                    purgeStatusMessage = "Invalid PIN"
                                }
                            }) {
                                Text("NEXT")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPurgeDialog = false; purgePinInput = ""; purgeStatusMessage = "" }) {
                                Text("CANCEL")
                            }
                        }
                    )
                }
                if (showPurgeConfirm) {
                    AlertDialog(
                        onDismissRequest = { showPurgeConfirm = false },
                        title = { Text("⚠️ WARNING: DATA PURGE", color = Color.Red, fontWeight = FontWeight.Bold) },
                        text = {
                            Text("This will permanently delete all driver profiles from Firebase and reset driver IDs to DRV-0001. Proceed?")
                        },
                        confirmButton = {
                            Button(onClick = {
                                com.example.data.remote.FirebaseSyncManager.purgeAllDrivers { success ->
                                    if (success) {
                                        showPurgeConfirm = false
                                    }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                Text("PURGE DATA", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPurgeConfirm = false }) {
                                Text("CANCEL")
                            }
                        }
                    )
                }
                if (showDeleteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        title = { Text("Delete Driver Record?", fontWeight = FontWeight.Black) },
                        text = {
                            Text("Are you sure you want to remove Driver ${d.driverName} (${d.driverId}) from active fleet database? This action is permanent.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    dispatchViewModel.removeDriverFromFleet(d.driverId)
                                    showDeleteConfirmDialog = false
                                    selectedDriverForDetails = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = redBrand)
                            ) {
                                Text("YES, DELETE DRIVER", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                Text("CANCEL", color = Color.Gray)
                            }
                        },
                        containerColor = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TabPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
        contentColor = if (isSelected) Color(0xFFC62828) else Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun PresetChip(text: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFFFFEBEE),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = Color(0xFFC62828),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun DriverChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) Color(0xFFC62828) else Color(0xFFEEEEEE),
        contentColor = if (isSelected) Color.White else Color.Black,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
