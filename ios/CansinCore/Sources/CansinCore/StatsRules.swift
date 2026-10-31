public struct SessionStats: Equatable, Sendable {
    public var sessionId: Int64
    public var startedAt: Millis
    public var beers: Int
    public var peak: Double?
    public var paceMin: Double?
}

public struct Overview: Equatable, Sendable {
    public var sessions = 0
    public var avgBeers: Double?
    public var avgPaceMin: Double?
    public var waterPerBeer: Double?
    public var perSession: [SessionStats] = []

    public init() {}
}

public enum StatsRules {
    public static let chartSessions = 20

    public static func durationMs(_ session: Session) -> Millis? {
        session.endedAt.map { max(0, $0 - session.startedAt) }
    }

    public static func paceMin(_ times: [Millis]) -> Double? {
        guard times.count >= 2, let hi = times.max(), let lo = times.min() else { return nil }
        return Double(hi - lo) / 60_000 / Double(times.count - 1)
    }

    public static func peak(profile: BodyProfile?, beers: [BeerEvent]) -> Double? {
        guard let profile, let last = beers.map(\.at).max() else { return nil }
        let drinks = beers.map { Drink(at: $0.at, finishedAt: $0.finishedAt, sizeMl: $0.sizeMl, abv: $0.abv) }
        return PromilleModel.estimate(profile: profile, drinks: drinks, now: last).peak
    }

    public static func session(_ session: Session, beers: [BeerEvent], profile: BodyProfile?) -> SessionStats? {
        guard VersusRules.meSide(session) != nil else { return nil }
        let mine = beers.filter { $0.sessionId == session.id && VersusRules.isMine($0, in: session) }
        return SessionStats(sessionId: session.id, startedAt: session.startedAt, beers: mine.count,
                            peak: peak(profile: profile, beers: mine), paceMin: paceMin(mine.map(\.at)))
    }

    public static func overview(sessions: [Session], beers: [BeerEvent], waterBySession: [Int64: Int],
                                profile: BodyProfile?) -> Overview {
        let bySession = Dictionary(grouping: beers, by: \.sessionId)
        let stats = sessions.sorted { $0.startedAt < $1.startedAt }
            .compactMap { session($0, beers: bySession[$0.id] ?? [], profile: profile) }
        guard !stats.isEmpty else { return Overview() }
        let totalBeers = stats.reduce(0) { $0 + $1.beers }
        let intervals = stats.reduce(0) { $0 + max(0, $1.beers - 1) }
        let span = stats.reduce(0.0) { acc, s in acc + (s.paceMin.map { $0 * Double(s.beers - 1) } ?? 0) }
        let water = stats.reduce(0) { $0 + (waterBySession[$1.sessionId] ?? 0) }
        var o = Overview()
        o.sessions = stats.count
        o.avgBeers = Double(totalBeers) / Double(stats.count)
        o.avgPaceMin = intervals > 0 ? span / Double(intervals) : nil
        o.waterPerBeer = totalBeers > 0 ? Double(water) / Double(totalBeers) : nil
        o.perSession = Array(stats.suffix(chartSessions))
        return o
    }
}
