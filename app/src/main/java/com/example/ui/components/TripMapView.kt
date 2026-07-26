package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.data.model.TripState
import com.example.data.model.TripStatus
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.Locale

enum class MapTypeMode {
    OSM_FREE_STREET,
    OSM_FREE_DARK,
    VECTOR_RADAR,
    GOOGLE_MAPS
}

@Composable
fun TripMapView(
    tripState: TripState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Check if MAPS_API_KEY is configured with a real key
    val mapsApiKey = try { BuildConfig.MAPS_API_KEY } catch (e: Exception) { "" }
    val isGoogleKeyConfigured = mapsApiKey.isNotBlank() &&
            !mapsApiKey.contains("DEFAULT_MAPS_KEY", ignoreCase = true) &&
            !mapsApiKey.contains("MY_GEMINI_API_KEY", ignoreCase = true) &&
            !mapsApiKey.contains("AIzaSyB7j5s8T369OKe4H69e3jhkGfM2sJhniCo", ignoreCase = true)

    // Default map mode is 100% Free OpenStreetMap
    var selectedMode by remember { mutableStateOf(MapTypeMode.OSM_FREE_STREET) }

    // LatLng for Google Maps if used
    val currentLatLng = if (tripState.latitude != null && tripState.longitude != null) {
        LatLng(tripState.latitude, tripState.longitude)
    } else {
        LatLng(11.0168, 76.9558)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLatLng, 16f)
    }

    LaunchedEffect(currentLatLng) {
        if (tripState.latitude != null && tripState.longitude != null && selectedMode == MapTypeMode.GOOGLE_MAPS) {
            try {
                cameraPositionState.animate(CameraUpdateFactory.newLatLng(currentLatLng))
            } catch (_: Exception) {}
        }
    }

    val latLngRoute = remember(tripState.routePoints) {
        tripState.routePoints.map { LatLng(it.first, it.second) }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedMode) {
                MapTypeMode.OSM_FREE_STREET -> {
                    OpenStreetMapTileView(
                        tripState = tripState,
                        useDarkStyle = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MapTypeMode.OSM_FREE_DARK -> {
                    OpenStreetMapTileView(
                        tripState = tripState,
                        useDarkStyle = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MapTypeMode.VECTOR_RADAR -> {
                    GpsVectorRouteCanvas(
                        tripState = tripState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MapTypeMode.GOOGLE_MAPS -> {
                    if (isGoogleKeyConfigured) {
                        GoogleMap(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("google_trip_map"),
                            cameraPositionState = cameraPositionState
                        ) {
                            if (latLngRoute.size >= 2) {
                                Polyline(
                                    points = latLngRoute,
                                    color = Color(0xFFE53935),
                                    width = 12f
                                )
                            }
                            if (tripState.latitude != null && tripState.longitude != null) {
                                Marker(
                                    state = rememberMarkerState(position = currentLatLng),
                                    title = "Taxi Position",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                                )
                            }
                        }
                    } else {
                        OpenStreetMapTileView(
                            tripState = tripState,
                            useDarkStyle = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun OpenStreetMapTileView(
    tripState: TripState,
    useDarkStyle: Boolean,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lat = tripState.latitude ?: 11.0168
    val lon = tripState.longitude ?: 76.9558
    val routePoints = tripState.routePoints

    LaunchedEffect(Unit) {
        org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName
    }

    AndroidView(
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            org.osmdroid.views.MapView(ctx).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                controller.setZoom(17.0)
                controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            if (useDarkStyle) {
                mapView.overlayManager.tilesOverlay.setColorFilter(
                    android.graphics.ColorMatrixColorFilter(
                        floatArrayOf(
                            -0.8f, 0f, 0f, 0f, 240f,
                            0f, -0.8f, 0f, 0f, 240f,
                            0f, 0f, -0.8f, 0f, 240f,
                            0f, 0f, 0f, 1.0f, 0f
                        )
                    )
                )
            } else {
                mapView.overlayManager.tilesOverlay.setColorFilter(null)
            }

            val geoPoint = org.osmdroid.util.GeoPoint(lat, lon)
            mapView.controller.animateTo(geoPoint)

            // Draw route polyline
            if (routePoints.size >= 2) {
                val polyline = org.osmdroid.views.overlay.Polyline(mapView).apply {
                    outlinePaint.color = android.graphics.Color.parseColor("#DC2626")
                    outlinePaint.strokeWidth = 14f
                    setPoints(routePoints.map { org.osmdroid.util.GeoPoint(it.first, it.second) })
                }
                mapView.overlays.add(polyline)

                // Start marker
                val startPoint = org.osmdroid.util.GeoPoint(routePoints.first().first, routePoints.first().second)
                val startMarker = org.osmdroid.views.overlay.Marker(mapView).apply {
                    position = startPoint
                    setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                    icon = androidx.core.content.ContextCompat.getDrawable(mapView.context, com.example.R.drawable.ic_start_flag)
                    title = "Trip Start"
                }
                mapView.overlays.add(startMarker)
            }

            // Taxi Car marker at current location
            val taxiMarker = org.osmdroid.views.overlay.Marker(mapView).apply {
                position = geoPoint
                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                icon = androidx.core.content.ContextCompat.getDrawable(mapView.context, com.example.R.drawable.ic_taxi_marker)
                title = "Taxi Location"
            }
            mapView.overlays.add(taxiMarker)

            mapView.invalidate()
        },
        modifier = modifier
    )
}

@Composable
private fun GpsVectorRouteCanvas(
    tripState: TripState,
    modifier: Modifier = Modifier
) {
    val route = tripState.routePoints

    Canvas(modifier = modifier.background(Color(0xFF0F172A))) {
        val width = size.width
        val height = size.height

        val gridStep = 40.dp.toPx()
        var x = 0f
        while (x < width) {
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += gridStep
        }
        var y = 0f
        while (y < height) {
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += gridStep
        }

        if (route.isEmpty()) {
            val centerX = width / 2f
            val centerY = height / 2f
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                radius = 36.dp.toPx(),
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 8.dp.toPx(),
                center = Offset(centerX, centerY)
            )
            return@Canvas
        }

        val lats = route.map { it.first }
        val lons = route.map { it.second }
        val minLat = lats.minOrNull() ?: 0.0
        val maxLat = lats.maxOrNull() ?: 0.0
        val minLon = lons.minOrNull() ?: 0.0
        val maxLon = lons.maxOrNull() ?: 0.0

        val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
        val lonRange = (maxLon - minLon).coerceAtLeast(0.0001)

        val padding = 48.dp.toPx()
        val usableW = width - (padding * 2)
        val usableH = height - (padding * 2)

        fun mapToScreen(lat: Double, lon: Double): Offset {
            val normX = ((lon - minLon) / lonRange).toFloat()
            val normY = (1.0f - ((lat - minLat) / latRange)).toFloat()
            return Offset(
                x = padding + (normX * usableW),
                y = padding + (normY * usableH)
            )
        }

        val path = Path()
        val firstPoint = mapToScreen(route.first().first, route.first().second)
        path.moveTo(firstPoint.x, firstPoint.y)

        for (i in 1 until route.size) {
            val pt = mapToScreen(route[i].first, route[i].second)
            path.lineTo(pt.x, pt.y)
        }

        drawPath(
            path = path,
            color = Color(0xFFEF4444),
            style = Stroke(
                width = 8f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val startScreen = mapToScreen(route.first().first, route.first().second)
        drawCircle(
            color = Color(0xFF22C55E),
            radius = 10.dp.toPx(),
            center = startScreen
        )
        drawCircle(
            color = Color.White,
            radius = 4.dp.toPx(),
            center = startScreen
        )

        val currentScreen = mapToScreen(route.last().first, route.last().second)
        drawCircle(
            color = Color(0xFFEF4444).copy(alpha = 0.3f),
            radius = 18.dp.toPx(),
            center = currentScreen
        )
        drawCircle(
            color = Color(0xFFEF4444),
            radius = 10.dp.toPx(),
            center = currentScreen
        )
        drawCircle(
            color = Color.White,
            radius = 4.dp.toPx(),
            center = currentScreen
        )
    }
}
