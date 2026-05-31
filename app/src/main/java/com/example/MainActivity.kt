package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.MainAppScaffold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PostHogViewModel
import com.example.ui.viewmodel.PostHogViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup full Edge-to-Edge display
        enableEdgeToEdge()
        
        // Instantiate the centralized PostHog viewModel via lazy provider factory
        val app = application as MyApplication
        val viewModel = ViewModelProvider(
            this, 
            PostHogViewModelFactory(app.repository)
        )[PostHogViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }
}
