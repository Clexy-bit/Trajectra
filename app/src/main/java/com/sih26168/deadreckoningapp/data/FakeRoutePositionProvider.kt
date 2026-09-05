package com.sih26168.deadreckoningapp.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2

class FakeRoutePositionProvider : PositionProvider {

    private val route = listOf(
        28.4595 to 77.0266,
        28.4600 to 77.0280,
        28.4610 to 77.0295,
        28.4625 to 77.0300,
        28.4640 to 77.0290,
        28.4650 to 77.0270
    )

    private val _currentState = MutableStateFlow(
        VehicleState(
            latitude = route.first().first,
            longitude = route.first().second,
            mode = PositionMode.SIMULATION
        )
    )
    override val currentState: StateFlow<VehicleState> = _currentState.asStateFlow()

    private var job: Job? = null

    override fun start() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            var index = 0
            while (true) {
                val current = route[index]
                val next = route[(index + 1) % route.size]
                val steps = 40
                for (step in 0 until steps) {
                    val t = step / steps.toFloat()
                    val lat = current.first + (next.first - current.first) * t
                    val lng = current.second + (next.second - current.second) * t
                    val heading = bearingBetween(current.first, current.second, next.first, next.second)

                    _currentState.value = VehicleState(
                        latitude = lat,
                        longitude = lng,
                        speedMetersPerSecond = 8f,
                        headingDegrees = heading,
                        mode = PositionMode.SIMULATION
                    )
                    delay(100)
                }
                index = (index + 1) % route.size
            }
        }
    }

    override fun stop() {
        job?.cancel()
    }

    private fun bearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2))
        val x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }
}