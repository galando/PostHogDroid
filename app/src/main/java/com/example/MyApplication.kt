package com.example

import android.app.Application
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.HogNotificationHelper
import com.example.data.HogSyncWorker
import com.example.data.database.AppDatabase
import com.example.data.repository.PostHogRepository
import com.example.data.repository.SecureKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MyApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "posthog_companion.db"
        ).fallbackToDestructiveMigration().build()
    }

    val notificationHelper: HogNotificationHelper by lazy {
        HogNotificationHelper(this)
    }

    val repository: PostHogRepository by lazy {
        PostHogRepository(database.postHogDao(), notificationHelper, SecureKeyStore(this))
    }

    override fun onCreate() {
        super.onCreate()

        // Schedule periodic background sync via WorkManager (15-minute interval)
        // try-catch guards against test environments where WorkManager isn't initialized
        try {
            val syncRequest = PeriodicWorkRequestBuilder<HogSyncWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                HogSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (_: IllegalStateException) {
            // WorkManager not initialized (e.g., Robolectric test environment)
        }

        // Asynchronous initialization of default settings structure
        appScope.launch {
            repository.initDefaultSettingsAndDemoData()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}
