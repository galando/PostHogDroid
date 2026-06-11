package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.ui.viewmodel.PostHogViewModel

@Composable
fun SettingsScreen(viewModel: PostHogViewModel, onNavigateToAbout: () -> Unit = {}) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val hostUrl = session?.hostUrl ?: "https://app.posthog.com"
    val projectId = session?.projectId ?: "Simulated"
    val useDemoMode = session?.isDemoMode ?: true
    val email = session?.email ?: ""

    val biometricEnabled = settings?.biometricLockEnabled ?: false
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(text = "Quillboard", style = MaterialTheme.typography.bodyMedium, color = HogPurple, fontWeight = FontWeight.Bold)
                Text(text = "Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            }

            Text(text = "Active session configurations and developer controls.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Active Session Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Connection Status:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Active Session", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Session Type:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text(text = if (useDemoMode) "Demo Sandbox Mock" else "Remote API Link", fontWeight = FontWeight.SemiBold, color = HogPurple, style = MaterialTheme.typography.bodySmall)
                    }

                    if (email.isNotBlank()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Logged In User:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Text(email, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Server Host URL:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text(hostUrl, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Project ID:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text(projectId, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Secret Storage:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text("Encrypted (Android Keystore)", fontWeight = FontWeight.Black, color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (useDemoMode) {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, HogPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = HogPurpleSoft.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Diagnostics & Sandbox Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HogPurple)
                        Text(text = "Click below to trigger a mock API failure breach and test your status bar notification alarms immediately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { viewModel.triggerAlertSimulation() },
                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("simulate_alert_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = HogPurple, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DeveloperMode, contentDescription = "Developer Sandbox")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulate Threshold Alarm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = HogPurple, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Biometric App Lock", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Require fingerprint or PIN on open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { viewModel.setBiometricLock(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = HogPurple)
                    )
                }
            }

            OutlinedButton(
                onClick = onNavigateToAbout,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("about_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "About")
                Spacer(modifier = Modifier.width(8.dp))
                Text("About Quillboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button"),
                colors = ButtonDefaults.buttonColors(containerColor = HogRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Sign Out", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout & Clear Memory Session", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
