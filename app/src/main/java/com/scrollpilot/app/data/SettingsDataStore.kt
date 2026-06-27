package com.scrollpilot.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("scroll_pilot_settings")
private val gson = Gson()

object SettingsDataStore {

    private val KEY_SELECTED_APPS     = stringSetPreferencesKey("selected_apps")
    private val KEY_DEFAULT_SPEED     = floatPreferencesKey("default_speed")
    private val KEY_MAX_SPEED         = floatPreferencesKey("max_speed")
    private val KEY_ACCEL_MODE        = stringPreferencesKey("accel_mode")
    private val KEY_CUSTOM_ACCEL      = floatPreferencesKey("custom_accel")
    private val KEY_CUSTOM_DECEL      = floatPreferencesKey("custom_decel")
    private val KEY_HIDE_KEYBOARD     = booleanPreferencesKey("hide_keyboard")
    private val KEY_SHOW_UP           = booleanPreferencesKey("show_up")
    private val KEY_SHOW_DOWN         = booleanPreferencesKey("show_down")
    private val KEY_SHOW_SLIDER       = booleanPreferencesKey("show_slider")
    private val KEY_SHOW_ANIM         = booleanPreferencesKey("show_anim")
    private val KEY_REMEMBER_POS      = booleanPreferencesKey("remember_pos")
    private val KEY_COMPACT           = booleanPreferencesKey("compact")
    private val KEY_INFINITE_ACCEL    = booleanPreferencesKey("infinite_accel")
    private val KEY_OVERLAY_X         = intPreferencesKey("overlay_x")
    private val KEY_OVERLAY_Y         = intPreferencesKey("overlay_y")
    private val KEY_PER_APP_SETTINGS  = stringPreferencesKey("per_app_settings")

    fun globalSettings(ctx: Context): Flow<GlobalSettings> =
        ctx.dataStore.data.catch { emit(emptyPreferences()) }.map { prefs ->
            GlobalSettings(
                selectedApps      = prefs[KEY_SELECTED_APPS] ?: emptySet(),
                defaultSpeed      = prefs[KEY_DEFAULT_SPEED] ?: 800f,
                maxSpeed          = prefs[KEY_MAX_SPEED] ?: 5000f,
                accelerationMode  = runCatching {
                    AccelerationMode.valueOf(prefs[KEY_ACCEL_MODE] ?: "SMOOTH")
                }.getOrDefault(AccelerationMode.SMOOTH),
                customAccel       = prefs[KEY_CUSTOM_ACCEL] ?: 3f,
                customDecel       = prefs[KEY_CUSTOM_DECEL] ?: 3f,
                hideWithKeyboard  = prefs[KEY_HIDE_KEYBOARD] ?: true,
                showUpButton      = prefs[KEY_SHOW_UP] ?: true,
                showDownButton    = prefs[KEY_SHOW_DOWN] ?: true,
                showSpeedSlider   = prefs[KEY_SHOW_SLIDER] ?: true,
                showSpeedAnimation= prefs[KEY_SHOW_ANIM] ?: true,
                rememberLastPosition = prefs[KEY_REMEMBER_POS] ?: true,
                keepCompact       = prefs[KEY_COMPACT] ?: false,
                infiniteAcceleration = prefs[KEY_INFINITE_ACCEL] ?: false,
                overlayX          = prefs[KEY_OVERLAY_X] ?: -1,
                overlayY          = prefs[KEY_OVERLAY_Y] ?: -1,
            )
        }

    suspend fun updateGlobal(ctx: Context, update: suspend (GlobalSettings) -> GlobalSettings) {
        ctx.dataStore.edit { prefs ->
            val current = GlobalSettings(
                selectedApps     = prefs[KEY_SELECTED_APPS] ?: emptySet(),
                defaultSpeed     = prefs[KEY_DEFAULT_SPEED] ?: 800f,
                maxSpeed         = prefs[KEY_MAX_SPEED] ?: 5000f,
                accelerationMode = runCatching { AccelerationMode.valueOf(prefs[KEY_ACCEL_MODE] ?: "SMOOTH") }.getOrDefault(AccelerationMode.SMOOTH),
                customAccel      = prefs[KEY_CUSTOM_ACCEL] ?: 3f,
                customDecel      = prefs[KEY_CUSTOM_DECEL] ?: 3f,
                hideWithKeyboard = prefs[KEY_HIDE_KEYBOARD] ?: true,
                showUpButton     = prefs[KEY_SHOW_UP] ?: true,
                showDownButton   = prefs[KEY_SHOW_DOWN] ?: true,
                showSpeedSlider  = prefs[KEY_SHOW_SLIDER] ?: true,
                showSpeedAnimation = prefs[KEY_SHOW_ANIM] ?: true,
                rememberLastPosition = prefs[KEY_REMEMBER_POS] ?: true,
                keepCompact      = prefs[KEY_COMPACT] ?: false,
                infiniteAcceleration = prefs[KEY_INFINITE_ACCEL] ?: false,
                overlayX         = prefs[KEY_OVERLAY_X] ?: -1,
                overlayY         = prefs[KEY_OVERLAY_Y] ?: -1,
            )
            val updated = kotlinx.coroutines.runBlocking { update(current) }
            prefs[KEY_SELECTED_APPS]  = updated.selectedApps
            prefs[KEY_DEFAULT_SPEED]  = updated.defaultSpeed
            prefs[KEY_MAX_SPEED]      = updated.maxSpeed
            prefs[KEY_ACCEL_MODE]     = updated.accelerationMode.name
            prefs[KEY_CUSTOM_ACCEL]   = updated.customAccel
            prefs[KEY_CUSTOM_DECEL]   = updated.customDecel
            prefs[KEY_HIDE_KEYBOARD]  = updated.hideWithKeyboard
            prefs[KEY_SHOW_UP]        = updated.showUpButton
            prefs[KEY_SHOW_DOWN]      = updated.showDownButton
            prefs[KEY_SHOW_SLIDER]    = updated.showSpeedSlider
            prefs[KEY_SHOW_ANIM]      = updated.showSpeedAnimation
            prefs[KEY_REMEMBER_POS]   = updated.rememberLastPosition
            prefs[KEY_COMPACT]        = updated.keepCompact
            prefs[KEY_INFINITE_ACCEL] = updated.infiniteAcceleration
            prefs[KEY_OVERLAY_X]      = updated.overlayX
            prefs[KEY_OVERLAY_Y]      = updated.overlayY
        }
    }

    suspend fun setSelectedApps(ctx: Context, apps: Set<String>) {
        ctx.dataStore.edit { it[KEY_SELECTED_APPS] = apps }
    }

    suspend fun setOverlayPosition(ctx: Context, x: Int, y: Int) {
        ctx.dataStore.edit { prefs ->
            prefs[KEY_OVERLAY_X] = x
            prefs[KEY_OVERLAY_Y] = y
        }
    }

    fun perAppSettings(ctx: Context): Flow<Map<String, PerAppSettings>> =
        ctx.dataStore.data.catch { emit(emptyPreferences()) }.map { prefs ->
            val json = prefs[KEY_PER_APP_SETTINGS] ?: return@map emptyMap()
            runCatching {
                val type = object : TypeToken<Map<String, PerAppSettings>>() {}.type
                gson.fromJson<Map<String, PerAppSettings>>(json, type) ?: emptyMap()
            }.getOrDefault(emptyMap())
        }

    suspend fun setPerAppSettings(ctx: Context, map: Map<String, PerAppSettings>) {
        ctx.dataStore.edit { it[KEY_PER_APP_SETTINGS] = gson.toJson(map) }
    }
}
