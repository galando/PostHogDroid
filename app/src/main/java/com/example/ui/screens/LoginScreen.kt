package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodel.PostHogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: PostHogViewModel) {
    var hostUrl by remember { mutableStateOf("https://app.posthog.com") }
    var apiKey by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    val storedSettings by viewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(storedSettings) {
        val st = storedSettings
        if (st != null) {
            if (st.hostUrl.isNotBlank()) hostUrl = st.hostUrl
            if (st.projectId.isNotBlank()) projectId = st.projectId
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Branding
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.ic_posthog_custom_logo),
                    contentDescription = "PostHog and Android logo",
                    modifier = Modifier.width(190.dp).height(105.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, PostHogOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Text(text = "Quillboard", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp), color = PostHogOrange)
                Text(text = "Mobile dashboards for PostHog", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connection form
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(text = "Connect Private Workspace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(text = "Requires active Personal API authorization key tokens of your remote PostHog workspace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Text("Select Region Host:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val usCloudHost = "https://app.posthog.com"
                        val euCloudHost = "https://eu.posthog.com"

                        FilledTonalButton(onClick = { hostUrl = usCloudHost }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (hostUrl == usCloudHost) PostHogOrangeLight else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), contentColor = if (hostUrl == usCloudHost) PostHogOrange else MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("US Cloud", fontSize = 11.sp, maxLines = 1) }
                        FilledTonalButton(onClick = { hostUrl = euCloudHost }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (hostUrl == euCloudHost) PostHogOrangeLight else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), contentColor = if (hostUrl == euCloudHost) PostHogOrange else MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("EU Cloud", fontSize = 11.sp, maxLines = 1) }
                        FilledTonalButton(onClick = { if (hostUrl == usCloudHost || hostUrl == euCloudHost) hostUrl = "https://" }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (hostUrl != usCloudHost && hostUrl != euCloudHost) PostHogOrangeLight else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), contentColor = if (hostUrl != usCloudHost && hostUrl != euCloudHost) PostHogOrange else MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Self Host", fontSize = 11.sp, maxLines = 1) }
                    }

                    OutlinedTextField(value = hostUrl, onValueChange = { hostUrl = it }, label = { Text("Server Instance Address") }, modifier = Modifier.fillMaxWidth().testTag("host_url_input"), shape = RoundedCornerShape(8.dp), singleLine = true)
                    OutlinedTextField(value = projectId, onValueChange = { projectId = it }, label = { Text("Project / Team ID") }, placeholder = { Text("E.g. 12345") }, modifier = Modifier.fillMaxWidth().testTag("project_id_input"), shape = RoundedCornerShape(8.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it }, label = { Text("Personal API Key") }, placeholder = { Text("phx_...") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) { Icon(imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = "Toggle visibility") } },
                        modifier = Modifier.fillMaxWidth().testTag("api_key_input"), shape = RoundedCornerShape(8.dp), singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (hostUrl.isBlank() || apiKey.isBlank() || projectId.isBlank()) { loginError = "Please complete all server configuration parameters."; return@Button }
                                isLoggingIn = true; loginError = null
                                viewModel.login(hostUrl = hostUrl, apiKey = apiKey, projectId = projectId, isDemoMode = false, email = "Workspace Link", onResult = { success, errorMsg -> isLoggingIn = false; if (!success) loginError = errorMsg })
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PostHogOrange), shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f).height(48.dp).testTag("secure_api_login_button")
                        ) {
                            if (isLoggingIn) { CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp) }
                            else { Icon(imageVector = Icons.Default.VpnKey, contentDescription = "Secure connect", modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Link Workspace", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        }
                        OutlinedButton(
                            onClick = { viewModel.login(hostUrl = "https://app.posthog.com", apiKey = "DemoModeMockKey", projectId = "DemoProject", isDemoMode = true, email = "Demo User", onResult = { _, _ -> }) },
                            border = BorderStroke(1.5.dp, PostHogOrange), colors = ButtonDefaults.outlinedButtonColors(contentColor = PostHogOrange), shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("demo_mode_button")
                        ) { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Demo Sandbox", modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Demo Sandbox", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    }
                }
            }

            // Error reporting
            loginError?.let { errorMsg ->
                Card(colors = CardDefaults.cardColors(containerColor = PostHogRed.copy(alpha = 0.08f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, PostHogRed.copy(alpha = 0.35f), RoundedCornerShape(8.dp))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Error detail", tint = PostHogRed)
                        Text(text = errorMsg, style = MaterialTheme.typography.bodySmall, color = PostHogRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // API Key helper
            val uriHandler = LocalUriHandler.current
            val keyGenerationUrl = remember(hostUrl, projectId) {
                var base = hostUrl.trim().removeSuffix("/")
                if (base.isBlank() || base == "https:/") base = "https://us.posthog.com"
                else if (base == "https://app.posthog.com") base = "https://us.posthog.com"
                if (projectId.trim().isNotBlank()) "$base/project/${projectId.trim()}/settings/user-api-keys" else "$base/settings/project-personal-api-keys"
            }

            Card(colors = CardDefaults.cardColors(containerColor = PostHogOrange.copy(alpha = 0.04f)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.5.dp, PostHogOrange.copy(alpha = 0.4f), RoundedCornerShape(12.dp))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.HelpOutline, contentDescription = "Key Generator Helper", tint = PostHogOrange, modifier = Modifier.size(24.dp))
                        Text(text = "1-Click API Key Generator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(text = "Follow these simple steps to generate a secure read-only token to load your metrics:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { uriHandler.openUri(keyGenerationUrl) }, colors = ButtonDefaults.buttonColors(containerColor = PostHogOrange), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(40.dp)) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Open PostHog Key Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth().background(PostHogOrange.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Required Scopes (Read-only):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.background(PostHogOrange.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("dashboard:read", fontSize = 10.sp, color = PostHogOrange, fontWeight = FontWeight.Bold) }
                            Box(modifier = Modifier.background(PostHogOrange.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("insight:read", fontSize = 10.sp, color = PostHogOrange, fontWeight = FontWeight.Bold) }
                            Box(modifier = Modifier.background(PostHogOrange.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("query:read", fontSize = 10.sp, color = PostHogOrange, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text(text = "1. Click the button above to login and jump straight to your Keys page.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "2. Press the '+ Create personal API key' button.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "3. Check only Dashboard (Read Only) and Insight (Read Only) as indicated above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "4. Copy the resulting 'phx_...' token and paste it in the field above!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Trust panel
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = "Security Shield", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                        Text(text = "Privacy & Trust Guarantee", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(text = "Your workspace endpoints demand high security. Here is how we enforce complete safety:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top) { Text("Encrypted Key Storage: Your API key is stored on-device using Android Keystore (EncryptedSharedPreferences). No plaintext storage, no external servers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(verticalAlignment = Alignment.Top) { Text("Direct-to-PostHog Only: Data is transmitted only to your configured PostHog host over TLS. No third-party proxies, analytics, or telemetry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(verticalAlignment = Alignment.Top) { Text("100% On-Device: Alerts are evaluated locally. No backend, no cloud logic. Open source with reproducible builds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(text = "Create a read-only key at PostHog > Settings > Personal API keys", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PostHogOrange)
                        Text(text = "Required scopes: dashboard:read, insight:read, query:read", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "Not affiliated with PostHog Inc.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
