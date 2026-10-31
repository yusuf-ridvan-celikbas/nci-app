public enum WaterRules {
    public static let defaultEvery = 2
    public static let minEvery = 1
    public static let maxEvery = 10

    public static func clamp(_ every: Int) -> Int { every.clamped(minEvery, maxEvery) }

    public static func beersSinceWater(myBeerTimes: [Millis], lastWaterAt: Millis?) -> Int {
        myBeerTimes.filter { lastWaterAt == nil || $0 > lastWaterAt! }.count
    }

    public static func shouldNudge(enabled: Bool, every: Int, since: Int, dismissedAt: Int?) -> Bool {
        enabled && since >= clamp(every) && (dismissedAt == nil || since > dismissedAt!)
    }
}

public struct TimerState: Equatable, Codable, Sendable {
    public var beerId: Int64?
    public var sessionId: Int64?
    public var startedAt: Millis?
    public var running: Bool { beerId != nil && startedAt != nil }

    public init(beerId: Int64? = nil, sessionId: Int64? = nil, startedAt: Millis? = nil) {
        self.beerId = beerId; self.sessionId = sessionId; self.startedAt = startedAt
    }
}

public enum WidgetPage: Int, CaseIterable, Codable, Sendable { case beer, tab, water }

public enum TimerRules {
    public static func canStart(enabled: Bool, state: TimerState, myLatestBeer: BeerEvent?) -> Bool {
        enabled && !state.running && myLatestBeer != nil && myLatestBeer?.finishedAt == nil
    }

    public static func isCurrent(_ state: TimerState, sessionId: Int64?) -> Bool {
        state.running && state.sessionId == sessionId
    }

    public static func page(_ index: Int) -> WidgetPage {
        let n = WidgetPage.allCases.count
        return WidgetPage(rawValue: ((index % n) + n) % n)!
    }

    public static func step(_ page: WidgetPage, _ delta: Int) -> WidgetPage { self.page(page.rawValue + delta) }
}

public enum ReminderPolicy {
    public static let defaultIntervalMin = 10
    public static let minIntervalMin = 5
    public static let maxIntervalMin = 60
    public static let restoreDelayMs: Millis = 60_000

    public static func clampInterval(_ minutes: Int) -> Int { minutes.clamped(minIntervalMin, maxIntervalMin) }

    public static func nextAt(now: Millis, intervalMin: Int) -> Millis { now + Millis(clampInterval(intervalMin)) * 60_000 }

    public static func shouldSchedule(enabled: Bool, sessionId: Int64, stoppedSessionId: Int64?) -> Bool {
        enabled && sessionId != stoppedSessionId
    }

    public static func isCurrent(actionSessionId: Int64, openSessionId: Int64?) -> Bool {
        openSessionId != nil && actionSessionId == openSessionId
    }

    public static func restoreAt(nextAt: Millis, now: Millis) -> Millis { max(nextAt, now + restoreDelayMs) }
}
