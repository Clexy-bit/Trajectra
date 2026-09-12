package com.sih26168.deadreckoningapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sih26168.deadreckoningapp.data.FakeRoutePositionProvider
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.sih26168.deadreckoningapp.data.RecordedRoutePositionProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val locationPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // We don't need to do anything special here for now —
        // the sensor code below just checks permission again when it runs.
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionRequest.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DeadReckoningMapScreen()
                }
            }
        }
    }
}

@Composable
fun DeadReckoningMapScreen() {
    val context = LocalContext.current
    var liveAccel by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var liveGyro by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var liveGpsText by remember { mutableStateOf("Waiting for GPS...") }
    var simulateDropout by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val accelSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val gyroSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)

        val sensorListener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                when (event.sensor.type) {
                    android.hardware.Sensor.TYPE_ACCELEROMETER ->
                        liveAccel = Triple(event.values[0], event.values[1], event.values[2])
                    android.hardware.Sensor.TYPE_GYROSCOPE ->
                        liveGyro = Triple(event.values[0], event.values[1], event.values[2])
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(sensorListener, accelSensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorListener, gyroSensor, android.hardware.SensorManager.SENSOR_DELAY_UI)

        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val locationListener = android.location.LocationListener { location ->
            liveGpsText = "Live GPS: %.5f, %.5f".format(location.latitude, location.longitude)
        }

        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                locationManager.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener
                )
            }
        } catch (e: SecurityException) {
            liveGpsText = "GPS permission not granted"
        }

        onDispose {
            sensorManager.unregisterListener(sensorListener)
            locationManager.removeUpdates(locationListener)
        }
    }
    val positionProvider = remember { RecordedRoutePositionProvider(context) }

    LaunchedEffect(Unit) {
        positionProvider.start()
    }

    val vehicleState by positionProvider.currentState.collectAsState()
    val displayState = if (simulateDropout) {
        vehicleState.copy(mode = com.sih26168.deadreckoningapp.data.PositionMode.DEAD_RECKONING)
    } else {
        vehicleState
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(vehicleState.latitude, vehicleState.longitude))
        }
    }

    val vehicleMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            mapView.overlays.add(this)
        }
    }

    LaunchedEffect(vehicleState) {
        val point = GeoPoint(vehicleState.latitude, vehicleState.longitude)
        vehicleMarker.position = point
        vehicleMarker.title = "Vehicle (${vehicleState.mode})"
        mapView.controller.animateTo(point)
        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            color = Color.White.copy(alpha = 0.85f)
        ) {
            Text(
                text = "Mode: ${displayState.mode}   Speed: ${"%.1f".format(displayState.speedMetersPerSecond)} m/s",
                modifier = Modifier.padding(8.dp)
            )
        }

        androidx.compose.material3.Button(
            onClick = { simulateDropout = !simulateDropout },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Text(if (simulateDropout) "Restore GPS" else "Simulate Tunnel / Kill GPS")
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            color = Color.White.copy(alpha = 0.85f)
        ) {
            Text(
                text = "$liveGpsText\nAccel: %.2f, %.2f, %.2f\nGyro: %.2f, %.2f, %.2f".format(
                    liveAccel.first, liveAccel.second, liveAccel.third,
                    liveGyro.first, liveGyro.second, liveGyro.third
                ),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}