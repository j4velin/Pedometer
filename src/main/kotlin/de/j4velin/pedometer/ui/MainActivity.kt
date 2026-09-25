/*
 * Copyright 2026 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.j4velin.pedometer.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.j4velin.pedometer.PedometerApp
import de.j4velin.pedometer.R
import de.j4velin.pedometer.SensorListener
import de.j4velin.pedometer.ui.dialogs.AboutDialog
import de.j4velin.pedometer.ui.dialogs.SplitDialog
import de.j4velin.pedometer.ui.dialogs.StatisticsDialog
import de.j4velin.pedometer.ui.overview.OverviewScreen
import de.j4velin.pedometer.ui.overview.OverviewViewModel
import de.j4velin.pedometer.ui.settings.SettingsScreen
import de.j4velin.pedometer.ui.settings.SettingsViewModel
import de.j4velin.pedometer.ui.theme.PedometerTheme

/**
 * The app's only activity: the overview and the settings, and the dialogs on top of them.
 *
 * The launcher starts it through the alias ".ui.Activity_Main", the name this activity had
 * before, so that existing home screen shortcuts keep working.
 */
class MainActivity : ComponentActivity() {

    private val app get() = PedometerApp.get(this)

    // not AndroidViewModels: their default factory keeps the first Application it sees, which
    // under Robolectric is the one of an earlier test
    private val overview: OverviewViewModel by viewModels {
        viewModelFactory { initializer { OverviewViewModel(app) } }
    }
    private val settings: SettingsViewModel by viewModels {
        viewModelFactory { initializer { SettingsViewModel(app) } }
    }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (SensorListener.hasPermission(this)) {
                SensorListener.start(this)
            } else {
                Toast.makeText(this, R.string.permission_activity_recognition, Toast.LENGTH_LONG)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PedometerTheme { MainScreen() } }
        requestPermissionsOrStart(firstStart = savedInstanceState == null)
        app.games.start(this)
    }

    /**
     * Requests the runtime permissions the step counter service needs, or starts the service if
     * they are granted. The permissions are only requested when the activity is created for the
     * first time, not when it is recreated (for example after a rotation).
     */
    private fun requestPermissionsOrStart(firstStart: Boolean) {
        val missing = buildList {
            if (!SensorListener.hasPermission(this@MainActivity)) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isEmpty()) {
            SensorListener.start(this)
        } else if (firstStart) {
            permissionRequest.launch(missing.toTypedArray())
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        val onSettings = entry?.destination?.route == SETTINGS
        val games by app.games.state.collectAsStateWithLifecycle()
        var dialog by rememberSaveable { mutableStateOf<MainDialog?>(null) }

        fun onMenuEntry(menuEntry: MenuEntry) {
            when (menuEntry) {
                MenuEntry.SETTINGS -> navController.navigate(SETTINGS) { launchSingleTop = true }
                MenuEntry.ACHIEVEMENTS, MenuEntry.LEADERBOARD -> when {
                    !games.signedIn -> dialog = MainDialog.SIGN_IN_NECESSARY
                    menuEntry == MenuEntry.ACHIEVEMENTS -> app.games.showAchievements(this)
                    else -> app.games.showLeaderboards(this)
                }
                MenuEntry.FAQ -> try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, "http://j4velin.de/faq/index.php?app=pm".toUri())
                    )
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
                MenuEntry.SPLIT_COUNT -> dialog = MainDialog.SPLIT
                MenuEntry.ABOUT -> dialog = MainDialog.ABOUT
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(if (onSettings) R.string.settings else R.string.app_name))
                    },
                    navigationIcon = {
                        if (onSettings) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(painterResource(R.drawable.ic_arrow_back), null)
                            }
                        }
                    },
                    actions = {
                        Menu(
                            MenuEntry.entries.filter {
                                (!onSettings || it.onSettings) && (!it.games || app.games.available)
                            },
                            ::onMenuEntry
                        )
                    },
                )
            },
        ) { padding ->
            NavHost(navController, startDestination = OVERVIEW, Modifier.padding(padding)) {
                composable(OVERVIEW) {
                    OverviewScreen(
                        overview,
                        onBarsClick = { dialog = MainDialog.STATISTICS },
                        onNoSensor = { finish() },
                    )
                }
                composable(SETTINGS) {
                    SettingsScreen(
                        settings,
                        games = if (app.games.available) games else null,
                        onSignIn = { app.games.signIn(this@MainActivity) },
                    )
                }
            }
        }

        val close = { dialog = null }
        when (dialog) {
            MainDialog.STATISTICS -> StatisticsDialog(remember { overview.statistics() }, close)
            MainDialog.SPLIT -> SplitDialog(app.settings, overview.totalSteps, close)
            MainDialog.ABOUT -> AboutDialog(close)
            MainDialog.SIGN_IN_NECESSARY -> AlertDialog(
                onDismissRequest = close,
                title = { Text(stringResource(R.string.sign_in_necessary)) },
                text = { Text(stringResource(R.string.please_sign_in_with_your_google_account)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        navController.navigate(SETTINGS) { launchSingleTop = true }
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = close) { Text(stringResource(android.R.string.cancel)) }
                },
            )
            null -> Unit
        }
    }

    @Composable
    private fun Menu(entries: List<MenuEntry>, onClick: (MenuEntry) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }, Modifier.testTag(MENU_TAG)) {
            Icon(painterResource(R.drawable.ic_more_vert), null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.title)) },
                    onClick = {
                        expanded = false
                        onClick(entry)
                    },
                )
            }
        }
    }

    private enum class MainDialog { STATISTICS, SPLIT, ABOUT, SIGN_IN_NECESSARY }

    /**
     * The menu, in order
     *
     * @param onSettings shown on the settings too, not only on the overview
     * @param games only in builds with Play Games
     */
    enum class MenuEntry(@StringRes val title: Int, val onSettings: Boolean, val games: Boolean) {
        SETTINGS(R.string.settings, onSettings = false, games = false),
        ACHIEVEMENTS(R.string.achievements, onSettings = true, games = true),
        LEADERBOARD(R.string.leaderboard, onSettings = true, games = true),
        FAQ(R.string.faq, onSettings = true, games = false),
        SPLIT_COUNT(R.string.split_count, onSettings = false, games = false),
        ABOUT(R.string.about, onSettings = true, games = false),
    }

    companion object {
        const val MENU_TAG = "menu"
        private const val OVERVIEW = "overview"
        private const val SETTINGS = "settings"
    }
}
