package com.morad.housekeepermini.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morad.housekeepermini.engine.AppsAndPrivacyEngine
import com.morad.housekeepermini.engine.CfAccountInfo
import com.morad.housekeepermini.engine.CfCleanIpResult
import com.morad.housekeepermini.engine.CloudflareVpnEngine
import com.morad.housekeepermini.engine.DeviceVitalsEngine
import com.morad.housekeepermini.engine.NetworkAndUpdateEngine
import com.morad.housekeepermini.engine.NetworkSummary
import com.morad.housekeepermini.engine.StorageScannerEngine
import com.morad.housekeepermini.model.AppEntry
import com.morad.housekeepermini.model.DeviceVitals
import com.morad.housekeepermini.model.NavTab
import com.morad.housekeepermini.model.PrivacyFinding
import com.morad.housekeepermini.model.RouteProbeResult
import com.morad.housekeepermini.model.SpeedTestResult
import com.morad.housekeepermini.model.StorageCategory
import com.morad.housekeepermini.model.StorageItem
import com.morad.housekeepermini.model.TrackedRepoApp
import com.morad.housekeepermini.model.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HousekeeperUiState(
    val selectedTab: NavTab = NavTab.OVERVIEW,
    val vitals: DeviceVitals = DeviceVitals(),
    val hasFullStorageAccess: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val isScanningStorage: Boolean = false,
    val storageItems: List<StorageItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isScanningApps: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val privacyFindings: List<PrivacyFinding> = emptyList(),
    val networkSummary: NetworkSummary = NetworkSummary(false, "Checking…", "—", "—"),
    val customLanHost: String = "192.168.1.81:8081",
    val isProbingRoutes: Boolean = false,
    val routeResults: List<RouteProbeResult> = emptyList(),
    val isRunningSpeedTest: Boolean = false,
    val speedTestPhase: String = "Ready to test",
    val liveDownloadMbps: Double? = null,
    val liveUploadMbps: Double? = null,
    val livePingMs: Double? = null,
    val liveJitterMs: Double? = null,
    val latestSpeedTest: SpeedTestResult? = null,
    val updateState: UpdateState = UpdateState.Idle,
    val trackedApps: List<TrackedRepoApp> = emptyList(),
    val isCheckingTrackedApps: Boolean = false,
    val cfTokenInput: String = "",
    val cfHasSavedToken: Boolean = false,
    val cfIsVerifying: Boolean = false,
    val cfAccount: CfAccountInfo? = null,
    val cfWorkerHost: String = "",
    val cfWorkerUuid: String = "",
    val cfIsScanningIps: Boolean = false,
    val cfScanStatus: String = "",
    val cfCleanIps: List<CfCleanIpResult> = emptyList(),
    val warpEndpoint: String = CloudflareVpnEngine.warpEndpoints.first(),
    val warpHeavyJunk: Boolean = false,
    val warpIsGenerating: Boolean = false,
    val warpConfigText: String = "",
    val warpStatus: String = "",
    val statusBanner: String? = null
)

class HousekeeperViewModel(application: Application) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(HousekeeperUiState())
    val ui: StateFlow<HousekeeperUiState> = _ui.asStateFlow()

    init {
        val ctx = getApplication<Application>()
        val savedToken = CloudflareVpnEngine.loadSavedToken(ctx)
        val savedHost = CloudflareVpnEngine.loadWorkerHost(ctx)
        val savedUuid = CloudflareVpnEngine.loadOrCreateUuid(ctx)
        _ui.update {
            it.copy(
                cfTokenInput = savedToken,
                cfHasSavedToken = savedToken.isNotBlank(),
                cfWorkerHost = savedHost,
                cfWorkerUuid = savedUuid
            )
        }
        refreshAll()
    }

    fun selectTab(tab: NavTab) {
        _ui.update { it.copy(selectedTab = tab, statusBanner = null) }
    }

    fun clearBanner() {
        _ui.update { it.copy(statusBanner = null) }
    }

    fun refreshAll() {
        val ctx = getApplication<Application>()
        _ui.update { it.copy(trackedApps = NetworkAndUpdateEngine.loadTrackedApps(ctx)) }
        viewModelScope.launch(Dispatchers.IO) {
            val vitals = DeviceVitalsEngine.read(ctx)
            val fullStorage = StorageScannerEngine.hasFullStorageAccess()
            val usageAccess = AppsAndPrivacyEngine.hasUsageAccess(ctx)
            val net = NetworkAndUpdateEngine.readNetworkSummary(ctx)
            _ui.update {
                it.copy(
                    vitals = vitals,
                    hasFullStorageAccess = fullStorage,
                    hasUsageAccess = usageAccess,
                    networkSummary = net
                )
            }
        }
        scanStorage()
        scanApps()
    }

    fun refreshVitals() {
        val ctx = getApplication<Application>()
        _ui.update { it.copy(trackedApps = NetworkAndUpdateEngine.loadTrackedApps(ctx)) }
        viewModelScope.launch(Dispatchers.IO) {
            val vitals = DeviceVitalsEngine.read(ctx)
            val fullStorage = StorageScannerEngine.hasFullStorageAccess()
            val usageAccess = AppsAndPrivacyEngine.hasUsageAccess(ctx)
            val net = NetworkAndUpdateEngine.readNetworkSummary(ctx)
            _ui.update {
                it.copy(
                    vitals = vitals,
                    hasFullStorageAccess = fullStorage,
                    hasUsageAccess = usageAccess,
                    networkSummary = net
                )
            }
        }
    }

    fun scanStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isScanningStorage = true) }
            val items = StorageScannerEngine.scan()
            val fullStorage = StorageScannerEngine.hasFullStorageAccess()
            _ui.update {
                it.copy(
                    isScanningStorage = false,
                    hasFullStorageAccess = fullStorage,
                    storageItems = items,
                    selectedPaths = emptySet()
                )
            }
        }
    }

    fun togglePathSelection(path: String) {
        _ui.update { state ->
            val next = state.selectedPaths.toMutableSet()
            if (!next.add(path)) next.remove(path)
            state.copy(selectedPaths = next)
        }
    }

    fun selectSafeLeftovers() {
        _ui.update { state ->
            val safe = state.storageItems
                .filter {
                    it.category == StorageCategory.APK ||
                        it.category == StorageCategory.DUPLICATE
                }
                .map { it.path }
                .toSet()
            state.copy(
                selectedPaths = safe,
                statusBanner = if (safe.isEmpty()) "No leftover APKs or duplicates found." else "Selected ${safe.size} safe leftovers."
            )
        }
    }

    fun clearStorageSelection() {
        _ui.update { it.copy(selectedPaths = emptySet()) }
    }

    fun deleteSelectedStorageItems() {
        val paths = _ui.value.selectedPaths
        if (paths.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val count = StorageScannerEngine.deleteItems(paths)
            val items = StorageScannerEngine.scan()
            val vitals = DeviceVitalsEngine.read(getApplication())
            _ui.update {
                it.copy(
                    storageItems = items,
                    selectedPaths = emptySet(),
                    vitals = vitals,
                    statusBanner = "Removed $count item(s)."
                )
            }
        }
    }

    fun scanApps() {
        val ctx = getApplication<Application>()
        _ui.update { it.copy(trackedApps = NetworkAndUpdateEngine.loadTrackedApps(ctx)) }
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isScanningApps = true) }
            val (apps, privacy) = AppsAndPrivacyEngine.scanApps(ctx)
            val usageAccess = AppsAndPrivacyEngine.hasUsageAccess(ctx)
            _ui.update {
                it.copy(
                    isScanningApps = false,
                    hasUsageAccess = usageAccess,
                    apps = apps,
                    privacyFindings = privacy
                )
            }
        }
    }

    fun backupApk(entry: AppEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = AppsAndPrivacyEngine.backupApk(entry)
            _ui.update {
                it.copy(
                    statusBanner = if (path != null) {
                        "Saved ${entry.label} APK to Downloads/HousekeeperMini-Backups."
                    } else {
                        "Could not copy ${entry.label} APK (check storage permission)."
                    }
                )
            }
        }
    }

    fun setCustomLanHost(value: String) {
        _ui.update { it.copy(customLanHost = value) }
    }

    fun runSpeedTest() {
        if (_ui.value.isRunningSpeedTest) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update {
                it.copy(
                    isRunningSpeedTest = true,
                    speedTestPhase = "Starting speed test…",
                    liveDownloadMbps = null,
                    liveUploadMbps = null,
                    livePingMs = null,
                    liveJitterMs = null
                )
            }
            val result = NetworkAndUpdateEngine.runSpeedTest { phase, dl, ul, ping, jitter ->
                _ui.update { state ->
                    state.copy(
                        speedTestPhase = phase,
                        liveDownloadMbps = dl ?: state.liveDownloadMbps,
                        liveUploadMbps = ul ?: state.liveUploadMbps,
                        livePingMs = ping ?: state.livePingMs,
                        liveJitterMs = jitter ?: state.liveJitterMs
                    )
                }
            }
            _ui.update {
                it.copy(
                    isRunningSpeedTest = false,
                    speedTestPhase = if (result.error == null) "Test complete" else result.error,
                    liveDownloadMbps = result.downloadMbps,
                    liveUploadMbps = result.uploadMbps,
                    livePingMs = result.pingMs,
                    liveJitterMs = result.jitterMs,
                    latestSpeedTest = result
                )
            }
        }
    }

    fun runRouteSweep() {
        val host = _ui.value.customLanHost
        val ctx = getApplication<Application>()
        _ui.update { it.copy(trackedApps = NetworkAndUpdateEngine.loadTrackedApps(ctx)) }
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isProbingRoutes = true) }
            val summary = NetworkAndUpdateEngine.readNetworkSummary(ctx)
            val results = NetworkAndUpdateEngine.runRouteSweep(host)
            _ui.update {
                it.copy(
                    isProbingRoutes = false,
                    networkSummary = summary,
                    routeResults = results
                )
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(updateState = UpdateState.Checking) }
            val result = NetworkAndUpdateEngine.checkForUpdate()
            _ui.update { it.copy(updateState = result) }
        }
    }

    fun installUpdate(apkUrl: String) {
        val ctx = getApplication<Application>()
        _ui.update { it.copy(trackedApps = NetworkAndUpdateEngine.loadTrackedApps(ctx)) }
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(updateState = UpdateState.Downloading(0)) }
            val err = NetworkAndUpdateEngine.downloadAndInstallApk(ctx, apkUrl) { pct ->
                _ui.update { it.copy(updateState = UpdateState.Downloading(pct)) }
            }
            if (err != null) {
                _ui.update { it.copy(updateState = UpdateState.Error(err)) }
            } else {
                _ui.update { it.copy(updateState = UpdateState.Idle) }
            }
        }
    }

    fun addTrackedApp(repoInput: String, packageNameInput: String) {
        val repo = NetworkAndUpdateEngine.parseGitHubRepo(repoInput)
        if (repo == null) {
            _ui.update { it.copy(statusBanner = "Enter a valid GitHub repo like owner/repo or https://github.com/owner/repo.") }
            return
        }
        val ctx = getApplication<Application>()
        val current = _ui.value.trackedApps
        if (current.any { it.repo.equals(repo, ignoreCase = true) }) {
            _ui.update { it.copy(statusBanner = "$repo is already in your list.") }
            return
        }
        val added = TrackedRepoApp(
            repo = repo,
            displayName = repo.substringAfter("/"),
            packageName = packageNameInput.trim(),
            statusText = "Checking…"
        )
        val updatedList = current + added
        NetworkAndUpdateEngine.saveTrackedApps(ctx, updatedList)
        _ui.update { it.copy(trackedApps = updatedList, statusBanner = "Added $repo") }

        viewModelScope.launch(Dispatchers.IO) {
            val checked = NetworkAndUpdateEngine.checkTrackedApp(ctx, added)
            _ui.update { state ->
                state.copy(
                    trackedApps = state.trackedApps.map {
                        if (it.repo.equals(repo, ignoreCase = true)) checked else it
                    }
                )
            }
        }
    }

    fun removeTrackedApp(repo: String) {
        val ctx = getApplication<Application>()
        val next = _ui.value.trackedApps.filterNot { !it.isBuiltIn && it.repo.equals(repo, ignoreCase = true) }
        NetworkAndUpdateEngine.saveTrackedApps(ctx, next)
        _ui.update { it.copy(trackedApps = next) }
    }

    fun checkAllTrackedApps() {
        val ctx = getApplication<Application>()
        val snapshot = _ui.value.trackedApps
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isCheckingTrackedApps = true) }
            val refreshed = snapshot.map { NetworkAndUpdateEngine.checkTrackedApp(ctx, it) }
            _ui.update {
                it.copy(
                    isCheckingTrackedApps = false,
                    trackedApps = refreshed
                )
            }
        }
    }

    // MARK: - Cloudflare Clean VPN & AmneziaWG WARP

    fun setCfToken(value: String) {
        _ui.update { it.copy(cfTokenInput = value) }
    }

    fun verifyCfToken() {
        val ctx = getApplication<Application>()
        val raw = _ui.value.cfTokenInput
        if (_ui.value.cfIsVerifying) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(cfIsVerifying = true) }
            val result = CloudflareVpnEngine.verifyToken(raw)
            result.fold(
                onSuccess = { info ->
                    val cleaned = CloudflareVpnEngine.sanitizeToken(raw)
                    CloudflareVpnEngine.saveToken(ctx, cleaned)
                    val autoHost = if (_ui.value.cfWorkerHost.isBlank() && info.workers.isNotEmpty() && !info.subdomain.isNullOrBlank()) {
                        "${info.workers.first()}.${info.subdomain}.workers.dev".also {
                            CloudflareVpnEngine.saveWorkerHost(ctx, it)
                        }
                    } else {
                        _ui.value.cfWorkerHost
                    }
                    _ui.update {
                        it.copy(
                            cfIsVerifying = false,
                            cfTokenInput = cleaned,
                            cfHasSavedToken = true,
                            cfAccount = info,
                            cfWorkerHost = autoHost,
                            statusBanner = "Connected to ${info.accountName}"
                        )
                    }
                },
                onFailure = { err ->
                    _ui.update {
                        it.copy(
                            cfIsVerifying = false,
                            statusBanner = err.localizedMessage ?: "Could not verify Cloudflare token."
                        )
                    }
                }
            )
        }
    }

    fun forgetCfToken() {
        CloudflareVpnEngine.forgetToken(getApplication())
        _ui.update {
            it.copy(
                cfTokenInput = "",
                cfHasSavedToken = false,
                cfAccount = null,
                statusBanner = "Cloudflare token removed."
            )
        }
    }

    fun deleteCfWorker(name: String) {
        val acc = _ui.value.cfAccount ?: return
        val token = _ui.value.cfTokenInput
        viewModelScope.launch(Dispatchers.IO) {
            val res = CloudflareVpnEngine.deleteWorker(acc.accountId, token, name)
            res.fold(
                onSuccess = { msg ->
                    _ui.update { state ->
                        val nextWorkers = state.cfAccount?.workers?.filterNot { it == name } ?: emptyList()
                        state.copy(
                            cfAccount = state.cfAccount?.copy(workers = nextWorkers),
                            statusBanner = msg
                        )
                    }
                },
                onFailure = { err ->
                    _ui.update { it.copy(statusBanner = err.localizedMessage ?: "Delete failed.") }
                }
            )
        }
    }

    fun setCfWorkerHost(host: String) {
        CloudflareVpnEngine.saveWorkerHost(getApplication(), host)
        _ui.update { it.copy(cfWorkerHost = host) }
    }

    fun regenerateCfUuid() {
        val fresh = java.util.UUID.randomUUID().toString().lowercase(java.util.Locale.US)
        CloudflareVpnEngine.saveUuid(getApplication(), fresh)
        _ui.update { it.copy(cfWorkerUuid = fresh, statusBanner = "Generated a fresh UUID.") }
    }

    fun scanCleanIps() {
        if (_ui.value.cfIsScanningIps) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update {
                it.copy(
                    cfIsScanningIps = true,
                    cfCleanIps = emptyList(),
                    cfScanStatus = "Scanning 24 Cloudflare IPv4 edge addresses…"
                )
            }
            val found = CloudflareVpnEngine.scanCleanIps(count = 24, port = 443) { tested, total, cleanSoFar ->
                _ui.update {
                    it.copy(
                        cfCleanIps = cleanSoFar,
                        cfScanStatus = "Tested $tested/$total IPs • ${cleanSoFar.size} clean found"
                    )
                }
            }
            _ui.update {
                it.copy(
                    cfIsScanningIps = false,
                    cfCleanIps = found,
                    cfScanStatus = if (found.isEmpty()) {
                        "No clean IPs responded. Turn VPN off and try again, or use AmneziaWG WARP."
                    } else {
                        "Found ${found.size} clean IPs. Fastest: ${found.first().ip} (${found.first().latencyMs} ms)."
                    }
                )
            }
        }
    }

    fun setWarpEndpoint(endpoint: String) {
        _ui.update { it.copy(warpEndpoint = endpoint) }
    }

    fun setWarpHeavyJunk(heavy: Boolean) {
        _ui.update { it.copy(warpHeavyJunk = heavy) }
    }

    fun generateWarpConfig() {
        if (_ui.value.warpIsGenerating) return
        val endpoint = _ui.value.warpEndpoint
        val heavy = _ui.value.warpHeavyJunk
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(warpIsGenerating = true, warpStatus = "Generating Curve25519 keys & registering with Cloudflare WARP…") }
            val output = CloudflareVpnEngine.generateAmneziaWarpConfig(
                endpoint = endpoint,
                heavyJunk = heavy,
                registerOnline = true
            )
            _ui.update {
                it.copy(
                    warpIsGenerating = false,
                    warpConfigText = output.confText,
                    warpStatus = output.message,
                    statusBanner = output.message
                )
            }
        }
    }

    fun showBanner(msg: String) {
        _ui.update { it.copy(statusBanner = msg) }
    }
}
