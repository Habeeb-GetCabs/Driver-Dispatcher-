package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.database.TripEntity
import com.example.data.model.DispatchOrder
import com.example.data.model.TripState
import com.example.data.model.TripStatus
import com.example.ui.components.AdminAuthPinModal
import com.example.ui.components.SpeedometerGauge
import com.example.ui.components.TripDispatchAlertModal
import com.example.ui.components.TripMapView
import com.example.viewmodel.AppRole
import com.example.viewmodel.DispatchViewModel
import com.example.viewmodel.MeterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMeterScreen(
    viewModel: MeterViewModel,
    dispatchViewModel: DispatchViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToReceipt: (Int) -> Unit
) {
    val context = LocalContext.current
    val tripState by viewModel.tripState.collectAsStateWithLifecycle()
    val allTrips by viewModel.allTrips.collectAsStateWithLifecycle()
    val hasBackup by viewModel.hasActiveSessionBackup.collectAsStateWithLifecycle()

    val currentRole by dispatchViewModel.currentRole.collectAsStateWithLifecycle()
    val activeAlertTrip by dispatchViewModel.activeAlertTrip.collectAsStateWithLifecycle()
    val acceptedEnRouteTrip by dispatchViewModel.acceptedEnRouteTrip.collectAsStateWithLifecycle()
    val broadcastMessage by dispatchViewModel.driverBroadcastMessage.collectAsStateWithLifecycle()
    val driverProfile by dispatchViewModel.driverProfile.collectAsStateWithLifecycle()
    val isDispatcherAuthenticated by dispatchViewModel.isDispatcherAuthenticated.collectAsStateWithLifecycle()

    var showAdminAuthModal by remember { mutableStateOf(false) }

    val brandRed = Color(0xFFC62828)

    // Permission handling states
    var locationPermissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionsGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Register permissions on initial load
    LaunchedEffect(key1 = true) {
        viewModel.checkForActiveBackup()

        val required = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            launcher.launch(required.toTypedArray())
        }
    }

    // Trigger alert modal if incoming order arrives
    if (activeAlertTrip != null) {
        TripDispatchAlertModal(
            order = activeAlertTrip!!,
            currencySymbol = tripState.currency,
            onAccept = { order ->
                dispatchViewModel.acceptTrip(order.orderId)
            },
            onDecline = { order ->
                dispatchViewModel.declineTrip(order.orderId)
            }
        )
    }

    // IF ROLE IS DISPATCHER -> RENDER DISPATCHER SCREEN HUB
    if (currentRole == AppRole.DISPATCHER) {
        DispatcherScreen(
            dispatchViewModel = dispatchViewModel,
            currencySymbol = tripState.currency,
            onSwitchToMeterMode = { dispatchViewModel.setRole(AppRole.DRIVER) }
        )
        return
    }

    // DRIVER MODE - TAXI METER
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_get_taxi_vector),
                                    contentDescription = "Get Taxi Logo",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "GET TAXI",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "DRIVER METER",
                                color = Color(0xFFFFD600),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    // Driver Profile Button
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.testTag("driver_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = "Driver Profile",
                            tint = Color.White
                        )
                    }

                    // Role Switch Button with Admin Security Check
                    Button(
                        onClick = {
                            if (isDispatcherAuthenticated) {
                                dispatchViewModel.setRole(AppRole.DISPATCHER)
                            } else {
                                showAdminAuthModal = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = brandRed
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("switch_to_dispatcher_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CellTower,
                                contentDescription = "Dispatcher",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DISPATCH",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .testTag("settings_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = brandRed
                )
            )
        },
        containerColor = Color(0xFF8A0000) // Deep Red Brand Canvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ADMIN PIN MODAL DIALOG
            if (showAdminAuthModal) {
                AdminAuthPinModal(
                    onDismiss = { showAdminAuthModal = false },
                    onPinSuccess = {
                        showAdminAuthModal = false
                    },
                    onVerifyPin = { pin ->
                        dispatchViewModel.verifyAdminPin(pin)
                    }
                )
            }

            // DRIVER ASSIGNED ID BANNER CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
                    .clickable { onNavigateToProfile() }
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFFFD600),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = driverProfile.driverId,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = driverProfile.driverName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E1E1E)
                            )
                            Text(
                                text = "Plate: ${driverProfile.vehiclePlate} • ${driverProfile.phoneNumber}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "PROFILE",
                            color = brandRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Dispatcher Announcement Banner
            if (broadcastMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD600)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DISPATCH: ${broadcastMessage}",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(
                            onClick = { dispatchViewModel.clearBroadcast() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.Black)
                        }
                    }
                }
            }

            // ACCEPTED / EN ROUTE TO PICKUP SCREEN CARD
            if (acceptedEnRouteTrip != null) {
                val order = acceptedEnRouteTrip!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ACCEPTED / EN ROUTE TO PICKUP",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }

                            Text(
                                text = "#${order.orderId}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        HorizontalDivider(color = Color(0xFFEEEEEE))

                        // Customer Details & Phone Call
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PASSENGER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(
                                    text = order.passengerName,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Color(0xFF1E1E1E)
                                )
                                Text(
                                    text = order.passengerPhone,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = brandRed
                                )
                            }

                            if (order.passengerPhone.isNotBlank()) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_DIAL,
                                                android.net.Uri.parse("tel:${order.passengerPhone}")
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Handle dialer error
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Phone,
                                            contentDescription = "Call Customer",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("CALL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Addresses
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pickup: ${order.pickupAddress}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E1E1E)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = brandRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Dropoff: ${order.destinationAddress}",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Color(0xFF424242)
                                )
                            }
                        }

                        if (order.notes.isNotBlank()) {
                            Surface(
                                color = Color(0xFFFFF8E1),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Note: ${order.notes}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // EXPLICIT TRIGGER BUTTON TO START TAXIMETER
                        Button(
                            onClick = {
                                dispatchViewModel.startMeterForAcceptedTrip()
                                viewModel.startTrip()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("arrived_start_meter_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ARRIVED / START METER",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        TextButton(
                            onClick = { dispatchViewModel.cancelAcceptedEnRouteTrip() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Cancel Trip", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Backup Active Session Card
            if (hasBackup && tripState.status == TripStatus.IDLE) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore Active Session",
                            tint = brandRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Unfinished Ride Detected",
                            color = brandRed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Your last tracking session was interrupted. Resume ride?",
                            color = Color(0xFF616161),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.discardBackup() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Discard", color = Color(0xFF424242), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.recoverTrip() },
                                colors = ButtonDefaults.buttonColors(containerColor = brandRed),
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Resume Ride", color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            // Status & GPS indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GPS DIAGNOSTICS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (tripState.latitude != null) Color(0xFF00E676) else Color(0xFFFF5252),
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (tripState.latitude != null) "GPS Signal Active" else "Acquiring GPS...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Vacant / Hired badge
                Surface(
                    color = when (tripState.status) {
                        TripStatus.RUNNING -> Color.White
                        TripStatus.PAUSED -> Color(0xFFFFD600)
                        else -> Color.White.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = when (tripState.status) {
                            TripStatus.RUNNING -> "HIRED / ON RIDE"
                            TripStatus.PAUSED -> "METER PAUSED"
                            else -> "VACANT / READY"
                        },
                        color = when (tripState.status) {
                            TripStatus.RUNNING -> brandRed
                            TripStatus.PAUSED -> Color.Black
                            else -> Color.White
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // ROUTE MAP VIEW
            TripMapView(
                tripState = tripState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                    .padding(vertical = 4.dp)
            )

            // GIGANTIC RED & WHITE FARE DISPLAY CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "TOTAL RIDE FARE",
                        color = brandRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 2.5.sp
                    )

                    Box(
                        modifier = Modifier.padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = String.format(Locale.US, "%.2f", tripState.currentFare),
                                color = Color(0xFF1E1E1E),
                                fontSize = 68.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("fare_text")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tripState.currency,
                                color = brandRed,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    // Distance & Wait time boxes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Distance Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(20.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("DISTANCE", color = brandRed, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f", tripState.distanceKm),
                                        color = Color(0xFF1E1E1E),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("KM", color = brandRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Wait Time Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(20.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("WAIT TIME", color = brandRed, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    val formattedWait = formatDuration(tripState.waitingSeconds).substring(3)
                                    Text(
                                        text = formattedWait,
                                        color = Color(0xFF1E1E1E),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("MIN", color = brandRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ACTION BUTTONS (START METER / PAUSE / END TRIP)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 4.dp)
            ) {
                AnimatedContent(
                    targetState = tripState.status,
                    transitionSpec = {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                    },
                    label = "MainActionControls"
                ) { status ->
                    when (status) {
                        TripStatus.IDLE, TripStatus.FINISHED -> {
                            Button(
                                onClick = { viewModel.startTrip() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = brandRed
                                ),
                                shape = RoundedCornerShape(32.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("start_trip_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp), tint = brandRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "START TAXI METER",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                            }
                        }

                        TripStatus.RUNNING -> {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.pauseTrip() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(32.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Pause, contentDescription = null, tint = brandRed)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("PAUSE", fontWeight = FontWeight.Black, color = brandRed, letterSpacing = 1.sp)
                                    }
                                }

                                Button(
                                    onClick = { viewModel.stopTrip() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Black,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(32.dp),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .fillMaxHeight()
                                        .testTag("stop_trip_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("END TRIP", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        }

                        TripStatus.PAUSED -> {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.resumeTrip() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    shape = RoundedCornerShape(32.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .fillMaxHeight()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("RESUME", fontWeight = FontWeight.Black, color = Color.Black, letterSpacing = 1.sp)
                                    }
                                }

                                Button(
                                    onClick = { viewModel.stopTrip() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    shape = RoundedCornerShape(32.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("END TRIP", fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // RIDE TIMINGS PANEL
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ELAPSED RIDE TIME",
                            color = brandRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.0.sp
                        )
                        Text(
                            text = formatDuration(tripState.durationSeconds),
                            color = Color(0xFF1E1E1E),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (tripState.status == TripStatus.RUNNING) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFFFEBEE), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Active ride indicator",
                                tint = brandRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TRIP HISTORIES HEADER
            Text(
                text = "RECENT COMPLETED TRIPS",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.0.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // TRIPS HISTORY LIST
            if (allTrips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "Empty",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recorded trips yet.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trips_history_list")
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    allTrips.forEach { trip ->
                        HistoryTripRow(
                            trip = trip,
                            currency = tripState.currency,
                            onRowClick = { onNavigateToReceipt(trip.id) },
                            onDelete = { viewModel.deleteTrip(trip.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTripRow(
    trip: TripEntity,
    currency: String,
    onRowClick: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()) }
    val formattedDate = formatter.format(Date(trip.startTime))
    val brandRed = Color(0xFFC62828)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formattedDate,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", trip.distanceKm)} KM",
                        color = Color(0xFF1E1E1E),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${trip.durationSeconds / 60}m ${trip.durationSeconds % 60}s",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$currency${String.format(Locale.US, "%.2f", trip.totalFare)}",
                    color = brandRed,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(end = 6.dp)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete record",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(totalSec: Long): String {
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
}
