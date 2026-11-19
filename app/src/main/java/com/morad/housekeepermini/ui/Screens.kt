package com.morad.housekeepermini.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morad.housekeepermini.BuildConfig
import com.morad.housekeepermini.engine.AppsAndPrivacyEngine
import com.morad.housekeepermini.engine.StorageScannerEngine
import com.morad.housekeepermini.model.NavTab
import com.morad.housekeepermini.model.StorageCategory
import com.morad.housekeepermini.model.UpdateState
import com.morad.housekeepermini.model.formatBytes
import com.morad.housekeepermini.model.formatLastUsed

@Composable
fun OverviewScreen(
    state: HousekeeperUiState,
    onRefresh: () -> Unit,
    onNavigate: (NavTab) -> Unit
) {
    val context = LocalContext.current
    val v = state.vitals

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = v.deviceName.ifBlank { "Android Device" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildString {
                                    append(v.androidVersion)
                                    if (v.isQuestOrVr) append(" • Meta Quest Mode")
                                    append(" • ${v.cpuAbi}")
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        FilledTonalButton(onClick = onRefresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Housekeeper Mini v${BuildConfig.VERSION_NAME} • Made by Morad",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Memory, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Memory (RAM)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { v.ramProgress },
                        modifier = Modifier.fillMaxWidth().height(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${formatBytes(context, v.availRamBytes)} free of ${formatBytes(context, v.totalRamBytes)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storage, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Internal Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { v.storageProgress },
                        modifier = Modifier.fillMaxWidth().height(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${formatBytes(context, v.freeStorageBytes)} free of ${formatBytes(context, v.totalStorageBytes)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { onNavigate(NavTab.STORAGE) }) {
                        Icon(Icons.Outlined.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review Clutter (${state.storageItems.size} found)")
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Battery & Temperature", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val batText = if (v.batteryPercent >= 0) {
                        "${v.batteryPercent}%${if (v.isCharging) " (Charging)" else ""} • ${v.batteryTempC}°C • ${v.thermalLabel}"
                    } else {
                        "Thermal: ${v.thermalLabel}"
                    }
                    Text(batText, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = { onNavigate(NavTab.APPS) }
                ) {
                    Text("Apps (${state.apps.size})")
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f).height(52.dp),
                    onClick = { onNavigate(NavTab.PRIVACY) }
                ) {
                    Text("Privacy (${state.privacyFindings.size})")
                }
            }
        }
    }
}

@Composable
fun StorageScreen(
    state: HousekeeperUiState,
    onScan: () -> Unit,
    onTogglePath: (String) -> Unit,
    onSelectSafe: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        val selectedBytes = state.storageItems
            .filter { it.path in state.selectedPaths }
            .sumOf { it.sizeBytes }
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Remove ${state.selectedPaths.size} file(s)?") },
            text = {
                Text("This will permanently remove ${formatBytes(context, selectedBytes)} of selected files. Everything else is left alone.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showConfirm = false
                        onDeleteSelected()
                    }
                ) {
                    Text("Remove Selected")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.hasFullStorageAccess) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Allow Storage Access to see all Downloads, APKs, and duplicates.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { StorageScannerEngine.openStoragePermissionSettings(context) }) {
                            Text("Grant Storage Access")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onScan, enabled = !state.isScanningStorage) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.isScanningStorage) "Scanning…" else "Scan")
                }
                OutlinedButton(onClick = onSelectSafe, enabled = state.storageItems.isNotEmpty()) {
                    Text("Select Safe")
                }
                if (state.selectedPaths.isNotEmpty()) {
                    TextButton(onClick = onClearSelection) { Text("Clear") }
                }
            }
        }

        if (state.selectedPaths.isNotEmpty()) {
            item {
                val totalSelected = state.storageItems
                    .filter { it.path in state.selectedPaths }
                    .sumOf { it.sizeBytes }
                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = { showConfirm = true }
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove ${state.selectedPaths.size} item(s) • ${formatBytes(context, totalSelected)}")
                }
            }
        }

        if (state.isScanningStorage) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.storageItems.isEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Storage looks tidy!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("No leftover APKs, duplicates, or oversized clutter were found.")
                    }
                }
            }
        } else {
            for (category in StorageCategory.entries) {
                val group = state.storageItems.filter { it.category == category }
                if (group.isEmpty()) continue
                item {
                    Text(
                        text = "${category.title} (${group.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = category.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(group, key = { it.path }) { item ->
                    val checked = item.path in state.selectedPaths
                    Card(onClick = { onTogglePath(item.path) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { onTogglePath(item.path) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    "${formatBytes(context, item.sizeBytes)} • ${item.detail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppsScreen(
    state: HousekeeperUiState,
    onRefresh: () -> Unit,
    onBackupApk: (com.morad.housekeepermini.model.AppEntry) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var unusedOnly by remember { mutableStateOf(false) }

    val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
    val now = remember(state.apps) { System.currentTimeMillis() }
    val filteredApps = remember(state.apps, searchQuery, unusedOnly) {
        state.apps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesUnused = !unusedOnly ||
                (app.lastUsedMs > 0L && (now - app.lastUsedMs) >= thirtyDaysMs)
            matchesQuery && matchesUnused
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.hasUsageAccess) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Grant Usage Access to see app cache sizes and last-used dates.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { AppsAndPrivacyEngine.openUsageAccessSettings(context) }) {
                            Text("Enable Usage Access")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Installed Apps (${filteredApps.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                FilledTonalButton(onClick = onRefresh, enabled = !state.isScanningApps) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.hasUsageAccess) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !unusedOnly,
                        onClick = { unusedOnly = false },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = unusedOnly,
                        onClick = { unusedOnly = true },
                        label = { Text("Unused 30d+") }
                    )
                }
            }
        }

        items(filteredApps, key = { it.packageName }) { app ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatBytes(context, app.totalBytes)} total" +
                                    if (app.cacheBytes > 0) " (${formatBytes(context, app.cacheBytes)} cache)" else "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${formatLastUsed(app.lastUsedMs)} • v${app.versionName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { AppsAndPrivacyEngine.openAppDetails(context, app.packageName) }) {
                            Text("Storage / Cache")
                        }
                        OutlinedButton(onClick = { onBackupApk(app) }) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Backup APK")
                        }
                        TextButton(onClick = { AppsAndPrivacyEngine.requestUninstall(context, app.packageName) }) {
                            Text("Uninstall", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(
    state: HousekeeperUiState,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Privacy Doctor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Apps holding sensitive permissions (Camera, Mic, Location, Installs).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Audit")
                }
            }
        }

        if (state.privacyFindings.isEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Icon(Icons.Outlined.Security, contentDescription = null)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No user apps hold sensitive permissions right now.")
                    }
                }
            }
        } else {
            items(state.privacyFindings, key = { it.packageName }) { finding ->
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(finding.appLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(finding.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(onClick = { AppsAndPrivacyEngine.openAppDetails(context, finding.packageName) }) {
                            Text("Review")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkScreen(
    state: HousekeeperUiState,
    onCustomHostChange: (String) -> Unit,
    onRunSweep: () -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    onAddTrackedApp: (String, String) -> Unit,
    onRemoveTrackedApp: (String) -> Unit,
    onCheckAllTrackedApps: () -> Unit
) {
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var repoInput by remember { mutableStateOf("") }
    var packageInput by remember { mutableStateOf("") }

    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { Text("Add GitHub App") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Paste any GitHub repo that publishes APK releases (Obtainium-style).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("GitHub URL or owner/repo") },
                        placeholder = { Text("ImranR98/Obtainium") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = packageInput,
                        onValueChange = { packageInput = it },
                        label = { Text("Package ID (optional, for version check)") },
                        placeholder = { Text("dev.imranr.obtainium") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddTrackedApp(repoInput, packageInput)
                    repoInput = ""
                    packageInput = ""
                    showAddRepoDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRepoDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NetworkCheck, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Connection & Route Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Network: ${state.networkSummary.transportLabel} • IP: ${state.networkSummary.localIp}")
                    Text("DNS: ${state.networkSummary.dnsServers}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.customLanHost,
                        onValueChange = onCustomHostChange,
                        label = { Text("Optional LAN Host (e.g. PS5 or Mac MLX)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRunSweep,
                        enabled = !state.isProbingRoutes,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (state.isProbingRoutes) "Checking Routes…" else "Run Route Check")
                    }
                }
            }
        }

        items(state.routeResults, key = { it.name + it.target }) { probe ->
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(probe.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(probe.target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = if (probe.reachable) probe.detail else "Blocked / Offline",
                        color = if (probe.reachable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("App & Self-Updater", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Housekeeper Mini v${BuildConfig.VERSION_NAME} • Made by Morad",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showAddRepoDialog = true }) {
                                Text("+ Add")
                            }
                            FilledTonalButton(onClick = onCheckAllTrackedApps, enabled = !state.isCheckingTrackedApps) {
                                Text(if (state.isCheckingTrackedApps) "Checking…" else "Check All")
                            }
                        }
                    }

                    when (val u = state.updateState) {
                        is UpdateState.Downloading -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { u.progress / 100f }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Downloading APK… ${u.progress}%", style = MaterialTheme.typography.bodySmall)
                        }
                        is UpdateState.Error -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(u.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        else -> Unit
                    }
                }
            }
        }

        items(state.trackedApps, key = { it.repo }) { app ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(app.repo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(app.statusText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (app.apkUrl.isNotEmpty() || !app.isBuiltIn) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (app.apkUrl.isNotEmpty()) {
                                Button(onClick = { onInstallUpdate(app.apkUrl) }) {
                                    Text(if (app.hasUpdate) "Update v${app.latestVersion}" else "Install v${app.latestVersion}")
                                }
                            }
                            if (!app.isBuiltIn) {
                                TextButton(onClick = { onRemoveTrackedApp(app.repo) }) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
