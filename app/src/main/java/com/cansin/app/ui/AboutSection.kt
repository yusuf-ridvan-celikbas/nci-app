package com.cansin.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cansin.app.R

/** P40: contact (hand-off to the mail app, the app itself has no network) and the owner line. */
@Composable
internal fun AboutSection() {
    val context = LocalContext.current
    val version = remember { context.versionName() }
    var noMailApp by rememberSaveable { mutableStateOf(false) }
    val email = stringResource(R.string.about_email)
    val owner = stringResource(R.string.about_owner)
    val appName = stringResource(R.string.name_salgam)
    val body = stringResource(R.string.about_mail_body, version, Build.VERSION.RELEASE)

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(appName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 16.dp))

    SectionHeader(R.string.about_contact)
    Text(stringResource(R.string.about_contact_note), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    SelectionContainer {
        Text(email, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val requestSubject = stringResource(R.string.about_subject_request, appName)
        val problemSubject = stringResource(R.string.about_subject_problem, appName)
        FilledTonalButton(
            onClick = { noMailApp = !context.sendMail(email, requestSubject, body) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.about_request)) }
        OutlinedButton(
            onClick = { noMailApp = !context.sendMail(email, problemSubject, body) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.about_problem)) }
    }
    if (noMailApp) {
        Note(R.string.about_no_mail_app)
    }
    HorizontalDivider(Modifier.padding(vertical = 16.dp))

    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FooterLine(stringResource(R.string.about_made_by, owner))
        FooterLine(stringResource(R.string.about_rights, owner))
    }
}

@Composable
private fun Note(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FooterLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private fun Context.versionName(): String =
    try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

/** Opens a mail draft in the user's mail app; nothing is sent until they press send there. */
private fun Context.sendMail(to: String, subject: String, body: String): Boolean =
    try {
        startActivity(
            Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
