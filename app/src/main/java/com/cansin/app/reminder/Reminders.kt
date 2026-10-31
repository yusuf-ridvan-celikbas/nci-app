package com.cansin.app.reminder

import android.app.AlarmManager
import android.content.Context
import com.cansin.app.data.SessionRepository
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.flow.first

class Reminders(
    private val context: Context,
    private val store: SettingsStore,
    private val repository: () -> SessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifier = ReminderNotifier(context)

    suspend fun onBeerAdded(sessionId: Long) {
        val settings = store.settings.first()
        val state = store.reminderState.first()
        if (ReminderPolicy.shouldSchedule(settings.remindersEnabled, sessionId, state.stoppedSessionId)) {
            schedule(ReminderPolicy.nextAt(clock(), settings.reminderIntervalMin), sessionId)
        }
    }

    suspend fun onSessionClosed(sessionId: Long) {
        val state = store.reminderState.first()
        if (state.sessionId == sessionId) cancel()
        if (state.stoppedSessionId == sessionId) store.setStoppedSession(null)
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.setRemindersEnabled(enabled)
        if (!enabled) return cancel()
        repository().snapshot().sessionId?.let { onBeerAdded(it) }
    }

    suspend fun handle(action: String, sessionId: Long) {
        val settings = store.settings.first()
        val current = ReminderPolicy.isCurrent(sessionId, repository().snapshot().sessionId)
        when (action) {
            ReminderReceiver.ACTION_REMIND -> {
                store.setScheduledReminder(null, null)
                val stopped = store.reminderState.first().stoppedSessionId == sessionId
                if (settings.remindersEnabled && current && !stopped) notifier.show(settings, sessionId)
            }
            ReminderReceiver.ACTION_ANOTHER -> {
                notifier.dismiss()
                if (current) repository().addMyBeer(finishPrevious = true)
            }
            ReminderReceiver.ACTION_SNOOZE -> {
                notifier.dismiss()
                if (current) schedule(ReminderPolicy.nextAt(clock(), settings.reminderIntervalMin), sessionId)
            }
            ReminderReceiver.ACTION_STOP -> {
                notifier.dismiss()
                store.setStoppedSession(sessionId)
                cancel()
            }
        }
    }

    suspend fun restore() {
        val settings = store.settings.first()
        val state = store.reminderState.first()
        val nextAt = state.nextAt ?: return
        val sessionId = state.sessionId ?: return
        if (!settings.remindersEnabled || repository().snapshot().sessionId != sessionId) return cancel()
        setAlarm(ReminderPolicy.restoreAt(nextAt, clock()), sessionId)
    }

    private suspend fun schedule(at: Long, sessionId: Long) {
        setAlarm(at, sessionId)
        store.setScheduledReminder(at, sessionId)
    }

    private fun setAlarm(at: Long, sessionId: Long) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ReminderReceiver.intent(context, ReminderReceiver.ACTION_REMIND, sessionId))
    }

    private suspend fun cancel() {
        alarms.cancel(ReminderReceiver.intent(context, ReminderReceiver.ACTION_REMIND, 0))
        store.setScheduledReminder(null, null)
        notifier.dismiss()
    }
}
