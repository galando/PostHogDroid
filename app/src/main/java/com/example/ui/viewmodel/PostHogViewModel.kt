package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AlertEntity
import com.example.data.database.DashboardEntity
import com.example.data.database.InsightEntity
import com.example.data.database.NotificationLogEntity
import com.example.data.database.PostHogSettings
import com.example.data.repository.PostHogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.data.repository.SessionCredentials

class PostHogViewModel(private val repository: PostHogRepository) : ViewModel() {

    val session: StateFlow<SessionCredentials?> = repository.session

    val settings: StateFlow<PostHogSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dashboards: StateFlow<List<DashboardEntity>> = repository.dashboards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val insights: StateFlow<List<InsightEntity>> = repository.allInsights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alerts: StateFlow<List<AlertEntity>> = repository.alerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationLogEntity>> = repository.notificationLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun updateSettings(hostUrl: String, apiKey: String, projectId: String, useDemoMode: Boolean) {
        viewModelScope.launch {
            try {
                repository.saveSettings(hostUrl, apiKey, projectId, useDemoMode)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Save failed: ${e.message}"
            }
        }
    }

    fun login(hostUrl: String, apiKey: String, projectId: String, isDemoMode: Boolean, email: String? = null, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null
            try {
                repository.login(hostUrl, apiKey, projectId, isDemoMode, email)
                _isSyncing.value = false
                onResult(true, null)
            } catch (e: Exception) {
                _isSyncing.value = false
                val errMsg = e.message ?: "Authentication error"
                _errorMessage.value = "Login failed: $errMsg"
                onResult(false, errMsg)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null
            try {
                repository.syncRemoteData(forceRefresh = true)
            } catch (e: Exception) {
                _errorMessage.value = "Sync Error: ${e.message}. Switch to Demo Mode if you are testing."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun toggleDashboardPin(dashboardId: Int) {
        viewModelScope.launch {
            repository.toggleDashboardPin(dashboardId)
        }
    }

    fun createAlert(name: String, insightId: Int, insightName: String, metric: String, condition: String, threshold: Double) {
        viewModelScope.launch {
            repository.createLocalAlert(name, insightId, insightName, metric, condition, threshold)
        }
    }

    fun saveAlertThreshold(insightId: Int, insightName: String, threshold: Double, isActive: Boolean) {
        viewModelScope.launch {
            repository.saveAlertThreshold(insightId, insightName, threshold, isActive)
        }
    }

    fun toggleMuteAlert(alertId: Int) {
        viewModelScope.launch {
            repository.toggleMuteAlert(alertId)
        }
    }

    fun deleteAlert(alertId: Int) {
        viewModelScope.launch {
            repository.deleteAlert(alertId)
        }
    }

    fun triggerAlertSimulation(alertId: Int? = null) {
        viewModelScope.launch {
            repository.forceTriggerAlertSimulation(alertId)
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    fun markNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}

class PostHogViewModelFactory(private val repository: PostHogRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PostHogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PostHogViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
