package com.sih26168.deadreckoningapp.data


import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Plays back a real, pre-computed trajectory (exported by the fusion/
 * map-matching teammate from their working offline demo) as if it were
 * arriving live. Same PositionProvider contract as the fake route, so
 * the UI needs zero changes to use this instead.
 */
class RecordedRoutePositionProvider(private val context: Context) : PositionProvider {

    private data class RecordedPoint(
        val t: Double,
        val lat: Double,
        val lon: Double,
        val speed: Float,
        val heading: Float,
        val mode: PositionMode
    )

    private val points: List<RecordedPoint> by lazy { loadRoute() }

    private val _currentState = MutableStateFlow(
        VehicleState(latitude = 0.0, longitude = 0.0, mode = PositionMode.SIMULATION)
    )
    override val currentState: StateFlow<VehicleState> = _currentState.asStateFlow()

    private var job: Job? = null

    private fun loadRoute(): List<RecordedPoint> {
        val jsonText = context.assets.open("demo_route.json")
            .bufferedReader()
            .use { it.readText() }
        val arr = JSONArray(jsonText)
        val list = mutableListOf<RecordedPoint>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                RecordedPoint(
                    t = obj.getDouble("t"),
                    lat = obj.getDouble("lat"),
                    lon = obj.getDouble("lon"),
                    speed = obj.optDouble("speed", 0.0).toFloat(),
                    heading = obj.optDouble("heading", 0.0).toFloat(),
                    mode = PositionMode.valueOf(obj.optString("mode", "SIMULATION"))
                )
            )
        }
        return list
    }

    override fun start() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            if (points.isEmpty()) return@launch
            var previousT = points.first().t

            for (point in points) {
                val waitMs = ((point.t - previousT) * 1000).toLong().coerceAtLeast(0)
                delay(waitMs)
                previousT = point.t

                _currentState.value = VehicleState(
                    latitude = point.lat,
                    longitude = point.lon,
                    speedMetersPerSecond = point.speed,
                    headingDegrees = point.heading,
                    mode = point.mode
                )
            }
            start() // loop back to the beginning once it finishes
        }
    }

    override fun stop() {
        job?.cancel()
    }
}