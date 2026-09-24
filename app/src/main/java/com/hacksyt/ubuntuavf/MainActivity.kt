package com.hacksyt.ubuntuavf

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hacksyt.ubuntuavf.ui.UbuntuViewModel
import com.hacksyt.ubuntuavf.ui.dashboard.VmDashboardScreen
import com.hacksyt.ubuntuavf.ui.settings.VmSettingsScreen
import com.hacksyt.ubuntuavf.ui.terminal.UbuntuTerminalScreen
import com.hacksyt.ubuntuavf.ui.theme.UbuntuAVFTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            UbuntuAVFTheme {
                UbuntuAppMainScreen()
            }
        }
    }
}

@Composable
fun UbuntuAppMainScreen(viewModel: UbuntuViewModel = viewModel()) {
    val navController = rememberNavController()

    val vmState by viewModel.vmState.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val setupProgress by viewModel.setupProgress.collectAsState()
    val configData by viewModel.configData.collectAsState()
    val terminalLines by viewModel.terminalLines.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1B1B22),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentRoute == "dashboard",
                    onClick = {
                        navController.navigate("dashboard") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = Color(0xFF69F0AE),
                        indicatorColor = Color(0xFF69F0AE),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )

                NavigationBarItem(
                    selected = currentRoute == "terminal",
                    onClick = {
                        navController.navigate("terminal") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Terminal") },
                    label = { Text("Terminal") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = Color(0xFF69F0AE),
                        indicatorColor = Color(0xFF69F0AE),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )

                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = Color(0xFF69F0AE),
                        indicatorColor = Color(0xFF69F0AE),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                VmDashboardScreen(
                    vmState = vmState,
                    capabilities = capabilities,
                    storageInfo = storageInfo,
                    setupProgress = setupProgress,
                    configData = configData,
                    onStartVm = { viewModel.startVm() },
                    onStopVm = { viewModel.stopVm() },
                    onNavigateToTerminal = { navController.navigate("terminal") },
                    onRefreshDiagnostics = {
                        viewModel.checkCapabilities()
                        viewModel.refreshStorageInfo()
                    }
                )
            }

            composable("terminal") {
                UbuntuTerminalScreen(
                    terminalLines = terminalLines,
                    vmState = vmState,
                    onSendCommand = { viewModel.sendConsoleInput(it) },
                    onClearBuffer = { viewModel.clearTerminalBuffer() },
                    onStartVm = { viewModel.startVm() },
                    onStopVm = { viewModel.stopVm() }
                )
            }

            composable("settings") {
                VmSettingsScreen(
                    currentConfig = configData,
                    onSaveConfig = { updated -> viewModel.updateConfig(updated) }
                )
            }
        }
    }
}
