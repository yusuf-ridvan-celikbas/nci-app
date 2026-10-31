package com.cansin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.PinRules
import com.cansin.app.system.AppLock
import com.cansin.app.system.PinCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(title: String, lock: AppLock, onUnlocked: () -> Unit, onDeleteAll: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var forgot by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        waitUntil = System.currentTimeMillis() + lock.remainingWait()
        focus.requestFocus()
    }
    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val waitSeconds = ((waitUntil - now + 999) / 1_000).coerceAtLeast(0)
    val canTry = waitSeconds == 0L && !busy && PinRules.isValid(pin)
    val submit = {
        if (canTry) {
            busy = true
            scope.launch {
                when (val result = lock.check(pin)) {
                    PinCheck.Ok -> onUnlocked()
                    is PinCheck.Wrong -> { wrong = true; waitUntil = System.currentTimeMillis() + result.waitMs }
                    is PinCheck.Wait -> waitUntil = System.currentTimeMillis() + result.waitMs
                }
                pin = ""
                busy = false
            }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().imePadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.lock_enter_pin))
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { it in '0'..'9' }.take(PinRules.MAX_LENGTH); wrong = false },
                singleLine = true,
                isError = wrong,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.focusRequester(focus),
            )
            val status = when {
                waitSeconds > 0 -> stringResource(R.string.lock_wait, waitSeconds)
                wrong -> stringResource(R.string.lock_wrong)
                else -> ""
            }
            Text(status, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Button(onClick = submit, enabled = canTry) { Text(stringResource(R.string.lock_unlock)) }
            TextButton(onClick = { forgot = true }) { Text(stringResource(R.string.lock_forgot)) }
        }
    }
    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text(stringResource(R.string.lock_forgot)) },
            text = { Text(stringResource(R.string.lock_forgot_text)) },
            confirmButton = {
                TextButton(onClick = { forgot = false; onDeleteAll() }) {
                    Text(stringResource(R.string.lock_delete_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { forgot = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
