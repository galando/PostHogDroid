package com.example.ui.screens

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.ui.viewmodel.PostHogViewModel
import androidx.compose.foundation.shape.CircleShape

data class NavigationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: PostHogViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    if (session == null) {
        LoginScreen(viewModel = viewModel)
    } else {
        val currentRoute = remember { mutableStateOf("dashboards") }
        val activeDashboardId = remember { mutableStateOf<Int?>(null) }
        val activeDashboardName = remember { mutableStateOf("") }

        val notifications by viewModel.notifications.collectAsStateWithLifecycle()
        val unreadNotificationsCount = remember(notifications) { notifications.filter { !it.isRead }.size }

        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

        LaunchedEffect(errorMessage) {
            errorMessage?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearErrorMessage()
            }
        }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) {
                val activity = context as? androidx.activity.ComponentActivity
                activity?.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1001)
            }
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    val navItems = listOf(
                        NavigationItem("dashboards", "Dashboards", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, 0),
                        NavigationItem("alerts", "Rules", Icons.Filled.NotificationsActive, Icons.Outlined.NotificationsActive, 0),
                        NavigationItem("notifications", "Inbox", Icons.Filled.Inbox, Icons.Outlined.Inbox, unreadNotificationsCount),
                        NavigationItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, 0)
                    )

                    navItems.forEach { item ->
                        val isSelected = currentRoute.value == item.route ||
                            (item.route == "dashboards" && currentRoute.value == "dashboard_detail")

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentRoute.value = item.route },
                            icon = {
                                if (item.badgeCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = HogRed) {
                                                Text(item.badgeCount.toString(), color = Color.White)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                }
                            },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HogPurple,
                                selectedTextColor = HogPurple,
                                indicatorColor = HogPurpleSoft
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentRoute.value) {
                    "dashboards" -> DashboardsListScreen(
                        viewModel = viewModel,
                        onNavigateToDetails = { id, name ->
                            activeDashboardId.value = id
                            activeDashboardName.value = name
                            currentRoute.value = "dashboard_detail"
                        }
                    )
                    "dashboard_detail" -> DashboardDetailsScreen(
                        viewModel = viewModel,
                        dashboardId = activeDashboardId.value ?: 0,
                        dashboardName = activeDashboardName.value,
                        onBack = { currentRoute.value = "dashboards" }
                    )
                    "alerts" -> AlertsScreen(viewModel = viewModel)
                    "notifications" -> NotificationsInboxScreen(viewModel = viewModel)
                    "settings" -> SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToAbout = { currentRoute.value = "about" }
                    )
                    "about" -> AboutScreen(
                        onBack = { currentRoute.value = "settings" }
                    )
                }
            }
        }
    }
}

@Composable
fun BiometricGate(onUnlocked: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val allowedAuth = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuth = BiometricManager.from(context).canAuthenticate(allowedAuth)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnlocked()
            return@LaunchedEffect
        }
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Device credential not set up or user cancelled — fail open so app stays usable
                    onUnlocked()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Quillboard")
            .setSubtitle("Verify your identity to view your dashboards")
            .setAllowedAuthenticators(allowedAuth)
            .build()
        prompt.authenticate(info)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(72.dp), tint = HogPurple)
            Text("Quillboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = HogPurple)
            Text("Authenticate to continue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
