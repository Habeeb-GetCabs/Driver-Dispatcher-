package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DriverProfile
import com.example.viewmodel.DispatchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverProfileScreen(
    dispatchViewModel: DispatchViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val currentProfile by dispatchViewModel.driverProfile.collectAsState()

    var customDriverId by remember(currentProfile) { mutableStateOf(currentProfile.driverId) }
    var name by remember(currentProfile) { mutableStateOf(currentProfile.driverName) }
    var phone by remember(currentProfile) { mutableStateOf(currentProfile.phoneNumber) }
    var vehiclePlate by remember(currentProfile) { mutableStateOf(currentProfile.vehiclePlate) }
    var vehicleModel by remember(currentProfile) { mutableStateOf(currentProfile.vehicleModel) }
    var photoUriStr by remember(currentProfile) { mutableStateOf(currentProfile.photoUri) }
    var fleetCode by remember(currentProfile) { mutableStateOf(currentProfile.fleetNetworkCode) }
    var isOnline by remember(currentProfile) { mutableStateOf(currentProfile.isOnline) }

    var isSavedToastVisible by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            photoUriStr = it.toString()
        }
    }

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
        unfocusedLeadingIconColor = Color(0xFF616161)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Driver Profile & ID", color = Color.White, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = redBrand)
            )
        },
        containerColor = Color(0xFF8A0000)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // DRIVER ID BADGE CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Photo / Selfie Selector
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFEBEE))
                            .border(3.dp, redBrand, CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUriStr.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Driver Selfie",
                                tint = redBrand,
                                modifier = Modifier.size(60.dp)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_get_taxi_vector),
                                contentDescription = "Default Avatar",
                                modifier = Modifier.size(70.dp)
                            )
                        }

                        // Camera Icon Overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                                .background(redBrand, CircleShape)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change Selfie",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "UNIQUE DRIVER ASSIGNED ID",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Gray,
                        letterSpacing = 1.5.sp
                    )

                    // Auto-Assigned Driver ID Badge
                    Surface(
                        color = Color(0xFFFFD600),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = customDriverId.ifBlank { currentProfile.driverId },
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .testTag("driver_assigned_id")
                        )
                    }

                    Text(
                        text = "This ID is automatically registered to your device and synced with Dispatch Admin.",
                        fontSize = 11.sp,
                        color = Color(0xFF616161),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // DRIVER DETAILS FORM CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "DRIVER & VEHICLE DETAILS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = redBrand,
                        letterSpacing = 1.sp
                    )

                    HorizontalDivider(color = Color(0xFFEEEEEE))

                    // Driver Assigned ID field
                    OutlinedTextField(
                        value = customDriverId,
                        onValueChange = { customDriverId = it },
                        label = { Text("Driver ID (e.g., DRV-667 or 684)") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_driver_id")
                    )

                    // Full Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Driver Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_driver_name")
                    )

                    // Phone Number
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_driver_phone")
                    )

                    // Vehicle Plate Number
                    OutlinedTextField(
                        value = vehiclePlate,
                        onValueChange = { vehiclePlate = it },
                        label = { Text("Vehicle License Plate Number") },
                        leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_vehicle_plate")
                    )

                    // Vehicle Model
                    OutlinedTextField(
                        value = vehicleModel,
                        onValueChange = { vehicleModel = it },
                        label = { Text("Vehicle Make & Model (e.g. Toyota Prius)") },
                        leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_vehicle_model")
                    )

                    // Fleet Network Code
                    OutlinedTextField(
                        value = fleetCode,
                        onValueChange = { fleetCode = it },
                        label = { Text("Fleet Network Code (Join Same Fleet)") },
                        leadingIcon = { Icon(Icons.Default.CellTower, contentDescription = null) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_fleet_code")
                    )

                    // Online Duty Status Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DUTY STATUS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1E1E)
                            )
                            Text(
                                text = if (isOnline) "ONLINE / Available for Trips" else "OFFLINE / Off Duty",
                                fontSize = 11.sp,
                                color = if (isOnline) Color(0xFF2E7D32) else Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = isOnline,
                            onCheckedChange = { isOnline = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = redBrand)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Save Profile Button
                    Button(
                        onClick = {
                            val updated = currentProfile.copy(
                                driverId = customDriverId.ifBlank { currentProfile.driverId },
                                driverName = name,
                                phoneNumber = phone,
                                vehiclePlate = vehiclePlate,
                                vehicleModel = vehicleModel,
                                photoUri = photoUriStr,
                                isOnline = isOnline,
                                status = if (isOnline) "AVAILABLE" else "OFFLINE",
                                fleetNetworkCode = fleetCode,
                                lastUpdatedTimestamp = System.currentTimeMillis()
                            )
                            dispatchViewModel.updateDriverProfile(updated)
                            isSavedToastVisible = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = redBrand,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_profile_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SAVE & UPDATE PROFILE",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    if (isSavedToastVisible) {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✅ Profile updated and synced with Fleet Network!",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
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
