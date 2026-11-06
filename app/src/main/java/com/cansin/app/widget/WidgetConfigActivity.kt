package com.cansin.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.cansin.app.CansinApp
import com.cansin.app.R
import com.cansin.app.prefs.Language
import com.cansin.app.prefs.Settings
import com.cansin.app.prefs.ThemeMode
import com.cansin.app.system.AppLocale
import com.cansin.app.ui.RadioRow
import com.cansin.app.ui.theme.CansinTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * P31 widget settings ("what this widget shows"). Exported because launchers start it; it only accepts ids
 * of our own widget and shows no counts or personal data, only the choices.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runBlocking { (newBase.applicationContext as CansinApp).settingsStore.settings.first().language }
        } else {
            Language.SYSTEM
        }
        super.attachBaseContext(AppLocale.wrap(newBase, language))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val app = application as CansinApp
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider
        if (provider != ComponentName(this, CounterWidget::class.java)) {
            finish()
            return
        }
        val current = runBlocking { app.settingsStore.widgetFocus(id) }
        setContent {
            val settings by app.settingsStore.settings.collectAsState(initial = Settings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            var focus by remember { mutableStateOf(current) }
            val promille = WidgetContent.promilleOn(settings.widgetPromille, settings.profile != null)
            CansinTheme(dark, settings.accent) {
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Column(Modifier.selectableGroup()) {
                            WidgetFocus.entries.filter { it != WidgetFocus.PROMILLE || promille }.forEach { f ->
                                RadioRow(stringResource(label(f)), f == focus) { focus = f }
                            }
                        }
                        Text(
                            stringResource(R.string.widget_config_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        Button(onClick = {
                            lifecycleScope.launch {
                                app.settingsStore.setWidgetFocus(id, focus)
                                CounterWidget.refreshAll(app)
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
                    }
                }
            }
        }
    }

    private fun label(f: WidgetFocus) = when (f) {
        WidgetFocus.AUTO -> R.string.widget_focus_auto
        WidgetFocus.DRINKS -> R.string.widget_focus_drinks
        WidgetFocus.WATER -> R.string.widget_focus_water
        WidgetFocus.TAB -> R.string.widget_focus_tab
        WidgetFocus.PROMILLE -> R.string.widget_focus_promille
    }
}
