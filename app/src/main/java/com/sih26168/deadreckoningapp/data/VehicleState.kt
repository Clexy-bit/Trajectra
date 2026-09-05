package com.sih26168.deadreckoningapp.data

data class VehicleState(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float = 0f,
    val headingDegrees: Float = 0f,
    val mode: PositionMode = PositionMode.SIMULATION,
    val accuracyMeters: Float? = null
)

enum class PositionMode {
    GNSS_ACTIVE,
    DEAD_RECKONING,
    FUSED,
    SIMULATION
}