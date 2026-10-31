package com.cansin.app.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.cansin.app.prefs.AppName

object LauncherAlias {
    fun apply(context: Context, selected: AppName) {
        val pm = context.packageManager
        val ordered = listOf(selected) + AppName.entries.filter { it != selected }
        for (name in ordered) {
            val state = if (name == selected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val component = ComponentName(context.packageName, name.alias)
            if (pm.getComponentEnabledSetting(component) != state) {
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
