package com.cansin.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cansin.app.R
import kotlinx.coroutines.delay

@Composable
fun TimerBar(startedAt: Long?, canStart: Boolean, resumed: Boolean, onStart: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    if (startedAt == null && !canStart) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (startedAt != null) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(startedAt, resumed) {
                while (resumed) {
                    now = System.currentTimeMillis()
                    delay(1_000 - now % 1_000)
                }
            }
            val elapsed = DateUtils.formatElapsedTime(((now - startedAt) / 1_000).coerceAtLeast(0))
            Text(stringResource(R.string.timer_running) + " " + elapsed, Modifier.weight(1f))
            OutlinedButton(onClick = onStop) { Text(stringResource(R.string.timer_stop)) }
        } else {
            Text(stringResource(R.string.setting_timer), Modifier.weight(1f))
            FilledTonalButton(onClick = onStart) { Text(stringResource(R.string.timer_idle)) }
        }
    }
}
