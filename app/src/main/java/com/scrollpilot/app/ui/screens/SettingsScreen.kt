package com.scrollpilot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scrollpilot.app.data.*
import com.scrollpilot.app.service.ScrollController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(padding: PaddingValues = PaddingValues(0.dp)) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    var s     by remember { mutableStateOf(GlobalSettings()) }

    LaunchedEffect(Unit) { s = SettingsDataStore.globalSettings(ctx).first() }

    LazyColumn(
        modifier            = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding      = PaddingValues(vertical = 16.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }

        // ── Scroll engine ──────────────────────────────────────────────────
        item { SectionHeader("Scroll Engine") }
        item {
            SegmentedRow(
                options   = ScrollEngine.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                selected  = s.scrollEngine.ordinal,
                onSelect  = { idx ->
                    val engine = ScrollEngine.entries[idx]
                    s = s.copy(scrollEngine = engine)
                    ScrollController.settings = s
                    scope.launch { SettingsDataStore.setScrollEngine(ctx, engine) }
                },
            )
        }
        item {
            Text(
                when (s.scrollEngine) {
                    ScrollEngine.SWIPE     -> "Repeated swipe gestures. Smooth but capped speed."
                    ScrollEngine.FLING     -> "High-velocity fling + momentum wait. Fastest for big lists."
                    ScrollEngine.HYBRID    -> "Swipe at low speed, fling at high speed. Best default."
                    ScrollEngine.PAGE_JUMP -> "Uses app's own scroll actions. Fastest in supported apps."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Default speed ──────────────────────────────────────────────────
        item { SectionHeader("Default Speed") }
        item {
            LabeledSlider(
                label         = "Speed: ${fmtSpeed(s.defaultSpeed)}",
                value         = (s.defaultSpeed - ScrollController.MIN_SPEED) /
                                (ScrollController.MAX_SPEED - ScrollController.MIN_SPEED),
                onValueChange = { v ->
                    val spd = ScrollController.MIN_SPEED +
                              v * (ScrollController.MAX_SPEED - ScrollController.MIN_SPEED)
                    s = s.copy(defaultSpeed = spd)
                    ScrollController.setSpeed(spd)
                    scope.launch { SettingsDataStore.setDefaultSpeed(ctx, spd) }
                },
            )
        }

        // ── Fling settings (shown when FLING or HYBRID) ────────────────────
        if (s.scrollEngine == ScrollEngine.FLING || s.scrollEngine == ScrollEngine.HYBRID) {
            item { SectionHeader("Fling Settings") }
            item {
                LabeledSlider(
                    label         = "Wait between flings: ${s.flingWaitMs}ms",
                    value         = (s.flingWaitMs - 300f) / (2000f - 300f),
                    onValueChange = { v ->
                        val ms = (300f + v * (2000f - 300f)).toLong()
                        s = s.copy(flingWaitMs = ms)
                        ScrollController.settings = s
                        scope.launch { SettingsDataStore.setFlingWait(ctx, ms) }
                    },
                )
            }
            if (s.scrollEngine == ScrollEngine.HYBRID) {
                item {
                    LabeledSlider(
                        label         = "Switch to fling above: ${fmtSpeed(s.swipeToFlingThreshold)}",
                        value         = (s.swipeToFlingThreshold - 2000f) / (ScrollController.MAX_SPEED - 2000f),
                        onValueChange = { v ->
                            val thr = 2000f + v * (ScrollController.MAX_SPEED - 2000f)
                            s = s.copy(swipeToFlingThreshold = thr)
                            ScrollController.settings = s
                            scope.launch { SettingsDataStore.setFlingThreshold(ctx, thr) }
                        },
                    )
                }
            }
        }

        // ── Overlay size ───────────────────────────────────────────────────
        item { SectionHeader("Overlay Size") }
        item {
            val sizeLabels = OverlaySize.entries.filter { it != OverlaySize.CUSTOM }.map { it.label }
            SegmentedRow(
                options  = sizeLabels,
                selected = OverlaySize.entries.filter { it != OverlaySize.CUSTOM }.indexOf(s.overlaySize)
                               .coerceAtLeast(0),
                onSelect = { idx ->
                    val sz = OverlaySize.entries.filter { it != OverlaySize.CUSTOM }[idx]
                    s = s.copy(overlaySize = sz)
                    scope.launch { SettingsDataStore.setOverlaySize(ctx, sz) }
                },
            )
        }
        item {
            Text("Compact = 75%   Normal = 100%   Large = 125%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── Behavior toggles ───────────────────────────────────────────────
        item { SectionHeader("Behavior") }
        item {
            ToggleRow("Hide overlay when keyboard opens", s.hideOnKeyboard) { v ->
                s = s.copy(hideOnKeyboard = v)
                scope.launch { SettingsDataStore.setHideOnKeyboard(ctx, v) }
            }
        }
        item {
            ToggleRow("Remember overlay position", s.rememberLastPosition) { v ->
                s = s.copy(rememberLastPosition = v)
                scope.launch { SettingsDataStore.setRememberPosition(ctx, v) }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun fmtSpeed(spd: Float) =
    if (spd >= 1000f) "${"%.1f".format(spd / 1000f)}K px/s" else "${spd.toInt()} px/s"

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(top = 4.dp))
}

@Composable
private fun LabeledSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange)
    }
}

@Composable
private fun SegmentedRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(
                selected  = i == selected,
                onClick   = { onSelect(i) },
                label     = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier  = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
