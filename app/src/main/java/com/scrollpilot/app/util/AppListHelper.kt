package com.scrollpilot.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

object AppListHelper {
    fun getInstalledUserApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(mainIntent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ctx.packageName }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName in ALWAYS_SHOW }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    label       = pm.getApplicationLabel(info).toString(),
                    icon        = pm.getApplicationIcon(info),
                )
            }
    }

    private val ALWAYS_SHOW = setOf(
        "com.android.chrome",
        "com.google.android.apps.docs",
        "com.google.android.apps.maps",
    )
}
