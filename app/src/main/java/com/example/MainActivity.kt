package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.screens.BiometricGate
import com.example.ui.screens.MainAppScaffold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PostHogViewModel
import com.example.ui.viewmodel.PostHogViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MyApplication
        val viewModel = ViewModelProvider(
            this,
            PostHogViewModelFactory(app.repository)
        )[PostHogViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val needsBiometric = settings?.biometricLockEnabled == true
                var unlocked by rememberSaveable { mutableStateOf(false) }

                when {
                    settings == null -> Unit // brief blank frame while settings loads from DB
                    needsBiometric && !unlocked -> BiometricGate(onUnlocked = { unlocked = true })
                    else -> MainAppScaffold(viewModel = viewModel)
                }
            }
        }
    }
}
