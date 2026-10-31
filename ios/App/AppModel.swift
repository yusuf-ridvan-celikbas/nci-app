import Foundation
import Observation
import WidgetKit
import CansinCore

struct Prefs: Equatable {
    var ageConfirmed = false
    var appName = AppName.SOY_TU_TENTACION
    var themeMode = ThemeMode.SYSTEM
    var accent = Accent.AMBER
    var remindersEnabled = false
    var reminderIntervalMin = ReminderPolicy.defaultIntervalMin
    var reminderSilent = false
    var beerSize = BeerSize.default
    var profile: BodyProfile?
    var profilePromptDismissed = false
    var waterEnabled = true
    var waterEvery = WaterRules.defaultEvery
    var timerEnabled = true
    var timer = TimerState()

    init() {}

    init(_ s: SettingsStore) {
        ageConfirmed = s.ageConfirmed; appName = s.appName; themeMode = s.themeMode; accent = s.accent
        remindersEnabled = s.remindersEnabled; reminderIntervalMin = s.reminderIntervalMin; reminderSilent = s.reminderSilent
        beerSize = s.beerSize; profile = s.profile; profilePromptDismissed = s.profilePromptDismissed
        waterEnabled = s.waterEnabled; waterEvery = s.waterEvery; timerEnabled = s.timerEnabled; timer = s.timer
    }
}

enum Route: Hashable { case tab, promille, history, session(Int64), settings }

enum UndoItem: Equatable {
    case beer(BeerEvent)
    case water(WaterEvent)

    var messageKey: String {
        if case .beer = self { return "beer_added" }
        return "water_added"
    }
}

@MainActor @Observable final class AppModel {
    private(set) var prefs = Prefs()
    private(set) var snapshot = CounterSnapshot()
    private(set) var session: Session?
    private(set) var beers: [BeerEvent] = []
    private(set) var notes: [Note] = []
    private(set) var water: [WaterEvent] = []
    private(set) var friends: [FriendProfile] = []
    private(set) var storeUnavailable = false
    var undo: UndoItem?
    var path: [Route] = []
    /// "Not now" on the water nudge: hidden until the next beer (in memory, like Android).
    var waterDismissedAt: Int?

    private let store = SettingsStore.shared
    private var repo: Repository?
    private var undoTask: Task<Void, Never>?

    init() {
        prefs = Prefs(store)
        Reminders.registerCategory()
    }

    var repository: Repository? {
        if repo == nil { repo = Effects.makeRepository() }
        return repo
    }

    // MARK: Lifecycle

    /// On foreground: new context (widget may have written), close a stale session, reload.
    func onForeground() {
        repository?.refresh()
        repository?.closeIfStale()
        reload()
    }

    func reload() {
        prefs = Prefs(store)
        guard let repo = repository else {
            storeUnavailable = true
            return
        }
        storeUnavailable = false
        snapshot = repo.snapshot()
        let record = snapshot.sessionId.flatMap(repo.session)
        session = record?.core
        beers = record.map(repo.beers) ?? []
        notes = record.map(repo.notes) ?? []
        water = record.map(repo.water) ?? []
        friends = repo.friends()
    }

    func updatePrefs(_ change: (SettingsStore) -> Void) {
        change(store)
        reload()
        WidgetCenter.shared.reloadAllTimelines()
    }

    // MARK: Derived

    var isVersus: Bool { session?.mode == .VERSUS }
    var meSide: Side? { VersusRules.meSide(session) }
    var myBeers: [BeerEvent] { guard let s = session else { return [] }; return beers.filter { VersusRules.isMine($0, in: s) } }
    var tabTotals: TabTotals { VersusRules.totals(notes) }

    var beersSinceWater: Int {
        WaterRules.beersSinceWater(myBeerTimes: myBeers.map(\.at), lastWaterAt: water.map(\.at).max())
    }

    var showWaterNudge: Bool {
        session != nil && meSide != nil &&
            WaterRules.shouldNudge(enabled: prefs.waterEnabled, every: prefs.waterEvery, since: beersSinceWater, dismissedAt: waterDismissedAt)
    }

    var timerRunningHere: Bool { prefs.timerEnabled && TimerRules.isCurrent(prefs.timer, sessionId: session?.id) }

    var canStartTimer: Bool { TimerRules.canStart(enabled: prefs.timerEnabled, state: prefs.timer, myLatestBeer: snapshot.myLatestBeer) }

    func estimate(now: Int64) -> PromilleEstimate? {
        guard let p = prefs.profile else { return nil }
        let drinks = myBeers.map { Drink(at: $0.at, finishedAt: $0.finishedAt, sizeMl: $0.sizeMl, abv: $0.abv) }
        return PromilleModel.estimate(profile: p, drinks: drinks, now: now)
    }

    // MARK: Actions

    func addBeer(size: BeerSize? = nil, side: Side = .SOLO) {
        guard let e = repository?.addBeer(size: size, side: side) else { return }
        if e.side == meSide { waterDismissedAt = nil }
        reload()
        showUndo(.beer(e))
    }

    func removeLatest(side: Side = .SOLO) { act { $0.removeLatest(side: side) } }

    func endSession() { act { $0.endSession() }; waterDismissedAt = nil }

    func undoLast() {
        guard let item = undo else { return }
        undo = nil
        switch item {
        case .beer(let e): act { $0.undo(e) }
        case .water(let e): act { $0.undoWater(e) }
        }
    }

    func addWater() {
        guard let e = repository?.addWater() else { return }
        reload()
        showUndo(.water(e))
    }

    func startVersus(_ setup: VersusSetup) { act { $0.startVersus(setup) } }
    func updateVersus(_ setup: VersusSetup) { act { $0.updateVersus(setup) } }
    func saveFriend(name: String, side: Side) -> Int64? {
        let id = repository?.saveFriend(name: name, color: side == .BLUE ? 0x0061A4 : 0xBA1A1A)
        reload()
        return id
    }
    func deleteFriend(_ id: Int64) { act { $0.deleteFriend(id) } }

    func addNote(text: String, amountMinor: Int64?, side: Side?) { act { $0.addNote(text: text, amountMinor: amountMinor, side: side) } }
    func updateNote(_ n: Note) { act { $0.updateNote(n) } }
    func deleteNote(_ n: Note) { act { $0.deleteNote(n) } }
    func updateBeer(_ e: BeerEvent) { act { $0.updateBeer(e) } }

    func startTimer() { if let r = repository { DrinkTimer.start(repository: r) }; reload() }
    func stopTimer() { if let r = repository { DrinkTimer.stop(repository: r) }; reload() }

    func setTimerEnabled(_ on: Bool) {
        updatePrefs {
            $0.timerEnabled = on
            if !on { $0.timer = TimerState() }
        }
    }

    func setRemindersEnabled(_ on: Bool) {
        updatePrefs { $0.remindersEnabled = on }
        if let r = repository { Reminders.enabledChanged(on, repository: r) }
    }

    func handleNotification(action: String, userInfo: [AnyHashable: Any]) {
        guard let r = repository else { return }
        Reminders.handle(action: action, userInfo: userInfo, repository: r)
        reload()
    }

    func deleteAllData() {
        Reminders.cancel()
        repository?.deleteAll()
        store.wipe()
        Exporter.wipe()
        waterDismissedAt = nil
        reload()
        WidgetCenter.shared.reloadAllTimelines()
    }

    func open(url: URL) {
        guard url.scheme == "cansin", prefs.ageConfirmed else { return }
        switch url.host {
        case "tab": path = [.tab]
        case "app": path = []
        default: break
        }
    }

    private func act(_ block: (Repository) -> Void) {
        guard let r = repository else { return }
        block(r)
        reload()
    }

    private func showUndo(_ item: UndoItem) {
        undo = item
        undoTask?.cancel()
        undoTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled, self?.undo == item else { return }
            self?.undo = nil
        }
    }
}
