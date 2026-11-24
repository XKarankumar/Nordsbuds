package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.AnimatedSplashOverlay
import com.example.ui.screens.AudioControlsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.UpdateCenterScreen
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.NordBudsPremiumTheme
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnePlusRedContainer
import com.example.ui.theme.OnePlusRedGlow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.NordBudsViewModel

object NordRoutes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_AUDIO = "audio"
    const val ROUTE_UPDATES = "updates"
    const val ROUTE_DIAGNOSTICS = "diagnostics"
}

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

class MainActivity : ComponentActivity() {

    private val viewModel: NordBudsViewModel by viewModels {
        val app = application as NordBudsApp
        NordBudsViewModel.Factory(
            appContext = applicationContext,
            bluetoothManager = app.bluetoothEarbudsManager,
            audioEffectsController = app.audioEffectsController,
            storageRepository = app.storageRepository,
            crashDiagnosticsManager = app.crashDiagnosticsManager,
            updateCenterRepository = app.updateCenterRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NordBudsPremiumTheme {
                NordBudsRoot(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppStart()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppStop()
    }
}

@Composable
fun NordBudsRoot(
    viewModel: NordBudsViewModel,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: NordRoutes.ROUTE_DASHBOARD

    var showSplash by rememberSaveable { mutableStateOf(true) }

    val bluetoothState by viewModel.bluetoothState.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val diagnosticsConsent by viewModel.diagnosticsConsent.collectAsStateWithLifecycle()
    val diagnosticEntries by viewModel.diagnosticEntries.collectAsStateWithLifecycle()
    val ancMode by viewModel.ancPreviewMode.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissions()
    }

    val requestNeededPermissions: () -> Unit = {
        val requested = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(requested)
    }

    val navTabs = listOf(
        NavTabItem(
            route = NordRoutes.ROUTE_DASHBOARD,
            labelRes = R.string.nav_dashboard,
            selectedIcon = Icons.Filled.Headphones,
            unselectedIcon = Icons.Outlined.Headphones,
            testTag = "nav_tab_dashboard",
        ),
        NavTabItem(
            route = NordRoutes.ROUTE_AUDIO,
            labelRes = R.string.nav_audio,
            selectedIcon = Icons.Filled.Equalizer,
            unselectedIcon = Icons.Outlined.Equalizer,
            testTag = "nav_tab_audio",
        ),
        NavTabItem(
            route = NordRoutes.ROUTE_UPDATES,
            labelRes = R.string.nav_updates,
            selectedIcon = Icons.Filled.SystemUpdate,
            unselectedIcon = Icons.Outlined.SystemUpdate,
            testTag = "nav_tab_updates",
        ),
        NavTabItem(
            route = NordRoutes.ROUTE_DIAGNOSTICS,
            labelRes = R.string.nav_diagnostics,
            selectedIcon = Icons.Filled.Build,
            unselectedIcon = Icons.Outlined.Build,
            testTag = "nav_tab_diagnostics",
        ),
    )

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack),
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = AmoledBlack,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        navTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = OnePlusRedGlow,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = OnePlusRedContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                    ) {
                        navTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = OnePlusRedGlow,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = OnePlusRedContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = NordRoutes.ROUTE_DASHBOARD,
                    enterTransition = { fadeIn(animationSpec = tween(220)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    composable(NordRoutes.ROUTE_DASHBOARD) {
                        DashboardScreen(
                            bluetoothState = bluetoothState,
                            onRequestPermissions = requestNeededPermissions,
                            onScanOrRefresh = { viewModel.triggerBluetoothScan() },
                            onStopScan = { viewModel.stopBluetoothScan() },
                            onOpenSystemBluetooth = {
                                runCatching { context.startActivity(viewModel.openBluetoothSettingsIntent()) }
                                    .onFailure {
                                        Toast.makeText(context, "Unable to open Bluetooth settings", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            onNavigateToAudio = { navigateToTab(NordRoutes.ROUTE_AUDIO) },
                        )
                    }

                    composable(NordRoutes.ROUTE_AUDIO) {
                        AudioControlsScreen(
                            audioState = audioState,
                            ancMode = ancMode,
                            isAudioPlaybackActive = bluetoothState.isAudioPlaybackActive,
                            onSelectAncMode = { viewModel.selectAncInspectionMode(it) },
                            onBandChanged = { idx, norm -> viewModel.updateEqBand(idx, norm) },
                            onBassBoostChanged = { viewModel.updateBassBoost(it) },
                            onApplyProfile = { viewModel.applySoundProfile(it) },
                            onSaveCustomProfile = { name, desc -> viewModel.saveCurrentAsCustomProfile(name, desc) },
                            onDeleteCustomProfile = { viewModel.deleteCustomProfile(it) },
                            onOpenSystemBluetooth = {
                                runCatching { context.startActivity(viewModel.openBluetoothSettingsIntent()) }
                            },
                        )
                    }

                    composable(NordRoutes.ROUTE_UPDATES) {
                        UpdateCenterScreen(
                            updateState = updateState,
                            permissions = permissions,
                            onUpdateManifestUrl = { viewModel.updateManifestUrl(it) },
                            onCheckForUpdates = { viewModel.checkForUpdates() },
                            onDownloadVerifiedUpdate = { viewModel.downloadVerifiedUpdate() },
                            onInstallVerifiedApk = { path ->
                                runCatching { context.startActivity(viewModel.buildInstallUpdateIntent(path)) }
                                    .onFailure {
                                        Toast.makeText(context, "Enable unknown app installs to proceed", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            onOpenUnknownSourcesSettings = {
                                runCatching { context.startActivity(viewModel.openUnknownSourcesSettingsIntent()) }
                            },
                        )
                    }

                    composable(NordRoutes.ROUTE_DIAGNOSTICS) {
                        DiagnosticsScreen(
                            permissions = permissions,
                            diagnosticsConsent = diagnosticsConsent,
                            diagnosticEntries = diagnosticEntries,
                            onRequestPermissions = requestNeededPermissions,
                            onToggleDiagnosticsConsent = { viewModel.setDiagnosticsConsent(it) },
                            onClearDiagnostics = { viewModel.clearDiagnostics() },
                            onRecordTestSnapshot = { viewModel.recordTestDiagnosticWarning() },
                            onExportDiagnostics = {
                                val intent = viewModel.createDiagnosticsExportIntent()
                                if (intent != null) {
                                    runCatching {
                                        context.startActivity(Intent.createChooser(intent, "Export NordBuds Diagnostics"))
                                    }
                                }
                            },
                            onSubmitBugReport = { summary, repro ->
                                runCatching {
                                    context.startActivity(viewModel.createBugReportIntent(summary, repro))
                                }.onFailure {
                                    Toast.makeText(context, "No browser available to open issue tracker", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        }

        AnimatedSplashOverlay(
            visible = showSplash,
            onFinished = { showSplash = false },
        )
    }
}
