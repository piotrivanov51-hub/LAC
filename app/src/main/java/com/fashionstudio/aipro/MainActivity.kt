package com.fashionstudio.aipro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.fashionstudio.aipro.core.ui.theme.FashionStudioTheme
import com.fashionstudio.aipro.ui.screens.*
import com.fashionstudio.aipro.viewmodel.SettingsViewModel
import com.fashionstudio.aipro.viewmodel.StudioViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()

            FashionStudioTheme(darkTheme = settingsState.settings.darkThemeEnabled) {
                FashionStudioRootScaffold(settingsVm = settingsVm)
            }
        }
    }
}

@Composable
fun FashionStudioRootScaffold(
    settingsVm: SettingsViewModel,
    studioVm: StudioViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"

    val topLevelTabs = listOf(
        Triple("home", "Home", Icons.Default.AutoAwesome),
        Triple("projects", "Projects", Icons.Default.Folder),
        Triple("batch", "Batch", Icons.Default.Layers),
        Triple("brand", "Brand", Icons.Default.Storefront),
        Triple("settings", "Settings", Icons.Default.Settings)
    )
    val showBottomBar = topLevelTabs.any { it.first == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    topLevelTabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    studioVm = studioVm,
                    onNewProject = { navController.navigate("create") },
                    onOpenProject = { id ->
                        studioVm.loadProject(id)
                        navController.navigate("editor")
                    },
                    onOpenBatch = { navController.navigate("batch") },
                    onOpenSettings = { navController.navigate("settings") }
                )
            }
            composable("create") {
                CreateProjectScreen(
                    studioVm = studioVm,
                    onBack = { navController.popBackStack() },
                    onProjectReady = {
                        navController.navigate("editor") {
                            popUpTo("create") { inclusive = true }
                        }
                    }
                )
            }
            composable("editor") {
                StudioEditorScreen(
                    studioVm = studioVm,
                    onBack = { navController.popBackStack() },
                    onOpenResult = { navController.navigate("result") }
                )
            }
            composable("result") {
                ResultVerificationScreen(
                    studioVm = studioVm,
                    onBackToEditor = { navController.popBackStack() }
                )
            }
            composable("projects") {
                ProjectsScreen(
                    studioVm = studioVm,
                    onOpenProject = { id ->
                        studioVm.loadProject(id)
                        navController.navigate("editor")
                    },
                    onNewProject = { navController.navigate("create") }
                )
            }
            composable("batch") {
                BatchStudioScreen(studioVm = studioVm)
            }
            composable("brand") {
                BrandStudioScreen(studioVm = studioVm)
            }
            composable("settings") {
                SettingsScreen(settingsVm = settingsVm)
            }
        }
    }
}
