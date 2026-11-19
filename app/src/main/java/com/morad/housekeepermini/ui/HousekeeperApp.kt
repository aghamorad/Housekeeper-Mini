package com.morad.housekeepermini.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.morad.housekeepermini.model.NavTab

@Composable
fun HousekeeperApp(vm: HousekeeperViewModel = viewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusBanner) {
        val msg = state.statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.clearBanner()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        NavTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = state.selectedTab == tab,
                                onClick = { vm.selectTab(tab) },
                                icon = { Icon(iconFor(tab), contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        Spacer(modifier = Modifier.padding(top = 12.dp))
                        NavTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = state.selectedTab == tab,
                                onClick = { vm.selectTab(tab) },
                                icon = { Icon(iconFor(tab), contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Made by\nMorad",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    when (state.selectedTab) {
                        NavTab.OVERVIEW -> OverviewScreen(
                            state = state,
                            onRefresh = vm::refreshVitals,
                            onNavigate = vm::selectTab
                        )
                        NavTab.STORAGE -> StorageScreen(
                            state = state,
                            onScan = vm::scanStorage,
                            onTogglePath = vm::togglePathSelection,
                            onSelectSafe = vm::selectSafeLeftovers,
                            onClearSelection = vm::clearStorageSelection,
                            onDeleteSelected = vm::deleteSelectedStorageItems
                        )
                        NavTab.APPS -> AppsScreen(
                            state = state,
                            onRefresh = vm::scanApps,
                            onBackupApk = vm::backupApk
                        )
                        NavTab.PRIVACY -> PrivacyScreen(
                            state = state,
                            onRefresh = vm::scanApps
                        )
                        NavTab.NETWORK -> NetworkScreen(
                            state = state,
                            onCustomHostChange = vm::setCustomLanHost,
                            onRunSweep = vm::runRouteSweep,
                            onCheckUpdates = vm::checkForUpdates,
                            onInstallUpdate = vm::installUpdate,
                            onAddTrackedApp = vm::addTrackedApp,
                            onRemoveTrackedApp = vm::removeTrackedApp,
                            onCheckAllTrackedApps = vm::checkAllTrackedApps
                        )
                    }
                }
            }
        }
    }
}

private fun iconFor(tab: NavTab): ImageVector = when (tab) {
    NavTab.OVERVIEW -> Icons.Outlined.Home
    NavTab.STORAGE -> Icons.Outlined.Storage
    NavTab.APPS -> Icons.Outlined.Apps
    NavTab.PRIVACY -> Icons.Outlined.Security
    NavTab.NETWORK -> Icons.Outlined.NetworkCheck
}
