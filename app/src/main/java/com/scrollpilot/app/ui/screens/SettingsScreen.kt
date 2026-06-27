package com.scrollpilot.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scrollpilot.app.data.AccelerationMode
import com.scrollpilot.app.data.GlobalSettings
import com.scrollpilot.app.data.SettingsDataStore
import com.scrollpilot.app.service.ScrollController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(padding: PaddingValues = PaddingValues(0.dp)) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(GlobalSettings()) }

    LaunchedEffect(Unit) {
        settings = SettingsDataStore.globalSettings(ctx).first()
    }

    fun save(update: GlobalSettings.() -> GlobalSettings) {
        settings = settings.update()
        val s = settings
        scope.launch {
            SettingsDataStore.updateGlobal(ctx) { s }
            ScrollController.applySettings(
                targetSpeed = s.defaultSpeed,
                maxSpeed    = s.maxSpeed,
                mode        = s.accelerationMode,
                customAccel = s.customAccel,
                customDecel = s.customDecel,
                infinite    = s.infiniteAcceleration,
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
        }

        // ── Scroll behavior ────────────────────────────────────────────────
        item { SectionHeader("Scroll Behavior") }

        item {
            LabeledSlider(
                label = "Default speed: ${settings.defaultSpeed.toInt()} px/s",
                value = settings.defaultSpeed / settings.maxSpeed,
                onValueChange = { save { copy(defaultSpeed = (it * maxSpeed).coerceIn(50f, maxSpeed)) } }
            )
        }

        item {
            var maxText by remember(settings.maxSpeed) { mutableStateOf(settings.maxSpeed.toInt().toString()) }
            OutlinedTextField(
                value         = maxText,
                onValueChange = {
                    maxText = it
                    it.toFloatOrNull()?.let { v -> save { copy(maxSpeed = v.coerceIn(200f, 20000f)) } }
                },
                label     = { Text("Max speed (px/s)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier  = Modifier.fillMaxWidth()
            )
        }

        // ── Acceleration mode ──────────────────────────────────────────────
        item { SectionHeader("Acceleration Mode") }

        item {
            AccelerationMode.values().forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium)
                        Text(modeDescription(mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(
                        selected = settings.accelerationMode == mode,
                        onClick  = { save { copy(accelerationMode = mode) } }
                    )
                }
            }
        }

        // Custom accel controls
        if (settings.accelerationMode == AccelerationMode.CUSTOM) {
            item {
                LabeledSlider(
                    label = "Acceleration: ${settings.customAccel}x",
                    value = settings.customAccel / 20f,
                    onValueChange = { save { copy(customAccel = (it * 20f).coerceIn(0.1f, 20f)) } }
                )
                LabeledSlider(
                    label = "Deceleration: ${settings.customDecel}x",
                    value = settings.customDecel / 20f,
                    onValueChange = { save { copy(customDecel = (it * 20f).coerceIn(0.1f, 20f)) } }
                )
            }
        }

        // ── Keyboard behavior ──────────────────────────────────────────────
        item { SectionHeader("Keyboard Behavior") }

        item {
            ToggleRow("Hide overlay when keyboard opens", settings.hideWithKeyboard) {
                save { copy(hideWithKeyboard = it) }
            }
        }

        // ── Overlay appearance ─────────────────────────────────────────────
        item { SectionHeader("Overlay Appearance") }

        item {
            ToggleRow("Show Up button",      settings.showUpButton)      { save { copy(showUpButton = it) } }
            ToggleRow("Show Down button",    settings.showDownButton)    { save { copy(showDownButton = it) } }
            ToggleRow("Show speed slider",   settings.showSpeedSlider)   { save { copy(showSpeedSlider = it) } }
            ToggleRow("Show speed animation",settings.showSpeedAnimation){ save { copy(showSpeedAnimation = it) } }
            ToggleRow("Remember last position", settings.rememberLastPosition) { save { copy(rememberLastPosition = it) } }
            ToggleRow("Compact overlay",     settings.keepCompact)       { save { copy(keepCompact = it) } }
        }

        // ── Advanced ───────────────────────────────────────────────────────
        item { SectionHeader("Advanced") }

        item {
            ToggleRow(
                label   = "Infinite acceleration (always full speed)",
                checked = settings.infiniteAcceleration,
                onChange = { save { copy(infiniteAcceleration = it) } }
            )
            if (settings.infiniteAcceleration) {
                Text(
                    "⚠ With this on, tapping a direction immediately scrolls at max speed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
    HorizontalDivider()
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun modeDescription(mode: AccelerationMode) = when (mode) {
    AccelerationMode.INSTANT    -> "Jumps to target speed immediately"
    AccelerationMode.SMOOTH     -> "Eases into speed gradually (default)"
    AccelerationMode.ADAPTIVE   -> "Fast start, slows near target"
    AccelerationMode.AGGRESSIVE -> "Reaches target speed very quickly"
    AccelerationMode.CUSTOM     -> "Define your own acceleration values"
}
