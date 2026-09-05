package com.sih26168.deadreckoningapp.data

import kotlinx.coroutines.flow.StateFlow

interface PositionProvider {
    val currentState: StateFlow<VehicleState>
    fun start()
    fun stop()
}