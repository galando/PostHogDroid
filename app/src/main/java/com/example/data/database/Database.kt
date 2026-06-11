package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ------------------ ENTITIES ------------------

@Entity(tableName = "posthog_settings")
data class PostHogSettings(
    @PrimaryKey val id: Int = 1,
    val hostUrl: String,
    val projectId: String,
    val useDemoMode: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val lastDigestSentAt: Long = 0L
)

@Entity(tableName = "dashboards")
data class DashboardEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val description: String?,
    val createdAt: String?,
    val isPinned: Boolean = false
)

@Entity(tableName = "insights")
data class InsightEntity(
    @PrimaryKey val id: Int,
    val dashboardId: Int?,
    val name: String,
    val description: String?,
    val lastValueString: String,
    val displayType: String, // "ActionsLineGraph", "ActionsBarValue", "ActionsPie", "ActionsTable"
    val labelsJson: String,  // comma-separated or json array
    val dataJson: String,    // comma-separated or json array
    val trendDirection: String // "UP", "DOWN", "FLAT"
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val insightId: Int,
    val insightName: String,
    val metric: String,
    val condition: String, // "ABOVE", "BELOW"
    val threshold: Double,
    val currentValue: Double,
    val isActive: Boolean,
    val isTriggered: Boolean,
    val status: String, // "OK", "WARNING", "CRITICAL"
    val lastTriggeredAt: Long = 0,
    val isMuted: Boolean = false,
    val alertType: String = "THRESHOLD",   // "THRESHOLD" or "PCT_CHANGE"
    val pctChangeThreshold: Double = 20.0  // % magnitude that triggers PCT_CHANGE alerts
)

@Entity(tableName = "notification_logs")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "WARNING", "CRITICAL", "INFO"
    val alertId: Int?,
    val isRead: Boolean = false
)

// ------------------ DAOS ------------------

@Dao
interface PostHogDao {
    // Settings
    @Query("SELECT * FROM posthog_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<PostHogSettings?>

    @Query("SELECT * FROM posthog_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): PostHogSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: PostHogSettings)

    // Dashboards
    @Query("SELECT * FROM dashboards ORDER BY isPinned DESC, name ASC")
    fun getAllDashboardsFlow(): Flow<List<DashboardEntity>>

    @Query("SELECT * FROM dashboards")
    suspend fun getAllDashboardsDirect(): List<DashboardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDashboards(dashboards: List<DashboardEntity>)

    @Query("DELETE FROM dashboards")
    suspend fun clearDashboards()

    // Insights
    @Query("SELECT * FROM insights WHERE dashboardId = :dashboardId")
    fun getInsightsForDashboardFlow(dashboardId: Int): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights")
    fun getAllInsightsFlow(): Flow<List<InsightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsights(insights: List<InsightEntity>)

    @Query("DELETE FROM insights")
    suspend fun clearInsights()

    // Alerts
    @Query("SELECT * FROM alerts ORDER BY isActive DESC, status ASC")
    fun getAllAlertsFlow(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts")
    suspend fun getAllAlertsDirect(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<AlertEntity>)

    @Update
    suspend fun updateAlert(alert: AlertEntity)

    @Query("DELETE FROM alerts")
    suspend fun clearAlerts()

    // Notification Logs
    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    fun getAllNotificationLogsFlow(): Flow<List<NotificationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationLog(log: NotificationLogEntity)

    @Query("UPDATE notification_logs SET isRead = 1")
    suspend fun markAllNotificationsAsRead()

    @Query("DELETE FROM notification_logs")
    suspend fun clearAllNotifications()
}

// ------------------ DATABASE ------------------

@Database(
    entities = [
        PostHogSettings::class,
        DashboardEntity::class,
        InsightEntity::class,
        AlertEntity::class,
        NotificationLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postHogDao(): PostHogDao
}
