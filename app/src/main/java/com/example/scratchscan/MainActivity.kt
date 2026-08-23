package com.example.scratchscan

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.scratchscan.ui.catalog.GameListScreen
import com.example.scratchscan.ui.catalog.AllGamesViewModel
import com.example.scratchscan.ui.compare.CompareGamesScreen
import com.example.scratchscan.ui.compare.CompareViewModel
import com.example.scratchscan.ui.detail.GameDetailScreen
import com.example.scratchscan.ui.detail.GameDetailViewModel
import com.example.scratchscan.ui.scanner.ScannerView
import com.example.scratchscan.ui.scanner.ScannerViewModel
import com.example.scratchscan.ui.theme.ScratchScanTheme

sealed class Screen(val route: String, val labelId: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Scanner : Screen("scanner", R.string.nav_scanner, Icons.Default.CenterFocusStrong)
    object Games : Screen("games", R.string.nav_games, Icons.AutoMirrored.Filled.List)
    object Compare : Screen("compare", R.string.nav_compare, Icons.Default.BarChart)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = LocalContext.current.applicationContext as Application
            val settingsViewModel: SettingsViewModel = viewModel()
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()

            ScratchScanTheme(darkTheme = isDarkMode) {
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        hasCameraPermission = granted
                    }
                )

                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                }

                val navController = rememberNavController()
                val items = listOf(Screen.Scanner, Screen.Games, Screen.Compare)

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(stringResource(screen.labelId)) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Scanner.route
                        ) {
                            composable(Screen.Scanner.route) {
                                if (hasCameraPermission) {
                                    ScannerView(
                                        onNavigateToStats = { gameNumber -> 
                                            navController.navigate("detail/$gameNumber")
                                        },
                                        viewModel = viewModel(factory = ScannerViewModel.Factory(app))
                                    )
                                } else {
                                    CameraPermissionPrompt(onRequestPermission = {
                                        launcher.launch(Manifest.permission.CAMERA)
                                    })
                                }
                            }
                            composable(Screen.Games.route) {
                                GameListScreen(
                                    onGameClick = { gameNumber -> navController.navigate("detail/$gameNumber") },
                                    viewModel = viewModel(factory = AllGamesViewModel.Factory(app))
                                )
                            }
                            composable(Screen.Compare.route) { 
                                CompareGamesScreen(
                                    viewModel = viewModel(factory = CompareViewModel.Factory(app))
                                ) 
                            }
                            composable(
                                route = "detail/{gameNumber}",
                                arguments = listOf(navArgument("gameNumber") {
                                    type = NavType.IntType
                                })
                            ) { backStackEntry ->
                                val gameNumber = backStackEntry.arguments?.getInt("gameNumber") ?: 0
                                GameDetailScreen(
                                    gameNumber = gameNumber,
                                    viewModel = viewModel(factory = GameDetailViewModel.Factory(app))
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onBackClick = { navController.popBackStack() },
                                    settingsViewModel = settingsViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPermissionPrompt(onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onRequestPermission) {
            Text("Enable Camera for Scanning")
        }
    }
}
