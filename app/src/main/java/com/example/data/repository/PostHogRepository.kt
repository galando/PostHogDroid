package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.HogNotificationHelper
import com.example.data.api.PostHogClient
import com.example.data.database.AlertEntity
import com.example.data.database.DashboardEntity
import com.example.data.database.InsightEntity
import com.example.data.database.NotificationLogEntity
import com.example.data.database.PostHogDao
import com.example.data.database.PostHogSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

data class SessionCredentials(
    val hostUrl: String,
    val personalApiKey: String,
    val projectId: String,
    val isDemoMode: Boolean,
    val email: String? = null
)

data class RepoSeries(
    val name: String,
    val data: List<Double>
)

/** Pure condition evaluator — no I/O, testable in isolation. */
fun evaluateCondition(condition: String, metricValue: Double, threshold: Double): Boolean {
    return if (condition == "ABOVE") metricValue > threshold else metricValue < threshold
}

/** Pure metric extractor — derives a single numeric value from parsed series. */
fun extractMetricValue(seriesList: List<RepoSeries>): Double {
    return when {
        seriesList.isEmpty() -> 0.0
        seriesList.size == 1 -> seriesList.first().data.lastOrNull() ?: 0.0
        else -> seriesList.sumOf { it.data.lastOrNull() ?: 0.0 }
    }
}

fun parseRepositoryDataJson(dataJson: String): List<RepoSeries> {
    if (dataJson.isBlank()) return emptyList()
    if (dataJson.contains("|") || dataJson.contains(":")) {
        return dataJson.split("|").mapNotNull { part ->
            val colonIndex = part.indexOf(":")
            if (colonIndex != -1) {
                val name = part.substring(0, colonIndex)
                val valsStr = part.substring(colonIndex + 1)
                val vals = valsStr.split(",").mapNotNull { it.toDoubleOrNull() }
                RepoSeries(name, vals)
            } else {
                val vals = part.split(",").mapNotNull { it.toDoubleOrNull() }
                RepoSeries("Metric", vals)
            }
        }.filter { it.data.isNotEmpty() }
    } else {
        val vals = dataJson.split(",").mapNotNull { it.toDoubleOrNull() }
        return if (vals.isEmpty()) emptyList() else listOf(RepoSeries("Metric", vals))
    }
}

class PostHogRepository(
    private val postHogDao: PostHogDao,
    private val notificationHelper: HogNotificationHelper,
    private val secureKeyStore: SecureKeyStore
) {
    // Structured scope bound to the repository lifecycle — cancelled on logout
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow<SessionCredentials?>(null)
    val session: StateFlow<SessionCredentials?> = _session.asStateFlow()

    val settings: Flow<PostHogSettings?> = postHogDao.getSettingsFlow()
    val dashboards: Flow<List<DashboardEntity>> = postHogDao.getAllDashboardsFlow()
    val allInsights: Flow<List<InsightEntity>> = postHogDao.getAllInsightsFlow()
    val alerts: Flow<List<AlertEntity>> = postHogDao.getAllAlertsFlow()
    val notificationLogs: Flow<List<NotificationLogEntity>> = postHogDao.getAllNotificationLogsFlow()

    fun getActiveSession(): SessionCredentials? = _session.value

    suspend fun getSettings(): PostHogSettings? = postHogDao.getSettingsDirect()

    suspend fun login(hostUrl: String, personalApiKey: String, projectId: String, isDemoMode: Boolean, email: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            if (isDemoMode) {
                _session.value = SessionCredentials(hostUrl, personalApiKey, projectId, isDemoMode, email)
                val defaultSettings = PostHogSettings(
                    id = 1,
                    hostUrl = hostUrl,
                    projectId = projectId,
                    useDemoMode = isDemoMode
                )
                postHogDao.saveSettings(defaultSettings)
                loadPrepopulatedDemoData()
                true
            } else {
                val sanitizedUrl = if (hostUrl.endsWith("/")) hostUrl else "$hostUrl/"
                try {
                    val api = PostHogClient.createService(sanitizedUrl)
                    val authHeader = "Bearer $personalApiKey"

                    // Lightweight, fast network call to verify if authentication credentials and project ID are valid.
                    api.getDashboards(projectId = projectId, authHeader = authHeader)

                    // Clear prior cache before starting remote sync to keep things completely fresh and clean
                    postHogDao.clearDashboards()
                    postHogDao.clearInsights()
                    postHogDao.clearAlerts()

                    // Save API key to encrypted storage (not Room)
                    secureKeyStore.saveApiKey(personalApiKey)

                    // Save settings (without API key) & update memory session immediately
                    val defaultSettings = PostHogSettings(
                        id = 1,
                        hostUrl = hostUrl,
                        projectId = projectId,
                        useDemoMode = isDemoMode
                    )
                    postHogDao.saveSettings(defaultSettings)
                    _session.value = SessionCredentials(hostUrl, personalApiKey, projectId, isDemoMode, email)

                    // Trigger the detailed, deep remote data fetching concurrently in the background.
                    // Use cached server results (no forced recompute) so dashboards populate fast
                    // right after login; the user can hit Refresh for a forced recalculation.
                    repositoryScope.launch {
                        try {
                            syncWithCredentials(sanitizedUrl, personalApiKey, projectId, forceRefresh = false)
                        } catch (bgExc: Exception) {
                            if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Background login post-sync details fetch failed", bgExc)
                        }
                    }

                    true
                } catch (e: retrofit2.HttpException) {
                    val code = e.code()
                    val message = when (code) {
                        401 -> "Unauthorized: Your Personal API Key is invalid or expired."
                        403 -> "Forbidden: You don't have permission to access Project ID '$projectId'."
                        404 -> "Not Found: Project ID '$projectId' does not exist or host URL is wrong."
                        else -> "API error ($code): ${e.message()}"
                    }
                    throw Exception(message)
                } catch (e: java.net.UnknownHostException) {
                    throw Exception("Unresolved host: Verify the server URL address syntax.")
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: e.message ?: "Network unreachable"
                    throw Exception("Connection failed: $msg")
                }
            }
        }

    suspend fun logout() {
        _session.value = null
        // Cancel in-flight coroutines bound to the old session
        repositoryScope.cancel()
        // Clear encrypted key store
        secureKeyStore.clear()
        // Clear all cached database insights and records on logout for safety
        postHogDao.clearDashboards()
        postHogDao.clearInsights()
        postHogDao.clearAlerts()

        // Reset stored settings back to empty live configuration
        val defaultSettings = PostHogSettings(
            id = 1,
            hostUrl = "https://app.posthog.com",
            projectId = "",
            useDemoMode = false
        )
        postHogDao.saveSettings(defaultSettings)
    }

    suspend fun initDefaultSettingsAndDemoData() {
        val current = postHogDao.getSettingsDirect()

        // Resolve default configuration from BuildConfig if present initially
        val envHost = if (BuildConfig.POSTHOG_HOST_URL.isNotBlank() &&
                         !BuildConfig.POSTHOG_HOST_URL.contains("your_") &&
                         !BuildConfig.POSTHOG_HOST_URL.contains("example")) {
            BuildConfig.POSTHOG_HOST_URL
        } else {
            "https://app.posthog.com"
        }

        val envProjectId = if (BuildConfig.POSTHOG_PROJECT_ID.isNotBlank() &&
                             !BuildConfig.POSTHOG_PROJECT_ID.contains("your_") &&
                             !BuildConfig.POSTHOG_PROJECT_ID.contains("example")) {
            BuildConfig.POSTHOG_PROJECT_ID
        } else {
            ""
        }

        val settingsToUse = if (current != null) {
            // Already set up before. Keep and respect their configuration
            current
        } else {
            // Initialize for the first time
            PostHogSettings(
                id = 1,
                hostUrl = envHost,
                projectId = envProjectId,
                useDemoMode = false
            )
        }

        postHogDao.saveSettings(settingsToUse)
        if (BuildConfig.DEBUG) Log.d("PostHogRepository", "Initialized settings: Host=${settingsToUse.hostUrl}, demoMode=${settingsToUse.useDemoMode}")

        // Restore active session with stored credentials if they are valid
        if (settingsToUse.useDemoMode) {
            _session.value = SessionCredentials(
                hostUrl = "https://app.posthog.com",
                personalApiKey = "DemoModeMockKey",
                projectId = "DemoProject",
                isDemoMode = true,
                email = "Demo User"
            )
            loadPrepopulatedDemoData()
        } else {
            // Read API key from encrypted storage (not Room)
            val encryptedKey = secureKeyStore.readApiKey()
            if (encryptedKey.isNotBlank() && settingsToUse.projectId.isNotBlank()) {
                _session.value = SessionCredentials(
                    hostUrl = settingsToUse.hostUrl,
                    personalApiKey = encryptedKey,
                    projectId = settingsToUse.projectId,
                    isDemoMode = false,
                    email = "Workspace Link"
                )
                // Sync or pre-populate data
                try {
                    syncRemoteData()
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Failed to auto-sync remote data on app startup", e)
                }
            } else {
                // Leave session null so they can choose to either try demo mode or input credentials
                _session.value = null
            }
        }
    }

    private fun generatePast7DaysLabels(): String {
        val sdf = SimpleDateFormat("MMM dd", Locale.US)
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        for (i in 0 until 7) {
            if (i == 6) {
                list.add("Today")
            } else {
                list.add(sdf.format(cal.time))
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return list.joinToString(",")
    }

    suspend fun loadPrepopulatedDemoData() {
        // Dashboards
        val demoDashboards = listOf(
            DashboardEntity(161, "🚀 SaaS Conversions & Funnels", "Core funnel metrics, feature adoption, and growth campaigns.", "2026-05-18T10:00:00Z", true),
            DashboardEntity(162, "📱 Mobile App KPIs", "Daily active users, performance crashes, retention, and onboarding metrics.", "2026-05-20T14:30:00Z", false),
            DashboardEntity(163, "🛒 Checkout Basket Health", "Revenue streams, checkout abandonment drops, and gateway issues.", "2026-05-21T08:15:00Z", false)
        )
        postHogDao.insertDashboards(demoDashboards)

        val rolledDatesLabels = generatePast7DaysLabels()

        // Insights
        val demoInsights = listOf(
            // SaaS
            InsightEntity(201, 161, "Sign-up to Paid Conversion Funnel", "Step-by-step onboarding drop-off rate", "18.2%", "ActionsBarValue", "Visit Landing,Sign-up,Verify Email,Upgrade API,First Payment", "100.0,65.2,42.8,25.0,18.2", "DOWN"),
            InsightEntity(202, 161, "Daily Active Co-Pilot Users", "Feature interactions count per day", "5,420 DAU", "ActionsLineGraph", rolledDatesLabels, "Copilot Core Users:3200.0,3350.0,3700.0,3900.0,3800.0,4100.0,4250.0|Web Console Users:1000.0,1000.0,1100.0,1200.0,1100.0,1100.0,1170.0", "UP"),
            InsightEntity(203, 161, "API Gateway Timeout Errors", "Active error spikes on microservices", "34 Errors", "ActionsLineGraph", rolledDatesLabels, "Auth Service:10.0,12.0,80.0,150.0,260.0,35.0,20.0|Payment Gateway:5.0,8.0,40.0,90.0,150.0,20.0,14.0", "DOWN"),
            
            // Mobile App
            InsightEntity(204, 162, "Average Session Length", "Session activity length in minutes", "8.4m", "ActionsLineGraph", rolledDatesLabels, "Android App:5.2,5.8,7.1,6.8,7.0,6.9,7.2|iOS App:7.2,8.4,9.9,9.0,9.4,9.1,9.6", "UP"),
            InsightEntity(205, 162, "App Crash-Free Users Rate", "Percentage of sessions without severe crashes", "99.85%", "ActionsLineGraph", rolledDatesLabels, "Android App:99.8,99.75,99.85,99.88,99.72,99.8,99.82|iOS App:100.0,99.89,99.85,99.88,99.84,99.9,99.88", "UP"),
            
            // E-Commerce
            InsightEntity(206, 163, "Daily Gross Revenue (USD)", "Aggregate shopping checkouts volume", "$42,500", "ActionsBarValue", rolledDatesLabels, "Enterprise Clients:20000.0,22000.0,18000.0,25000.0,26000.0,24000.0,26500.0|Self-Serve Plans:10000.0,10000.0,10000.0,14000.0,15000.0,14000.0,16000.0", "UP"),
            InsightEntity(207, 163, "Cart Abandonment Drop-off", "Percentage of users leaving before complete checkout", "68.4%", "ActionsLineGraph", rolledDatesLabels, "Desktop Web:66.4,64.1,66.5,61.2,63.0,62.1,62.4|Mobile Devices:78.4,76.1,76.5,73.2,75.0,74.1,74.4", "DOWN")
        )
        postHogDao.insertInsights(demoInsights)

        // Alerts
        val demoAlerts = listOf(
            AlertEntity(301, "Severe Crash-Free Rate Under-Limit", 205, "App Crash-Free Users Rate", "Crash-Free Rate", "BELOW", 99.8, 99.85, true, false, "OK"),
            AlertEntity(302, "API Gateway Errors Alarm Spiker", 203, "API Gateway Timeout Errors", "Errors Count", "ABOVE", 200.0, 34.0, true, false, "OK"),
            AlertEntity(303, "Declining Feature Interactions Threshold", 202, "Daily Active Co-Pilot Users", "DAU Count", "BELOW", 4000.0, 5420.0, true, false, "OK")
        )
        postHogDao.insertAlerts(demoAlerts)
        
        if (BuildConfig.DEBUG) Log.d("PostHogRepository", "Inserted prepopulated demo dashboards, insights, and alerts.")
    }

    suspend fun saveSettings(hostUrl: String, personalApiKey: String, projectId: String, useDemoMode: Boolean) {
        val currentSession = _session.value
        val updatedApiKey = if (personalApiKey.isNotBlank()) personalApiKey else (currentSession?.personalApiKey ?: "")
        _session.value = SessionCredentials(hostUrl, updatedApiKey, projectId, useDemoMode, currentSession?.email)

        // Save API key to encrypted storage
        if (updatedApiKey.isNotBlank()) {
            secureKeyStore.saveApiKey(updatedApiKey)
        }

        val settings = PostHogSettings(1, hostUrl, projectId, useDemoMode)
        postHogDao.saveSettings(settings)
        if (BuildConfig.DEBUG) Log.d("PostHogRepository", "Updated settings: useDemoMode = $useDemoMode")
        if (useDemoMode) {
            loadPrepopulatedDemoData()
        } else {
            // Clear cache and trigger fresh sync
            postHogDao.clearDashboards()
            postHogDao.clearInsights()
            postHogDao.clearAlerts()
            syncRemoteData()
        }
    }

    suspend fun createLocalAlert(name: String, insightId: Int, insightName: String, metric: String, condition: String, threshold: Double) {
        val randId = Random.nextInt(1000, 99999)
        val alert = AlertEntity(
            id = randId,
            name = name,
            insightId = insightId,
            insightName = insightName,
            metric = metric,
            condition = condition,
            threshold = threshold,
            currentValue = 0.0,
            isActive = true,
            isTriggered = false,
            status = "OK"
        )
        postHogDao.insertAlerts(listOf(alert))
        evaluateAlertsDirect(listOf(alert))
    }

    suspend fun toggleMuteAlert(alertId: Int) {
        val all = postHogDao.getAllAlertsDirect()
        val match = all.find { it.id == alertId }
        if (match != null) {
            val updated = match.copy(isMuted = !match.isMuted)
            postHogDao.updateAlert(updated)
        }
    }

    suspend fun deleteAlert(alertId: Int) {
        val all = postHogDao.getAllAlertsDirect()
        val updated = all.filter { it.id != alertId }
        postHogDao.clearAlerts()
        postHogDao.insertAlerts(updated)
    }

    suspend fun saveAlertThreshold(insightId: Int, insightName: String, threshold: Double, isActive: Boolean) {
        val all = postHogDao.getAllAlertsDirect()
        val existing = all.find { it.insightId == insightId }
        val seriesList = postHogDao.getAllInsightsFlow().first().find { it.id == insightId }?.let {
            parseRepositoryDataJson(it.dataJson)
        } ?: emptyList()
        val lastVal = extractMetricValue(seriesList)

        if (existing != null) {
            val updated = existing.copy(
                threshold = threshold,
                isActive = isActive,
                currentValue = lastVal
            )
            val list = all.map { if (it.insightId == insightId) updated else it }
            postHogDao.clearAlerts()
            postHogDao.insertAlerts(list)
            evaluateAlertsDirect(listOf(updated))
        } else {
            val randId = kotlin.random.Random.nextInt(100000, 999999)
            val newAlert = AlertEntity(
                id = randId,
                name = "Threshold Alert: $insightName",
                insightId = insightId,
                insightName = insightName,
                metric = "Value Count",
                condition = "ABOVE",
                threshold = threshold,
                currentValue = lastVal,
                isActive = isActive,
                isTriggered = false,
                status = "OK"
            )
            postHogDao.insertAlerts(listOf(newAlert))
            evaluateAlertsDirect(listOf(newAlert))
        }
    }

    suspend fun syncRemoteData(forceRefresh: Boolean = false) =
        withContext(Dispatchers.IO) {
            val activeSession = _session.value ?: return@withContext
            if (activeSession.isDemoMode) {
                refreshDemoMetrics()
                return@withContext
            }
            if (activeSession.personalApiKey.isBlank() || activeSession.projectId.isBlank()) {
                if (BuildConfig.DEBUG) Log.e("PostHogRepository", "No credentials available for remote sync.")
                return@withContext
            }
            syncWithCredentials(
                hostUrl = activeSession.hostUrl,
                apiKey = activeSession.personalApiKey,
                projectId = activeSession.projectId,
                forceRefresh = forceRefresh
            )
        }

    private suspend fun syncWithCredentials(hostUrl: String, apiKey: String, projectId: String, forceRefresh: Boolean = false) {
        val sanitizedUrl = if (hostUrl.endsWith("/")) hostUrl else "$hostUrl/"
        try {
            val api = PostHogClient.createService(sanitizedUrl)
            val authHeader = "Bearer $apiKey"
            // refresh=true makes PostHog SYNCHRONOUSLY recompute every insight before responding,
            // which is the main reason loads were slow. Only pay that cost on an explicit manual
            // refresh; otherwise request cached server results ("false") for a fast load.
            val refreshParam = if (forceRefresh) "true" else "false"

            // Get existing dashboards to preserve their pinned state
            val existingDashboards = try { postHogDao.getAllDashboardsDirect() } catch (e: Exception) { emptyList() }
            val pinnedMap = existingDashboards.associate { it.id to it.isPinned }

            // 1. Fetch the (fast) dashboard list and persist immediately so the UI can render
            //    dashboard cards right away while the heavier insight data streams in behind it.
            val dashboardsEntities = try {
                val dashboardsResponse = api.getDashboards(
                    projectId = projectId,
                    authHeader = authHeader
                )
                val mapped = dashboardsResponse.results.map {
                    DashboardEntity(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        createdAt = it.createdAt,
                        isPinned = pinnedMap[it.id] ?: false
                    )
                }
                postHogDao.insertDashboards(mapped)
                mapped
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Failed to fetch dashboards from remote", e)
                if (e is retrofit2.HttpException) {
                    // Rethrow HTTP 401/403/404 errors as they are critical authentication signals!
                    throw e
                }
                emptyList()
            }

            // 2. Fetch each dashboard's insights CONCURRENTLY (bounded) instead of one-by-one.
            //    This turns the total wait from the sum of every dashboard's recompute time into
            //    roughly the slowest single dashboard. A wall-clock budget guarantees the refresh
            //    spinner can never hang for minutes — whatever finished in time is kept.
            val insightsEntitiesMap = ConcurrentHashMap<Int, InsightEntity>()
            val gate = Semaphore(SYNC_DETAIL_CONCURRENCY)
            withTimeoutOrNull(SYNC_TIME_BUDGET_MS) {
                coroutineScope {
                    dashboardsEntities.map { dashboard ->
                        async {
                            gate.withPermit {
                                fetchDashboardInsightsInto(
                                    api, projectId, authHeader, dashboard, refreshParam, insightsEntitiesMap
                                )
                            }
                        }
                    }.awaitAll()
                }
            }

            // 3. Also fetch general list of insights in case there are loose/non-dashboard insights.
            //    Always request cached results here ("false"): the per-dashboard calls above already
            //    refreshed the on-dashboard insights, so forcing a second full-project recompute was
            //    pure wasted time.
            var generalFetchSuccess = false
            try {
                val generalResponse = api.getInsights(
                    projectId = projectId,
                    authHeader = authHeader,
                    refresh = "false"
                )
                generalFetchSuccess = true
                for (insight in generalResponse.results) {
                    try {
                        val entity = mapRemoteInsightToEntity(insight)
                        val existing = insightsEntitiesMap[entity.id]
                        if (existing == null) {
                            insightsEntitiesMap[entity.id] = entity
                        } else if (existing.dashboardId == null && entity.dashboardId != null) {
                            insightsEntitiesMap[entity.id] = entity
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Failed parsing general list insight ${insight.id}", e)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Failed to fetch general insights list", e)
                if (e is retrofit2.HttpException) {
                    // Rethrow HTTP 401/403/404 signals if we didn't succeed with dashboards
                    if (dashboardsEntities.isEmpty()) {
                        throw e
                    }
                }
            }

            // If we fetched absolutely nothing and both remote scopes failed, throw an explanatory signal
            if (dashboardsEntities.isEmpty() && !generalFetchSuccess && insightsEntitiesMap.isEmpty()) {
                throw Exception("Could not fetch dashboards or insights lists. Confirm network address & api token.")
            }

            val allRealInsights = insightsEntitiesMap.values.toList()
            postHogDao.insertInsights(allRealInsights)

            // Keep current values updated on existing configured alerts if any
            val existingAlerts = postHogDao.getAllAlertsDirect()
            if (existingAlerts.isNotEmpty() && allRealInsights.isNotEmpty()) {
                val updatedRealAlerts = existingAlerts.map { alert ->
                    val matchingInsight = allRealInsights.find { it.id == alert.insightId }
                    if (matchingInsight != null) {
                        val seriesList = parseRepositoryDataJson(matchingInsight.dataJson)
                        val lastVal = extractMetricValue(seriesList)
                        alert.copy(currentValue = lastVal)
                    } else {
                        alert
                    }
                }
                postHogDao.insertAlerts(updatedRealAlerts)
            }

            // 4. Evaluate Alerts
            val currentAlerts = postHogDao.getAllAlertsDirect()
            evaluateAlertsDirect(currentAlerts)

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Error syncing remote PostHog data with credentials", e)
            throw e
        }
    }

    /**
     * Fetches a single dashboard's insights (from tiles/items, with a filtered-listing fallback)
     * and writes them into [target]. Runs per-dashboard so calls can be parallelized safely;
     * [target] must be a thread-safe map.
     */
    private suspend fun fetchDashboardInsightsInto(
        api: com.example.data.api.PostHogApiService,
        projectId: String,
        authHeader: String,
        dashboard: DashboardEntity,
        refreshParam: String,
        target: ConcurrentHashMap<Int, InsightEntity>
    ) {
        try {
            val fullDashboard = api.getDashboardDetail(
                projectId = projectId,
                id = dashboard.id,
                authHeader = authHeader,
                refresh = refreshParam
            )

            // Parse insights from tiles
            val tilesList = fullDashboard.tiles as? List<*>
            tilesList?.forEach { tile ->
                if (tile is Map<*, *>) {
                    val insightMap = tile["insight"] as? Map<*, *>
                    if (insightMap != null) {
                        val entity = parseMapToInsightEntity(insightMap, overrideDashboardId = dashboard.id)
                        if (entity != null) {
                            target[entity.id] = entity
                        }
                    }
                }
            }

            // Parse insights from items
            val itemsList = fullDashboard.items as? List<*>
            itemsList?.forEach { item ->
                if (item is Map<*, *>) {
                    val entity = parseMapToInsightEntity(item, overrideDashboardId = dashboard.id)
                    if (entity != null) {
                        target[entity.id] = entity
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Failed syncing dashboard details for ${dashboard.id}", e)

            // Fallback to fetch insights listing filtered by dashboard ID
            try {
                val response = api.getInsights(
                    projectId = projectId,
                    authHeader = authHeader,
                    dashboardId = dashboard.id,
                    refresh = refreshParam
                )
                for (insight in response.results) {
                    try {
                        val entity = mapRemoteInsightToEntity(insight, overrideDashboardId = dashboard.id)
                        target[entity.id] = entity
                    } catch (exInside: Exception) {
                        if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Failed fallback insight parsing: ${insight.id}", exInside)
                    }
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Fallback insights fetch also failed for dashboard: ${dashboard.id}", ex)
            }
        }
    }

    suspend fun toggleDashboardPin(dashboardId: Int) {
        val dashboards = postHogDao.getAllDashboardsDirect()
        val target = dashboards.find { it.id == dashboardId } ?: return
        val updated = target.copy(isPinned = !target.isPinned)
        postHogDao.insertDashboards(listOf(updated))
    }

    private fun mapRemoteInsightToEntity(insight: com.example.data.api.RemoteInsight, overrideDashboardId: Int? = null): InsightEntity {
        val seriesList = mutableListOf<com.example.data.api.RemoteInsightSeries>()
        val res = insight.result
        
        if (res is List<*>) {
            for (item in res) {
                if (item is Map<*, *>) {
                    val dataList = (item["data"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }
                    val labelsList = (item["labels"] as? List<*>)?.mapNotNull { it?.toString() }
                    val labelStr = item["label"]?.toString()
                        ?: item["breakdown_value"]?.toString()
                        ?: item["metric"]?.toString()
                        ?: (item["action"] as? Map<*, *>)?.get("name")?.toString()
                        ?: (item["action"] as? Map<*, *>)?.get("id")?.toString()
                    if (dataList != null || labelsList != null) {
                        seriesList.add(com.example.data.api.RemoteInsightSeries(dataList, labelsList, labelStr))
                    }
                }
            }
        } else if (res is Map<*, *>) {
            val dataList = (res["data"] as? List<*>)?.mapNotNull { (res as? Map<*, *>)?.get("data") as? List<*> ?: (res["data"] as? List<*>) }?.mapNotNull { (it as? Number)?.toDouble() }
                ?: (res["data"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }
            val labelsList = (res["labels"] as? List<*>)?.mapNotNull { it?.toString() }
            val labelStr = res["label"]?.toString()
                ?: res["breakdown_value"]?.toString()
                ?: res["metric"]?.toString()
                ?: (res["action"] as? Map<*, *>)?.get("name")?.toString()
                ?: (res["action"] as? Map<*, *>)?.get("id")?.toString()
            if (dataList != null || labelsList != null) {
                seriesList.add(com.example.data.api.RemoteInsightSeries(dataList, labelsList, labelStr))
            } else {
                val nestedResults = (res["results"] as? List<*>)
                if (nestedResults != null) {
                    for (item in nestedResults) {
                        if (item is Map<*, *>) {
                            val dList = (item["data"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }
                            val lList = (item["labels"] as? List<*>)?.mapNotNull { it?.toString() }
                            val lbl = item["label"]?.toString()
                                ?: item["breakdown_value"]?.toString()
                                ?: item["metric"]?.toString()
                                ?: (item["action"] as? Map<*, *>)?.get("name")?.toString()
                                ?: (item["action"] as? Map<*, *>)?.get("id")?.toString()
                            if (dList != null || lList != null) {
                                seriesList.add(com.example.data.api.RemoteInsightSeries(dList, lList, lbl))
                            }
                        }
                    }
                }
            }
        }

        // Parse standard lists with count metrics like Funnels
        if (seriesList.isEmpty() && res is List<*>) {
            val funnelLabels = mutableListOf<String>()
            val funnelData = mutableListOf<Double>()
            for (item in res) {
                if (item is Map<*, *>) {
                    val actionName = (item["action_name"] ?: item["name"] ?: item["label"] ?: item["step_name"])?.toString()
                    val count = (item["count"] ?: item["value"] ?: item["total"] ?: item["aggregated_value"]) as? Number
                    if (actionName != null && count != null) {
                        funnelLabels.add(actionName)
                        funnelData.add(count.toDouble())
                    }
                }
            }
            if (funnelLabels.isNotEmpty()) {
                seriesList.add(com.example.data.api.RemoteInsightSeries(funnelData, funnelLabels, "Funnel Steps"))
            }
        }

        val firstSeries = seriesList.firstOrNull()
        val dataPoints = firstSeries?.data ?: emptyList()
        val labels = seriesList.firstOrNull { !it.labels.isNullOrEmpty() }?.labels 
            ?: firstSeries?.labels 
            ?: emptyList()
        
        val displayType = insight.display ?: "ActionsLineGraph"
        
        val lastValue = if (seriesList.size == 1) {
            val lastD = dataPoints.lastOrNull()
            if (lastD != null) {
                if (lastD % 1.0 == 0.0) lastD.toInt().toString() else lastD.toString()
            } else {
                "No Data"
            }
        } else if (seriesList.size > 1) {
            val total = seriesList.sumOf { s -> s.data?.lastOrNull() ?: 0.0 }
            if (total % 1.0 == 0.0) {
                "${total.toInt()} (Total)"
            } else {
                "${String.format("%.1f", total)} (Total)"
            }
        } else {
            "No Data"
        }
        
        val dataJson = seriesList.mapNotNull { s ->
            val sData = s.data ?: return@mapNotNull null
            val sLabel = s.label ?: insight.name ?: "Metric"
            "$sLabel:${sData.joinToString(",")}"
        }.joinToString("|")

        val trend = if (dataPoints.size >= 2) {
            val pen = dataPoints[dataPoints.size - 2]
            val ultimate = dataPoints.last()
            if (ultimate > pen) "UP" else if (ultimate < pen) "DOWN" else "FLAT"
        } else "FLAT"

        val resolvedDashboardId = overrideDashboardId ?: insight.dashboard ?: insight.dashboards?.firstOrNull()

        return InsightEntity(
            id = insight.id,
            dashboardId = resolvedDashboardId,
            name = insight.name ?: "Unnamed Metric (${insight.id})",
            description = insight.description,
            lastValueString = lastValue,
            displayType = displayType,
            labelsJson = labels.joinToString(","),
            dataJson = dataJson,
            trendDirection = trend
        )
    }

    private fun parseMapToInsightEntity(map: Map<*, *>, overrideDashboardId: Int? = null): InsightEntity? {
        val id = (map["id"] as? Number)?.toInt() ?: return null
        val name = map["name"]?.toString()
        val description = map["description"]?.toString()
        val result = map["result"]
        val display = map["display"]?.toString()
        val dashboard = (map["dashboard"] as? Number)?.toInt()
        val dashboards = (map["dashboards"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
        
        val remoteInsight = com.example.data.api.RemoteInsight(
            id = id,
            name = name,
            description = description,
            result = result,
            display = display,
            dashboard = dashboard,
            dashboards = dashboards
        )
        return try {
            mapRemoteInsightToEntity(remoteInsight, overrideDashboardId)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("PostHogRepository", "Failed to map parsed insight map to entity for ID $id", e)
            null
        }
    }

    // Refresh metrics on demo mode simulating a ticking live server with alert checking!
    suspend fun refreshDemoMetrics() {
        val currentInsights = postHogDao.getAllInsightsFlow().first()
        val updatedInsights = currentInsights.map { insight ->
            val dataJson = insight.dataJson
            if (dataJson.isBlank()) return@map insight

            val seriesList = parseRepositoryDataJson(dataJson)
            if (seriesList.isEmpty()) return@map insight

            val updatedSeriesList = seriesList.map { series ->
                val dataPoints = series.data.toMutableList()
                if (dataPoints.isNotEmpty()) {
                    val lastVal = dataPoints.last()
                    var volatility = lastVal * 0.05
                    if (volatility == 0.0) volatility = 10.0
                    
                    var change = Random.nextDouble(-volatility, volatility)
                    
                    // Specific spiker for error tracking
                    if (insight.id == 203 && Random.nextInt(0, 100) < 15) {
                        change = Random.nextDouble(5.0, 30.0)
                    }
                    
                    if (insight.id == 205) {
                        val dropChance = Random.nextInt(0, 100) < 12
                        val newVal = if (dropChance) Random.nextDouble(99.6, 99.78) else Random.nextDouble(99.82, 99.95)
                        dataPoints.removeAt(0)
                        dataPoints.add(newVal)
                    } else {
                        var newVal = lastVal + change
                        if (newVal < 0) newVal = 0.0
                        dataPoints.removeAt(0)
                        dataPoints.add(newVal)
                    }
                }
                series.copy(data = dataPoints)
            }

            val newDataJson = if (updatedSeriesList.size == 1 && updatedSeriesList.first().name == "Metric" && !dataJson.contains(":")) {
                updatedSeriesList.first().data.joinToString(",")
            } else {
                updatedSeriesList.joinToString("|") { "${it.name}:${it.data.joinToString(",")}" }
            }

            val lastOldVal = seriesList.firstOrNull()?.data?.lastOrNull() ?: 0.0
            val lastNewVal = updatedSeriesList.firstOrNull()?.data?.lastOrNull() ?: 0.0
            val updatedDirection = if (lastNewVal > lastOldVal) "UP" else if (lastNewVal < lastOldVal) "DOWN" else "FLAT"

            val lastValueStr = when (insight.id) {
                201 -> {
                    val lastVal = updatedSeriesList.firstOrNull()?.data?.lastOrNull() ?: 0.0
                    "${String.format("%.1f", lastVal)}%"
                }
                202 -> {
                    val total = updatedSeriesList.sumOf { it.data.lastOrNull() ?: 0.0 }
                    "${String.format("%,.0f", total)} DAU"
                }
                203 -> {
                    val total = updatedSeriesList.sumOf { it.data.lastOrNull() ?: 0.0 }
                    "${String.format("%.0f", total)} Errors"
                }
                204 -> {
                    val avg = updatedSeriesList.map { it.data.lastOrNull() ?: 0.0 }.average()
                    "${String.format("%.1f", avg)}m"
                }
                205 -> {
                    val avg = updatedSeriesList.map { it.data.lastOrNull() ?: 0.0 }.average()
                    "${String.format("%.2f", avg)}%"
                }
                206 -> {
                    val total = updatedSeriesList.sumOf { it.data.lastOrNull() ?: 0.0 }
                    "$${String.format("%,.0f", total)}"
                }
                207 -> {
                    val avg = updatedSeriesList.map { it.data.lastOrNull() ?: 0.0 }.average()
                    "${String.format("%.1f", avg)}%"
                }
                else -> {
                    val firstVal = updatedSeriesList.firstOrNull()?.data?.lastOrNull() ?: 0.0
                    String.format("%.2f", firstVal)
                }
            }

            insight.copy(
                lastValueString = lastValueStr,
                labelsJson = generatePast7DaysLabels(),
                dataJson = newDataJson,
                trendDirection = updatedDirection
            )
        }
        postHogDao.insertInsights(updatedInsights)

        // Review alerts
        val currentAlerts = postHogDao.getAllAlertsDirect()
        evaluateAlertsDirect(currentAlerts)
    }

    suspend fun forceTriggerAlertSimulation(alertId: Int? = null) {
        // Find Alert by ID or default to 302 or first custom alert
        val allAlerts = postHogDao.getAllAlertsDirect()
        val apiAlert = if (alertId != null) {
            allAlerts.find { it.id == alertId } ?: allAlerts.find { it.insightId == alertId }
        } else {
            allAlerts.find { it.id == 302 } ?: allAlerts.firstOrNull()
        }
        if (apiAlert != null) {
            val triggeredAlert = apiAlert.copy(
                currentValue = apiAlert.threshold + 15.0,
                isTriggered = true,
                status = "CRITICAL",
                lastTriggeredAt = System.currentTimeMillis()
            )
            val updatedAlerts = allAlerts.map { if (it.id == triggeredAlert.id) triggeredAlert else it }
            postHogDao.clearAlerts()
            postHogDao.insertAlerts(updatedAlerts)

            // Trigger notification
            val notificationTitle = "🚨 PostHog Threshold Breached"
            val notificationMessage = "Metric '${triggeredAlert.insightName}' crossed threshold! Live value is ${triggeredAlert.currentValue} (Limit set at ${triggeredAlert.threshold})."
            
            val log = NotificationLogEntity(
                title = "Critical Alert: ${triggeredAlert.insightName}",
                message = notificationMessage,
                type = "CRITICAL",
                alertId = triggeredAlert.id
            )
            postHogDao.insertNotificationLog(log)
            
            if (!triggeredAlert.isMuted) {
                notificationHelper.showAlertNotification(
                    alertId = triggeredAlert.id,
                    alertName = triggeredAlert.name,
                    title = notificationTitle,
                    message = notificationMessage
                )
            }
            if (BuildConfig.DEBUG) Log.d("PostHogRepository", "Dispatched artificial alert simulation")
        }
    }

    internal suspend fun evaluateAlertsDirect(alertsList: List<AlertEntity>) {
        val currentInsights = postHogDao.getAllInsightsFlow().first()
        val evaluatedAlerts = alertsList.map { alert ->
            val matchingInsight = currentInsights.find { it.id == alert.insightId }
            if (matchingInsight != null) {
                val seriesList = parseRepositoryDataJson(matchingInsight.dataJson)
                val currentMetricValue = extractMetricValue(seriesList)
                
                val conditionBreached = evaluateCondition(alert.condition, currentMetricValue, alert.threshold)

                if (conditionBreached) {
                    // Alert breached! Check if it was already triggered
                    if (!alert.isTriggered) {
                        // Create Notification
                        val isCritical = alert.status == "CRITICAL" || alert.name.contains("Severe", ignoreCase = true)
                        val badgeSymbol = if (isCritical) "🚨" else "⚠️"
                        val prefix = if (isCritical) "CRITICAL Limit crossed!" else "WARNING Threshold met!"
                        
                        val title = "$badgeSymbol ${alert.name}"
                        val msg = "PostHog Metric Alert '${alert.name}' triggered on '${alert.insightName}'. The current live value ${String.format("%.2f", currentMetricValue)} breached the set threshold of ${alert.threshold}."
                        
                        // Insert notification log
                        val log = NotificationLogEntity(
                            title = title,
                            message = msg,
                            type = if (isCritical) "CRITICAL" else "WARNING",
                            alertId = alert.id
                        )
                        postHogDao.insertNotificationLog(log)

                        // Trigger native status notification
                        if (!alert.isMuted) {
                            notificationHelper.showAlertNotification(
                                alertId = alert.id,
                                alertName = alert.name,
                                title = "$badgeSymbol $prefix",
                                message = msg
                            )
                        }
                    }

                    alert.copy(
                        currentValue = currentMetricValue,
                        isTriggered = true,
                        status = if (alert.name.contains("Severe", ignoreCase = true)) "CRITICAL" else "WARNING",
                        lastTriggeredAt = if (!alert.isTriggered) System.currentTimeMillis() else alert.lastTriggeredAt
                    )
                } else {
                    // Alert fine / resolved
                    alert.copy(
                        currentValue = currentMetricValue,
                        isTriggered = false,
                        status = "OK"
                    )
                }
            } else {
                alert
            }
        }
        postHogDao.insertAlerts(evaluatedAlerts)
    }

    suspend fun clearAllNotifications() = postHogDao.clearAllNotifications()
    suspend fun markAllNotificationsAsRead() = postHogDao.markAllNotificationsAsRead()

    companion object {
        // Max number of dashboard-detail requests in flight at once. Keeps loads fast while
        // staying gentle enough to avoid tripping PostHog's rate limits.
        private const val SYNC_DETAIL_CONCURRENCY = 5
        // Hard wall-clock budget for the per-dashboard insight fetch phase. Guarantees the
        // refresh button never spins longer than this; partial results are still persisted.
        private const val SYNC_TIME_BUDGET_MS = 25_000L
    }
}
