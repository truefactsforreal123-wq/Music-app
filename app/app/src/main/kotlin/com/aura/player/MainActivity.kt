package com.aura.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.player.data.prefs.ThemeMode
import com.aura.player.ui.PlayerViewModel
import com.aura.player.ui.components.DownloadBanner
import com.aura.player.ui.components.MiniPlayer
import com.aura.player.ui.screens.DownloadsScreen
import com.aura.player.ui.screens.ImportScreen
import com.aura.player.ui.screens.LibraryScreen
import com.aura.player.ui.screens.NowPlayingScreen
import com.aura.player.ui.screens.PlaylistScreen
import com.aura.player.ui.screens.SearchScreen
import com.aura.player.ui.screens.SettingsScreen
import com.aura.player.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val container = (application as AuraApplication).container
            val themeMode by container.settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            // Re-apply edge-to-edge whenever the in-app theme flips, so system
            // (status bar / nav bar) icon contrast always matches the app theme.
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                )
            }
            AuraTheme(themeMode) {
                AuraApp(container)
            }
        }
    }
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

@Composable
private fun AuraApp(container: com.aura.player.di.AppContainer) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel(initializer = { PlayerViewModel(container) })

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = listOf(
        TopLevelDestination("library", "Home", Icons.Outlined.Home, Icons.Filled.Home),
        TopLevelDestination("search", "Search", Icons.Outlined.Search, Icons.Filled.Search),
        TopLevelDestination("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
    )
    val showBottomBar = currentRoute in topLevel.map { it.route }

    LaunchedEffect(Unit) {
        runCatching { container.library.syncLibrary() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                val batch by container.downloads.observeBatch().collectAsState(initial = null)
                Column {
                    AnimatedVisibility(
                        visible = (batch?.active ?: 0) > 0,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        DownloadBanner(active = batch?.active ?: 0, percent = batch?.percent ?: 0)
                    }
                    MiniPlayer(
                        container = container,
                        playerViewModel = playerViewModel,
                        onOpen = { navController.navigate("player") { launchSingleTop = true } },
                    )
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                        topLevel.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = "library",
                modifier = Modifier.fillMaxSize(),
                // Gentle fade-through with a hint of drift for every route;
                // the player route overrides with its own full slide-up.
                enterTransition = { fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 24 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(260)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutVertically(tween(260)) { it / 24 } },
            ) {
                composable("library") { LibraryScreen(container, playerViewModel, navController) }
                composable("import") { ImportScreen(container, navController) }
                composable("downloads") { DownloadsScreen(container, playerViewModel, navController) }
                composable("search") { SearchScreen(container, playerViewModel, navController) }
                composable("settings") { SettingsScreen(container) }
                composable("playlist/{id}") { entry ->
                    PlaylistScreen(
                        container,
                        playerViewModel,
                        navController,
                        playlistId = entry.arguments?.getString("id") ?: "",
                    )
                }
                composable(
                    "player",
                    enterTransition = { slideInVertically(initialOffsetY = { it }) },
                    exitTransition = { slideOutVertically(targetOffsetY = { it }) },
                ) {
                    NowPlayingScreen(container, playerViewModel, navController)
                }
            }
        }
    }
}
