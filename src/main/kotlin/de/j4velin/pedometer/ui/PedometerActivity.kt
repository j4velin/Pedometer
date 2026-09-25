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
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.j4velin.pedometer.PedometerApp
import de.j4velin.pedometer.R
import de.j4velin.pedometer.SensorListener
import de.j4velin.pedometer.ui.overview.OverviewScreen
import de.j4velin.pedometer.ui.overview.OverviewViewModel
import de.j4velin.pedometer.ui.theme.PedometerTheme

/**
 * The main screen: the overview, and the settings on top of it. The flavors add the Play Games
 * entries of the menu by overriding [onGamesItem].
 *
 * The settings are still the framework PreferenceFragment, hosted in the Compose content until
 * they become Compose themselves.
 */
abstract class PedometerActivity : ComponentActivity() {

    private var settingsShown by mutableStateOf(false)
    // not an AndroidViewModel: its default factory keeps the first Application it sees, which
    // under Robolectric is the one of an earlier test
    private val overview: OverviewViewModel by viewModels {
        viewModelFactory { initializer { OverviewViewModel(PedometerApp.get(this@PedometerActivity)) } }
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
        // The system would restore the settings fragment before the Compose content (and with
        // it the fragment's container) exists. The settings screen adds a new one instead.
        savedInstanceState?.remove(FRAGMENTS_STATE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settingsShown = savedInstanceState?.getBoolean(SETTINGS_SHOWN) ?: false
        setContent { PedometerTheme { MainScreen() } }
        requestPermissionsOrStart(firstStart = savedInstanceState == null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SETTINGS_SHOWN, settingsShown)
    }

    /**
     * Requests the runtime permissions the step counter service needs, or starts the service if
     * they are granted. The permissions are only requested when the activity is created for the
     * first time, not when it is recreated (for example after a rotation).
     */
    private fun requestPermissionsOrStart(firstStart: Boolean) {
        val missing = buildList {
            if (!SensorListener.hasPermission(this@PedometerActivity)) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    this@PedometerActivity, Manifest.permission.POST_NOTIFICATIONS
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

    fun openSettings() {
        settingsShown = true
    }

    /** Handles a menu entry, see [MENU] */
    fun onMenuItem(id: Int) {
        when (id) {
            R.id.action_settings -> openSettings()
            R.id.action_split_count -> Dialog_Split.getDialog(this, overview.totalSteps).show()
            R.id.action_faq -> startActivity(
                Intent(Intent.ACTION_VIEW, "http://j4velin.de/faq/index.php?app=pm".toUri())
            )
            R.id.action_about -> showAbout()
            R.id.action_achievements, R.id.action_leaderboard -> onGamesItem(id)
        }
    }

    /** The achievements or leaderboard entry was selected */
    protected abstract fun onGamesItem(id: Int)

    private fun showAbout() {
        val text = TextView(this).apply {
            setPadding(10, 10, 10, 10)
            setText(R.string.about_text_links)
            append(
                getString(
                    R.string.about_app_version,
                    packageManager.getPackageInfo(packageName, 0).versionName
                )
            )
            movementMethod = LinkMovementMethod.getInstance()
        }
        AlertDialog.Builder(this).setTitle(R.string.about).setView(text)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .create().show()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {
        BackHandler(enabled = settingsShown) { settingsShown = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(if (settingsShown) R.string.settings else R.string.app_name))
                    },
                    navigationIcon = {
                        if (settingsShown) {
                            IconButton(onClick = { settingsShown = false }) {
                                Icon(painterResource(R.drawable.ic_arrow_back), null)
                            }
                        }
                    },
                    actions = { Menu(MENU.filter { !settingsShown || it.onSettings }) },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (settingsShown) {
                    Settings()
                } else {
                    OverviewScreen(
                        overview,
                        onBarsClick = {
                            Dialog_Statistics.getDialog(this@PedometerActivity, overview.sinceBoot)
                                .show()
                        },
                        onNoSensor = { finish() },
                    )
                }
            }
        }
    }

    @Composable
    private fun Menu(entries: List<MenuEntry>) {
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
                        try {
                            onMenuItem(entry.id)
                        } catch (e: ActivityNotFoundException) {
                            e.printStackTrace()
                        }
                    },
                )
            }
        }
    }

    /** The framework preference fragment, until the settings are Compose too */
    @Composable
    private fun Settings() {
        AndroidView(
            factory = { FrameLayout(it).apply { id = R.id.settings_container } },
            modifier = Modifier.fillMaxSize(),
        )
        DisposableEffect(Unit) {
            @Suppress("DEPRECATION")
            val fm = fragmentManager
            if (fm.findFragmentByTag(SETTINGS_TAG) == null) {
                fm.beginTransaction()
                    .replace(R.id.settings_container, Fragment_Settings(), SETTINGS_TAG).commit()
            }
            onDispose {
                // leaving the settings, rather than the activity being recreated or closed
                if (!isChangingConfigurations && !isFinishing && !isDestroyed) {
                    fm.findFragmentByTag(SETTINGS_TAG)?.let {
                        fm.beginTransaction().remove(it).commitAllowingStateLoss()
                    }
                }
            }
        }
    }

    class MenuEntry(val id: Int, @StringRes val title: Int, val onSettings: Boolean)

    companion object {
        const val MENU_TAG = "menu"
        const val SETTINGS_TAG = "settings"
        private const val SETTINGS_SHOWN = "settingsShown"
        /** Where Activity keeps the framework fragments' state */
        private const val FRAGMENTS_STATE = "android:fragments"

        /** The menu entries, in order. Split count and settings are not shown on the settings. */
        val MENU = listOf(
            MenuEntry(R.id.action_settings, R.string.settings, onSettings = false),
            MenuEntry(R.id.action_achievements, R.string.achievements, onSettings = true),
            MenuEntry(R.id.action_leaderboard, R.string.leaderboard, onSettings = true),
            MenuEntry(R.id.action_faq, R.string.faq, onSettings = true),
            MenuEntry(R.id.action_split_count, R.string.split_count, onSettings = false),
            MenuEntry(R.id.action_about, R.string.about, onSettings = true),
        )
    }
}
