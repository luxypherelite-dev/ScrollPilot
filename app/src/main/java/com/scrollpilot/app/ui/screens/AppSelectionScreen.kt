package com.scrollpilot.app.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.scrollpilot.app.data.SettingsDataStore
import com.scrollpilot.app.util.AppInfo
import com.scrollpilot.app.util.AppListHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppSelectionScreen(padding: PaddingValues = PaddingValues(0.dp)) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps         by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedPkgs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading      by remember { mutableStateOf(true) }
    var searchQuery  by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val settings = SettingsDataStore.globalSettings(ctx).first()
        selectedPkgs = settings.selectedApps
        apps         = withContext(Dispatchers.IO) { AppListHelper.getInstalledUserApps(ctx) }
        loading      = false
    }

    val filtered = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("App Selection", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${selectedPkgs.size} app${if (selectedPkgs.size == 1) "" else "s"} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text("Search apps…") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(
                        app      = app,
                        selected = app.packageName in selectedPkgs,
                        onToggle = { checked ->
                            selectedPkgs = if (checked) selectedPkgs + app.packageName
                                          else           selectedPkgs - app.packageName
                            scope.launch {
                                SettingsDataStore.setSelectedApps(ctx, selectedPkgs)
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val bmp = remember(app.packageName) {
            runCatching { app.icon.toBitmap(48, 48).asImageBitmap() }.getOrNull()
        }
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Box(Modifier.size(40.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }

        Switch(checked = selected, onCheckedChange = onToggle)
    }
}
