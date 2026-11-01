package com.cansin.app.system

import android.app.Activity
import android.app.ActivityManager

/** Android screen pinning (user-confirmed lock task). No permission and no device owner needed. */
object ScreenPin {
    fun isPinned(activity: Activity): Boolean =
        activity.getSystemService(ActivityManager::class.java)?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    /** The system shows its own "Pin app?" prompt; the state changes only after the user confirms. */
    fun pin(activity: Activity) {
        runCatching { activity.startLockTask() }
    }

    /** Throws if not pinned, hence runCatching. */
    fun unpin(activity: Activity) {
        runCatching { activity.stopLockTask() }
    }
}
