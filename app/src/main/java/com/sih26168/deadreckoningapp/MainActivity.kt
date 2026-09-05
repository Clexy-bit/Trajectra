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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val positionProvider = remember { FakeRoutePositionProvider() }

    LaunchedEffect(Unit) {
        positionProvider.start()
    }

    val vehicleState by positionProvider.currentState.collectAsState()

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
                text = "Mode: ${vehicleState.mode}   Speed: ${"%.1f".format(vehicleState.speedMetersPerSecond)} m/s",
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}