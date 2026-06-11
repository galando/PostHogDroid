package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.MyApplication

class HogSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as MyApplication
        val repository = application.repository

        return try {
            repository.initDefaultSettingsAndDemoData()

            val activeSession = repository.getActiveSession()
            if (activeSession != null) {
                if (BuildConfig.DEBUG) Log.d("HogSyncWorker", "Performing background sync tick...")
                if (activeSession.isDemoMode) {
                    repository.refreshDemoMetrics()
                } else {
                    repository.syncRemoteData(forceRefresh = true)
                }
                // Send daily digest if 24h have passed since the last one
                repository.sendDailyDigestIfNeeded()
                if (BuildConfig.DEBUG) Log.d("HogSyncWorker", "Background sync finished successfully.")
            } else {
                if (BuildConfig.DEBUG) Log.d("HogSyncWorker", "No active session, skipping sync.")
            }
            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("HogSyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "hog_periodic_sync"
    }
}
