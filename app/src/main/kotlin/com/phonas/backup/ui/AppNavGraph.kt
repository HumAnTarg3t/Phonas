package com.phonas.backup.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phonas.backup.AppContainer
import com.phonas.backup.R
import com.phonas.backup.ui.logs.LogDetailScreen
import com.phonas.backup.ui.logs.LogsScreen
import com.phonas.backup.ui.logs.LogsViewModel
import com.phonas.backup.ui.setup.SetupScreen
import com.phonas.backup.ui.setup.SetupViewModel
import com.phonas.backup.ui.status.StatusScreen
import com.phonas.backup.ui.status.StatusViewModel

private const val ROUTE_STATUS = "status"
private const val ROUTE_LOGS = "logs"
private const val ROUTE_SETUP = "setup"
private const val ROUTE_LOG_DETAIL = "logs/detail/{logId}"

@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val statusVm: StatusViewModel = viewModel(factory = StatusViewModel.Factory(container, context))
    val logsVm: LogsViewModel = viewModel(factory = LogsViewModel.Factory(container))
    val setupVm: SetupViewModel = viewModel(factory = SetupViewModel.Factory(container))

    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val showBottomBar = currentRoute?.startsWith("logs/detail/") != true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_STATUS,
                        onClick = {
                            navController.navigate(ROUTE_STATUS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_status)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_LOGS,
                        onClick = {
                            navController.navigate(ROUTE_LOGS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_logs)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_SETUP,
                        onClick = {
                            navController.navigate(ROUTE_SETUP) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_setup)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_STATUS,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_STATUS) {
                StatusScreen(
                    viewModel = statusVm,
                    onNavigateToSetup = {
                        navController.navigate(ROUTE_SETUP) { launchSingleTop = true }
                    }
                )
            }
            composable(ROUTE_LOGS) {
                LogsScreen(
                    viewModel = logsVm,
                    onLogClick = { logId -> navController.navigate("logs/detail/$logId") }
                )
            }
            composable(ROUTE_SETUP) {
                SetupScreen(viewModel = setupVm)
            }
            composable(
                route = ROUTE_LOG_DETAIL,
                arguments = listOf(navArgument("logId") { type = NavType.LongType })
            ) { backStackEntry ->
                val logId = backStackEntry.arguments!!.getLong("logId")
                LogDetailScreen(
                    logId = logId,
                    viewModel = logsVm,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
