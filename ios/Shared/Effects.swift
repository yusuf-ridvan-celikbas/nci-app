import Foundation
import UserNotifications
import WidgetKit
import CansinCore

/// Local-only reminders. One pending request id → rescheduling replaces it (no duplicates).
/// iOS keeps pending notifications across reboots, so there is no boot receiver.
enum Reminders {
    static let requestId = "reminder"
    static let category = "REMINDER"
    static let actionAnother = "ANOTHER"
    static let actionSnooze = "SNOOZE"
    static let actionStop = "STOP"
    static let sessionKey = "session_id"

    static func registerCategory() {
        // Actions need the device unlocked: the store uses complete file protection.
        let opts: UNNotificationActionOptions = [.authenticationRequired]
        let cat = UNNotificationCategory(
            identifier: category,
            actions: [UNNotificationAction(identifier: actionAnother, title: L("action_another"), options: opts),
                      UNNotificationAction(identifier: actionSnooze, title: L("action_snooze"), options: opts),
                      UNNotificationAction(identifier: actionStop, title: L("action_stop"), options: opts)],
            intentIdentifiers: [],
            hiddenPreviewsBodyPlaceholder: L("reminder_public"),
            options: [])
        UNUserNotificationCenter.current().setNotificationCategories([cat])
    }

    static func schedule(sessionId: Int64, settings: SettingsStore = .shared, from now: Int64 = AppStore.now) {
        guard ReminderPolicy.shouldSchedule(enabled: settings.remindersEnabled, sessionId: sessionId,
                                            stoppedSessionId: settings.reminderState.stoppedSessionId) else { return }
        let content = UNMutableNotificationContent()
        content.title = L(settings.appName.labelKey)
        content.body = L("reminder_text")
        content.categoryIdentifier = category
        content.sound = settings.reminderSilent ? nil : .default
        content.interruptionLevel = settings.reminderSilent ? .passive : .active
        content.userInfo = [sessionKey: NSNumber(value: sessionId)]
        let seconds = Double(ReminderPolicy.nextAt(now: now, intervalMin: settings.reminderIntervalMin) - now) / 1000
        let request = UNNotificationRequest(identifier: requestId, content: content,
                                            trigger: UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false))
        var state = settings.reminderState
        state.sessionId = sessionId
        settings.reminderState = state
        UNUserNotificationCenter.current().add(request)
    }

    static func cancel() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [requestId])
        center.removeDeliveredNotifications(withIdentifiers: [requestId])
    }

    static func stopForTonight(sessionId: Int64, settings: SettingsStore = .shared) {
        cancel()
        settings.reminderState = ReminderState(sessionId: nil, stoppedSessionId: sessionId)
    }

    /// Enabling mid-session schedules from now.
    @MainActor static func enabledChanged(_ enabled: Bool, repository: Repository) {
        if enabled, let id = repository.snapshot().sessionId { schedule(sessionId: id) } else if !enabled { cancel() }
    }

    @MainActor static func handle(action: String, userInfo: [AnyHashable: Any], repository: Repository) {
        guard let sessionId = (userInfo[sessionKey] as? NSNumber)?.int64Value,
              ReminderPolicy.isCurrent(actionSessionId: sessionId, openSessionId: repository.snapshot().sessionId) else { return }
        switch action {
        case actionAnother: repository.addMyBeer(finishPrevious: true)
        case actionSnooze: schedule(sessionId: sessionId)
        case actionStop: stopForTonight(sessionId: sessionId)
        default: break
        }
    }
}

@MainActor enum DrinkTimer {
    @discardableResult
    static func start(repository: Repository, settings: SettingsStore = .shared) -> Bool {
        guard let beer = repository.snapshot().myLatestBeer,
              TimerRules.canStart(enabled: settings.timerEnabled, state: settings.timer, myLatestBeer: beer) else { return false }
        settings.timer = TimerState(beerId: beer.id, sessionId: beer.sessionId, startedAt: AppStore.now)
        WidgetCenter.shared.reloadAllTimelines()
        return true
    }

    static func stop(repository: Repository, settings: SettingsStore = .shared) {
        let state = settings.timer
        settings.timer = TimerState()
        if let beerId = state.beerId, let sessionId = state.sessionId {
            repository.finishBeer(id: beerId, sessionId: sessionId)
        } else {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}

/// Wires repository events to reminders, timer and widgets (Android SessionListener).
@MainActor enum Effects {
    static func makeRepository() -> Repository? {
        guard let container = AppStore.container else { return nil }
        let repo = Repository(container: container)
        repo.onEvents = { events in handle(events) }
        return repo
    }

    static func handle(_ events: [RepoEvent], settings: SettingsStore = .shared) {
        for event in events {
            switch event {
            case .beerAdded(let beer, let mine):
                if mine { Reminders.schedule(sessionId: beer.sessionId) }
            case .sessionClosed(let id):
                if settings.reminderState.sessionId == id || settings.reminderState.sessionId == nil { Reminders.cancel() }
                if settings.timer.sessionId == id { settings.timer = TimerState() }
            }
        }
        WidgetCenter.shared.reloadAllTimelines()
    }
}
