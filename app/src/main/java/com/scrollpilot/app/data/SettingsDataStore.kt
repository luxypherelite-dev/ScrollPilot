package com.scrollpilot.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.ds: DataStore<Preferences> by preferencesDataStore("scrollpilot_prefs")

object SettingsDataStore {

    private val KEY_APPS          = stringSetPreferencesKey("enabled_apps")
    private val KEY_OX            = intPreferencesKey("overlay_x")
    private val KEY_OY            = intPreferencesKey("overlay_y")
    private val KEY_REMEMBER_POS  = booleanPreferencesKey("remember_position")
    private val KEY_HIDE_KB       = booleanPreferencesKey("hide_keyboard")
    private val KEY_OVERLAY_SIZE  = stringPreferencesKey("overlay_size")
    private val KEY_CUSTOM_SCALE  = floatPreferencesKey("custom_scale")
    private val KEY_ENGINE        = stringPreferencesKey("scroll_engine")
    private val KEY_FLING_WAIT    = longPreferencesKey("fling_wait_ms")
    private val KEY_FLING_THRESH  = floatPreferencesKey("fling_threshold")
    private val KEY_DEF_SPEED     = floatPreferencesKey("default_speed")

    fun observe(ctx: Context): Flow<GlobalSettings> =
        ctx.ds.data.catch { emit(emptyPreferences()) }.map { p ->
            GlobalSettings(
                enabledApps             = p[KEY_APPS] ?: emptySet(),
                overlayX                = p[KEY_OX]  ?: -1,
                overlayY                = p[KEY_OY]  ?: -1,
                rememberLastPosition    = p[KEY_REMEMBER_POS]  ?: true,
                hideOnKeyboard          = p[KEY_HIDE_KB]       ?: true,
                overlaySize             = runCatching { OverlaySize.valueOf(p[KEY_OVERLAY_SIZE] ?: "") }.getOrDefault(OverlaySize.COMPACT),
                customScale             = p[KEY_CUSTOM_SCALE]  ?: 0.75f,
                scrollEngine            = runCatching { ScrollEngine.valueOf(p[KEY_ENGINE] ?: "") }.getOrDefault(ScrollEngine.HYBRID),
                flingWaitMs             = p[KEY_FLING_WAIT]    ?: 900L,
                swipeToFlingThreshold   = p[KEY_FLING_THRESH]  ?: 8000f,
                defaultSpeed            = p[KEY_DEF_SPEED]     ?: 3000f,
            )
        }

    suspend fun toggleApp(ctx: Context, pkg: String) = ctx.ds.edit { p ->
        val cur = p[KEY_APPS]?.toMutableSet() ?: mutableSetOf()
        if (cur.contains(pkg)) cur.remove(pkg) else cur.add(pkg)
        p[KEY_APPS] = cur
    }

    suspend fun setOverlayPosition(ctx: Context, x: Int, y: Int) = ctx.ds.edit { p ->
        p[KEY_OX] = x; p[KEY_OY] = y
    }

    suspend fun setOverlaySize(ctx: Context, size: OverlaySize) = ctx.ds.edit { p ->
        p[KEY_OVERLAY_SIZE] = size.name
    }

    suspend fun setCustomScale(ctx: Context, scale: Float) = ctx.ds.edit { p ->
        p[KEY_CUSTOM_SCALE] = scale
        p[KEY_OVERLAY_SIZE] = OverlaySize.CUSTOM.name
    }

    suspend fun setScrollEngine(ctx: Context, engine: ScrollEngine) = ctx.ds.edit { p ->
        p[KEY_ENGINE] = engine.name
    }

    suspend fun setFlingWait(ctx: Context, ms: Long) = ctx.ds.edit { p ->
        p[KEY_FLING_WAIT] = ms
    }

    suspend fun setFlingThreshold(ctx: Context, threshold: Float) = ctx.ds.edit { p ->
        p[KEY_FLING_THRESH] = threshold
    }

    suspend fun setDefaultSpeed(ctx: Context, speed: Float) = ctx.ds.edit { p ->
        p[KEY_DEF_SPEED] = speed
    }

    suspend fun setHideOnKeyboard(ctx: Context, v: Boolean) = ctx.ds.edit { p ->
        p[KEY_HIDE_KB] = v
    }

    suspend fun setRememberPosition(ctx: Context, v: Boolean) = ctx.ds.edit { p ->
        p[KEY_REMEMBER_POS] = v
    }
}
