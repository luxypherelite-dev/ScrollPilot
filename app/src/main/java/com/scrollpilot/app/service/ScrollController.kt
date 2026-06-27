package com.scrollpilot.app.service

import com.scrollpilot.app.data.AccelerationMode
import com.scrollpilot.app.data.ScrollDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScrollState(
    val direction: ScrollDirection = ScrollDirection.PAUSED,
    val targetSpeed: Float = 800f,
    val currentSpeed: Float = 0f,
    val maxSpeed: Float = 5000f,
    val accelerationMode: AccelerationMode = AccelerationMode.SMOOTH,
    val customAccel: Float = 3f,
    val customDecel: Float = 3f,
    val infiniteAcceleration: Boolean = false,
)

object ScrollController {

    private val _state = MutableStateFlow(ScrollState())
    val state: StateFlow<ScrollState> = _state.asStateFlow()

    fun toggleDirection(requested: ScrollDirection) {
        val current = _state.value
        val newDir = if (current.direction == requested) ScrollDirection.PAUSED else requested
        _state.value = current.copy(
            direction    = newDir,
            currentSpeed = if (newDir == ScrollDirection.PAUSED) 0f else current.currentSpeed,
        )
    }

    fun setTargetSpeed(speed: Float) {
        _state.value = _state.value.copy(targetSpeed = speed.coerceIn(50f, _state.value.maxSpeed))
    }

    fun setMaxSpeed(max: Float) {
        _state.value = _state.value.copy(
            maxSpeed    = max,
            targetSpeed = _state.value.targetSpeed.coerceAtMost(max),
        )
    }

    fun setAccelerationMode(mode: AccelerationMode) {
        _state.value = _state.value.copy(accelerationMode = mode)
    }

    fun setCustomAcceleration(accel: Float, decel: Float) {
        _state.value = _state.value.copy(customAccel = accel, customDecel = decel)
    }

    fun setInfiniteAcceleration(enabled: Boolean) {
        _state.value = _state.value.copy(infiniteAcceleration = enabled)
    }

    fun pause() {
        _state.value = _state.value.copy(direction = ScrollDirection.PAUSED, currentSpeed = 0f)
    }

    fun applySettings(
        targetSpeed: Float,
        maxSpeed: Float,
        mode: AccelerationMode,
        customAccel: Float,
        customDecel: Float,
        infinite: Boolean,
    ) {
        _state.value = _state.value.copy(
            targetSpeed          = targetSpeed.coerceIn(50f, maxSpeed),
            maxSpeed             = maxSpeed,
            accelerationMode     = mode,
            customAccel          = customAccel,
            customDecel          = customDecel,
            infiniteAcceleration = infinite,
        )
    }

    fun tick(deltaMs: Long): Float {
        val s = _state.value
        if (s.direction == ScrollDirection.PAUSED) {
            _state.value = s.copy(currentSpeed = 0f)
            return 0f
        }
        val target = if (s.infiniteAcceleration) s.maxSpeed else s.targetSpeed
        val newSpeed = when (s.accelerationMode) {
            AccelerationMode.INSTANT    -> target
            AccelerationMode.SMOOTH     -> lerp(s.currentSpeed, target, 0.04f * (deltaMs / 16f))
            AccelerationMode.ADAPTIVE   -> {
                val diff = target - s.currentSpeed
                val factor = if (diff > 0) 0.08f else 0.05f
                lerp(s.currentSpeed, target, factor * (deltaMs / 16f))
            }
            AccelerationMode.AGGRESSIVE -> lerp(s.currentSpeed, target, 0.18f * (deltaMs / 16f))
            AccelerationMode.CUSTOM     -> {
                val diff = target - s.currentSpeed
                val factor = (if (diff > 0) s.customAccel else s.customDecel) / 100f
                lerp(s.currentSpeed, target, factor * (deltaMs / 16f))
            }
        }.coerceIn(0f, s.maxSpeed)
        _state.value = s.copy(currentSpeed = newSpeed)
        return newSpeed
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    fun reset() {
        _state.value = ScrollState()
    }
}
