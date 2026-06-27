package com.scrollpilot.app.ui.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollpilot.app.data.GlobalSettings
import com.scrollpilot.app.data.ScrollDirection
import com.scrollpilot.app.service.ScrollController

@Composable
fun FloatingOverlay(
    settings: GlobalSettings,
    onDragDelta: (Float, Float) -> Unit,
) {
    val scrollState by ScrollController.state.collectAsState()

    Surface(
        shape     = RoundedCornerShape(20.dp),
        color     = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 12.dp,
        modifier  = Modifier.width(if (settings.keepCompact) 68.dp else 80.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {

            // ── Up button ─────────────────────────────────────────────────
            if (settings.showUpButton) {
                val upActive = scrollState.direction == ScrollDirection.UP
                FilledIconButton(
                    onClick  = { ScrollController.toggleDirection(ScrollDirection.UP) },
                    modifier = Modifier.size(56.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (upActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll up",
                        modifier = Modifier.size(32.dp),
                        tint = if (upActive) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Speed slider + indicator ───────────────────────────────────
            if (settings.showSpeedSlider) {
                val sliderValue = (scrollState.targetSpeed / settings.maxSpeed).coerceIn(0f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Vertical slider (rotated via custom drag)
                    VerticalSpeedSlider(
                        value    = sliderValue,
                        onChange = { v -> ScrollController.setTargetSpeed(v * settings.maxSpeed) },
                        modifier = Modifier.height(100.dp)
                    )

                    if (settings.showSpeedAnimation) {
                        SpeedIndicator(currentSpeed = scrollState.currentSpeed, maxSpeed = settings.maxSpeed)
                    }
                }
            }

            // ── Down button ───────────────────────────────────────────────
            if (settings.showDownButton) {
                val downActive = scrollState.direction == ScrollDirection.DOWN
                FilledIconButton(
                    onClick  = { ScrollController.toggleDirection(ScrollDirection.DOWN) },
                    modifier = Modifier.size(56.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (downActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll down",
                        modifier = Modifier.size(32.dp),
                        tint = if (downActive) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalSpeedSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragTotal by remember { mutableFloatStateOf(0f) }
    var baseValue by remember { mutableFloatStateOf(value) }

    Box(
        modifier = modifier
            .width(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { baseValue = value; dragTotal = 0f },
                    onVerticalDrag = { _, dy ->
                        dragTotal += dy
                        // drag up (negative dy) → increase speed
                        val newVal = (baseValue - dragTotal / size.height).coerceIn(0f, 1f)
                        onChange(newVal)
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0.02f, 1f))
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        )
    }
}

@Composable
private fun SpeedIndicator(currentSpeed: Float, maxSpeed: Float) {
    val ratio = (currentSpeed / maxSpeed).coerceIn(0f, 1f)
    val color = when {
        ratio < 0.33f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ratio < 0.66f -> MaterialTheme.colorScheme.primary
        else          -> Color(0xFFFF6D00)
    }

    // Pulsing dot when scrolling
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue  = if (currentSpeed > 0f) 1f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text  = "${currentSpeed.toInt()}",
            fontSize = 9.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
