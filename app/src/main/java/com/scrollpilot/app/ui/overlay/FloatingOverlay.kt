package com.scrollpilot.app.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.scrollpilot.app.data.*
import com.scrollpilot.app.service.ScrollController
import com.scrollpilot.app.service.ScrollState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Colors ────────────────────────────────────────────────────────────────────
private val ColorUp    = Color(0xFF4CAF50)
private val ColorDown  = Color(0xFF2196F3)
private val ColorIdle  = Color(0xFF555555)
private val BgColor    = Color(0xEE1A1A2E)
private val SurfaceCol = Color(0xFF16213E)

// ── Top-level composable ──────────────────────────────────────────────────────
@Composable
fun FloatingOverlay(settings: GlobalSettings) {
    Box(Modifier.scale(settings.effectiveScale)) {
        OverlayCard(settings = settings)
    }
}

@Composable
private fun OverlayCard(settings: GlobalSettings) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by ScrollController.state.collectAsState()
    val speed by ScrollController.speed.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue   = 0.88f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(tween(650, easing = EaseInOutSine), RepeatMode.Reverse),
        label          = "pulse",
    )
    val activeScale = if (state != ScrollState.IDLE) pulse else 1f

    Card(
        modifier  = Modifier.width(210.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = BgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier              = Modifier.padding(10.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(6.dp),
        ) {
            // ── Header ───────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "ScrollPilot",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
                // Long-press the dot = emergency stop
                val dotColor by animateColorAsState(
                    targetValue = when (state) {
                        ScrollState.IDLE           -> ColorIdle
                        ScrollState.SCROLLING_UP   -> ColorUp
                        ScrollState.SCROLLING_DOWN -> ColorDown
                    }, label = "dot",
                )
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { ScrollController.stop() })
                        },
                )
                Text(
                    when (state) {
                        ScrollState.IDLE           -> "IDLE"
                        ScrollState.SCROLLING_UP   -> "↑ UP"
                        ScrollState.SCROLLING_DOWN -> "↓ DOWN"
                    },
                    fontSize   = 10.sp,
                    color      = when (state) {
                        ScrollState.IDLE           -> Color.Gray
                        ScrollState.SCROLLING_UP   -> ColorUp
                        ScrollState.SCROLLING_DOWN -> ColorDown
                    },
                    fontWeight = FontWeight.Bold,
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // ── UP direction button ───────────────────────────────
            DirectionButton(
                label   = "▲  UP",
                active  = state == ScrollState.SCROLLING_UP,
                color   = ColorUp,
                pulse   = activeScale,
                onClick = { ScrollController.pressUp() },
            )

            // ── Speed controls ────────────────────────────────────
            SpeedRow(speed = speed, scope = scope)

            // ── DOWN direction button ─────────────────────────────
            DirectionButton(
                label   = "▼  DOWN",
                active  = state == ScrollState.SCROLLING_DOWN,
                color   = ColorDown,
                pulse   = activeScale,
                onClick = { ScrollController.pressDown() },
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // ── Engine + Size pickers ─────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Cycle scroll engine
                CycleButton(
                    label   = settings.scrollEngine.name.lowercase().replaceFirstChar { it.uppercase() },
                    prefix  = "⚙",
                    onClick = {
                        val next = ScrollEngine.entries.let { it[(it.indexOf(settings.scrollEngine) + 1) % it.size] }
                        scope.launch { SettingsDataStore.setScrollEngine(ctx, next) }
                    },
                )
                // Cycle overlay size
                val sizeOptions = listOf(OverlaySize.COMPACT, OverlaySize.NORMAL, OverlaySize.LARGE)
                CycleButton(
                    label   = settings.overlaySize.label,
                    prefix  = "⊞",
                    onClick = {
                        val cur = sizeOptions.indexOf(settings.overlaySize).coerceAtLeast(0)
                        val next = sizeOptions[(cur + 1) % sizeOptions.size]
                        scope.launch { SettingsDataStore.setOverlaySize(ctx, next) }
                    },
                )
            }
        }
    }
}

// ── Direction button (toggles on/off, shows ■ STOP when active) ───────────────
@Composable
private fun DirectionButton(
    label:   String,
    active:  Boolean,
    color:   Color,
    pulse:   Float,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        if (active) color.copy(alpha = 0.25f) else SurfaceCol,
        tween(200), label = "bg",
    )
    val borderColor by animateColorAsState(
        if (active) color else Color.White.copy(alpha = 0.12f),
        tween(200), label = "border",
    )
    Button(
        onClick        = onClick,
        modifier       = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .scale(if (active) pulse else 1f),
        shape          = RoundedCornerShape(10.dp),
        colors         = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor   = if (active) color else Color.White.copy(alpha = 0.7f),
        ),
        border         = androidx.compose.foundation.BorderStroke(
            if (active) 1.5.dp else 1.dp, borderColor,
        ),
        elevation      = ButtonDefaults.buttonElevation(0.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            // Replace arrow with ■ when active (press again to stop)
            text       = if (active) label.replace("▲", "■").replace("▼", "■") else label,
            fontSize   = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.5.sp,
        )
    }
}

// ── Speed row: slider + hold-repeat ± buttons ─────────────────────────────────
@Composable
private fun SpeedRow(speed: Float, scope: kotlinx.coroutines.CoroutineScope) {
    val pct = ((speed - ScrollController.MIN_SPEED) /
               (ScrollController.MAX_SPEED - ScrollController.MIN_SPEED)).coerceIn(0f, 1f)
    val label = if (speed >= 1000f) "${"%.1f".format(speed / 1000f)}K" else "${speed.toInt()}"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$label px/s", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HoldRepeatButton(Modifier.size(32.dp), scope, { ScrollController.speedDown() }) {
                SpeedBtn("−")
            }
            Slider(
                value         = pct,
                onValueChange = { v ->
                    ScrollController.setSpeed(
                        ScrollController.MIN_SPEED +
                        v * (ScrollController.MAX_SPEED - ScrollController.MIN_SPEED)
                    )
                },
                modifier = Modifier.weight(1f).height(24.dp),
                colors   = SliderDefaults.colors(
                    thumbColor         = Color.White,
                    activeTrackColor   = Color(0xFF7C83FD),
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
            )
            HoldRepeatButton(Modifier.size(32.dp), scope, { ScrollController.speedUp() }) {
                SpeedBtn("+")
            }
        }
    }
}

@Composable
private fun SpeedBtn(symbol: String) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(SurfaceCol),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Light)
    }
}

// ── Hold-to-repeat wrapper ────────────────────────────────────────────────────
@Composable
private fun HoldRepeatButton(
    modifier: Modifier,
    scope:    kotlinx.coroutines.CoroutineScope,
    onClick:  () -> Unit,
    content:  @Composable () -> Unit,
) {
    Box(
        modifier         = modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                onClick()
                val job = scope.launch {
                    delay(350L)
                    while (true) { onClick(); delay(100L) }
                }
                tryAwaitRelease()
                job.cancel()
            })
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ── Small cycle-through button ────────────────────────────────────────────────
@Composable
private fun CycleButton(label: String, prefix: String, onClick: () -> Unit) {
    TextButton(
        onClick        = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        colors         = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.45f)),
    ) {
        Text("$prefix $label", fontSize = 10.sp)
    }
}
