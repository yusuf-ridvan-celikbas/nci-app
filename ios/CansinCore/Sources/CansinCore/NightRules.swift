import Foundation

// Ports of the Android P14–P23 pure rules (Currency, Care/limits, Settle, Calendar, Drinks, Group).
// Written without a Swift compiler at hand: run `swift test` (see ios/README.md) before relying on them.

/// P14: display-only symbol; amounts stay plain minor units.
public enum Currency: String, Codable, CaseIterable, Sendable {
    case NONE, TRY, EUR, USD, GBP

    public var symbol: String? {
        switch self {
        case .NONE: return nil
        case .TRY: return "₺"
        case .EUR: return "€"
        case .USD: return "$"
        case .GBP: return "£"
        }
    }

    /// Turkish style puts the symbol after the number ("12,50 ₺"); $ and £ always go first.
    public func symbolFirst(turkish: Bool) -> Bool {
        switch self {
        case .NONE, .TRY: return false
        case .EUR: return !turkish
        case .USD, .GBP: return true
        }
    }

    public func format(_ minor: Int64, separator: Character, turkish: Bool) -> String {
        let number = NoteRules.formatAmount(minor, separator: separator)
        guard let s = symbol else { return number }
        return symbolFirst(turkish: turkish) ? s + number : number + " " + s
    }
}

// MARK: - P16/P17 care messages and self-set limits

public enum CareKind: Equatable, Sendable { case beerLimit, budget, late, slowDown }

public struct CareDismissals: Equatable, Sendable {
    public var lateAt: Millis?
    public var slowDownAtBeers: Int?
    public var limitAtBeers: Int?
    public var budgetAtSpend: Int64?
    public init(lateAt: Millis? = nil, slowDownAtBeers: Int? = nil, limitAtBeers: Int? = nil, budgetAtSpend: Int64? = nil) {
        self.lateAt = lateAt; self.slowDownAtBeers = slowDownAtBeers; self.limitAtBeers = limitAtBeers; self.budgetAtSpend = budgetAtSpend
    }
}

public struct CareInput: Equatable, Sendable {
    public var enabled: Bool
    public var sessionActive: Bool
    public var now: Millis
    public var hour: Int
    public var lateHour: Int = CareRules.defaultLateHour
    public var myBeers: Int = 0
    public var beerLimit: Int?
    public var budgetMinor: Int64?
    public var mySpendMinor: Int64 = 0
    public init(enabled: Bool, sessionActive: Bool, now: Millis, hour: Int, lateHour: Int = CareRules.defaultLateHour,
                myBeers: Int = 0, beerLimit: Int? = nil, budgetMinor: Int64? = nil, mySpendMinor: Int64 = 0) {
        self.enabled = enabled; self.sessionActive = sessionActive; self.now = now; self.hour = hour; self.lateHour = lateHour
        self.myBeers = myBeers; self.beerLimit = beerLimit; self.budgetMinor = budgetMinor; self.mySpendMinor = mySpendMinor
    }
}

public enum CareRules {
    public static let slowDownBeers = 5
    public static let defaultLateHour = 1
    public static let morningHour = 6
    public static let lateSnoozeMs: Millis = 60 * 60_000
    public static let maxHomeLength = 100
    public static let maxBeerLimit = 20
    public static let lateHours = [22, 23, 0, 1, 2, 3]

    public static func clampLateHour(_ hour: Int) -> Int { lateHours.contains(hour) ? hour : defaultLateHour }
    public static func cleanBeerLimit(_ v: Int?) -> Int? { v.flatMap { (1...maxBeerLimit).contains($0) ? $0 : nil } }
    public static func cleanBudget(_ v: Int64?) -> Int64? { v.flatMap { (1...NoteRules.maxAmountMinor).contains($0) ? $0 : nil } }

    public static func isLate(hour: Int, lateHour: Int) -> Bool {
        lateHour >= morningHour ? (hour >= lateHour || hour < morningHour) : (lateHour..<morningHour).contains(hour)
    }

    /// Limits are the user's own choice: first, and on even with kind reminders off.
    public static func pick(_ i: CareInput, dismissed d: CareDismissals) -> CareKind? {
        guard i.sessionActive else { return nil }
        if let limit = i.beerLimit, i.myBeers >= limit, d.limitAtBeers != i.myBeers { return .beerLimit }
        if let budget = i.budgetMinor, i.mySpendMinor >= budget, d.budgetAtSpend != i.mySpendMinor { return .budget }
        guard i.enabled else { return nil }
        let snoozed = d.lateAt.map { (0..<lateSnoozeMs).contains(i.now - $0) } ?? false
        if isLate(hour: i.hour, lateHour: i.lateHour) && !snoozed { return .late }
        if i.myBeers >= slowDownBeers && d.slowDownAtBeers != i.myBeers { return .slowDown }
        return nil
    }

    /// Solo: whole tab. Versus/group with my side: my side + shared items. Without a side: whole tab.
    public static func mySpend(_ session: Session, notes: [Note]) -> Int64 {
        let all = notes.reduce(0) { $0 + ($1.amountMinor ?? 0) }
        guard let me = VersusRules.meSide(session), me != .SOLO else { return all }
        return notes.filter { $0.side == nil || $0.side == me }.reduce(0) { $0 + ($1.amountMinor ?? 0) }
    }

    public static func cleanHome(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let noControl = String(String.UnicodeScalarView(raw.unicodeScalars.filter { !CharacterSet.controlCharacters.contains($0) }))
        let t = String(noControl.trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxHomeLength))
        return t.isEmpty ? nil : t
    }
}

// MARK: - P19 settle up

public struct PersonTotal: Equatable, Sendable {
    public var person: String?
    public var minor: Int64
    public init(person: String?, minor: Int64) { self.person = person; self.minor = minor }
}

public enum SettleRules {
    public static let minSplit = 2
    public static let maxSplit = 20

    /// Grouped by person (case-insensitive, first spelling kept), A–Z, unnamed last.
    public static func byPerson(_ notes: [Note]) -> [PersonTotal] {
        var named: [String: PersonTotal] = [:]
        var unnamed: Int64 = 0
        var hasUnnamed = false
        for note in notes where note.amountMinor != nil {
            let amount = note.amountMinor ?? 0
            let person = note.person?.trimmingCharacters(in: .whitespacesAndNewlines)
            if let person, !person.isEmpty {
                let key = person.lowercased()
                let prev = named[key]
                named[key] = PersonTotal(person: prev?.person ?? person, minor: (prev?.minor ?? 0) + amount)
            } else {
                unnamed += amount
                hasUnnamed = true
            }
        }
        let sorted = named.values.sorted { ($0.person ?? "").lowercased() < ($1.person ?? "").lowercased() }
        return hasUnnamed ? sorted + [PersonTotal(person: nil, minor: unnamed)] : sorted
    }

    /// Red/blue each pay their side plus half of shared; an odd cent goes to red.
    public static func splitShared(_ t: TabTotals) -> (Int64, Int64) {
        let half = t.shared / 2
        return (t.red + half + t.shared % 2, t.blue + half)
    }

    /// Even split rounded up to the cent.
    public static func eachPays(_ total: Int64, people: Int) -> Int64 {
        let n = Int64(people.clamped(minSplit, maxSplit))
        return (total + n - 1) / n
    }
}

// MARK: - P20 calendar

public struct DaySession: Equatable, Sendable {
    public var sessionId: Int64
    public var startedAt: Millis
    public var spentMinor: Int64
    public init(sessionId: Int64, startedAt: Millis, spentMinor: Int64) {
        self.sessionId = sessionId; self.startedAt = startedAt; self.spentMinor = spentMinor
    }
}

public struct MonthSummary: Equatable, Sendable {
    public var sessions: Int
    public var spentMinor: Int64
    public var avgSpentMinor: Int64?
}

/// Year + month (1–12), comparable.
public struct YearMonth: Hashable, Comparable, Sendable {
    public var year: Int
    public var month: Int
    public init(year: Int, month: Int) { self.year = year; self.month = month }
    public static func < (a: YearMonth, b: YearMonth) -> Bool { (a.year, a.month) < (b.year, b.month) }
    public func adding(months: Int) -> YearMonth {
        let total = year * 12 + (month - 1) + months
        return YearMonth(year: Int((Double(total) / 12).rounded(.down)), month: ((total % 12) + 12) % 12 + 1)
    }
}

public enum CalendarRules {
    public static let chartMonths = 6

    public static func yearMonth(_ millis: Millis, calendar: Calendar) -> YearMonth {
        let c = calendar.dateComponents([.year, .month], from: Date(timeIntervalSince1970: Double(millis) / 1000))
        return YearMonth(year: c.year ?? 1970, month: c.month ?? 1)
    }

    /// Day numbers for a month grid with nil padding before day 1; weeks start on `calendar.firstWeekday`.
    public static func grid(_ m: YearMonth, calendar: Calendar) -> [Int?] {
        guard let first = calendar.date(from: DateComponents(year: m.year, month: m.month, day: 1)),
              let days = calendar.range(of: .day, in: .month, for: first)?.count else { return [] }
        let weekday = calendar.component(.weekday, from: first)
        let lead = (weekday - calendar.firstWeekday + 7) % 7
        return Array(repeating: nil, count: lead) + (1...days).map { Optional($0) }
    }

    public static func inMonth(_ sessions: [DaySession], _ m: YearMonth, calendar: Calendar) -> [DaySession] {
        sessions.filter { yearMonth($0.startedAt, calendar: calendar) == m }
    }

    public static func summary(_ sessions: [DaySession]) -> MonthSummary {
        let spent = sessions.reduce(0) { $0 + $1.spentMinor }
        let paid = Int64(sessions.filter { $0.spentMinor > 0 }.count)
        return MonthSummary(sessions: sessions.count, spentMinor: spent, avgSpentMinor: paid > 0 ? spent / paid : nil)
    }

    public static func spentPerMonth(_ sessions: [DaySession], last: YearMonth, calendar: Calendar, count: Int = chartMonths) -> [(YearMonth, Int64)] {
        var totals: [YearMonth: Int64] = [:]
        for s in sessions { totals[yearMonth(s.startedAt, calendar: calendar), default: 0] += s.spentMinor }
        return (0..<count).reversed().map { last.adding(months: -$0) }.map { ($0, totals[$0] ?? 0) }
    }
}

// MARK: - P22 other drinks

public struct DrinkChoice: Equatable, Sendable {
    public var kind: DrinkKind
    public var size: BeerSize
    public init(kind: DrinkKind, size: BeerSize) { self.kind = kind; self.size = size }
}

public enum Drinks {
    public static let defaultKinds = DrinkKind.allCases.filter { $0 != .CUSTOM }

    /// Typical serving; beer uses the user's default beer size. Rakı = one "tek" (5 cl).
    public static func preset(_ kind: DrinkKind, beer: BeerSize) -> BeerSize {
        switch kind {
        case .BEER, .CUSTOM: return beer
        case .WINE: return BeerSize(sizeMl: 150, abv: 12.0)
        case .RAKI: return BeerSize(sizeMl: 50, abv: 45.0)
        case .SHOT: return BeerSize(sizeMl: 40, abv: 40.0)
        case .COCKTAIL: return BeerSize(sizeMl: 200, abv: 10.0)
        }
    }

    public static func defaultChoice(_ kind: DrinkKind, beer: BeerSize) -> DrinkChoice {
        let k = defaultKinds.contains(kind) ? kind : .BEER
        return DrinkChoice(kind: k, size: BeerRules.clamp(preset(k, beer: beer)))
    }

    public static func breakdown(_ kinds: [DrinkKind]) -> [(DrinkKind, Int)] {
        DrinkKind.allCases.compactMap { k in
            let n = kinds.filter { $0 == k }.count
            return n > 0 ? (k, n) : nil
        }
    }

    public static func showBreakdown(_ kinds: [DrinkKind]) -> Bool { kinds.contains { $0 != .BEER } }
}

// MARK: - P23 drinking together

public struct GroupSetup: Equatable, Sendable {
    public var names: [String?]
    public var me: Side?
    public init(names: [String?], me: Side? = nil) { self.names = names; self.me = me }
}

public struct SeatTally: Equatable, Sendable {
    public var side: Side
    public var drinks: Int
    public var water: Int
}

public struct SeatPays: Equatable, Sendable {
    public var side: Side
    public var own: Int64
    public var pays: Int64
}

/// Framed as a shared tab, not a race: tallies stay in seat order, nothing is ranked.
public enum GroupRules {
    public static let minSeats = 2
    public static let maxSeats = 10
    public static let allSides: [Side] = [.RED, .BLUE, .GREEN, .PURPLE, .AMBER, .YELLOW, .TEAL, .PINK, .ORANGE, .GREY]

    public static func sides(_ count: Int) -> [Side] { Array(allSides.prefix(count.clamped(minSeats, maxSeats))) }
    public static func isSeatSide(_ side: Side?) -> Bool { side.map(allSides.contains) ?? false }

    public static func seats(_ sessionId: Int64, _ setup: GroupSetup) -> [GroupSeat] {
        sides(setup.names.count).enumerated().map { i, side in
            GroupSeat(sessionId: sessionId, side: side, name: VersusRules.cleanName(i < setup.names.count ? setup.names[i] : nil))
        }
    }

    public static func me(_ setup: GroupSetup) -> Side? {
        guard let me = setup.me, sides(setup.names.count).contains(me) else { return nil }
        return me
    }

    public static func ordered(_ seats: [GroupSeat]) -> [GroupSeat] {
        seats.sorted { (allSides.firstIndex(of: $0.side) ?? 0) < (allSides.firstIndex(of: $1.side) ?? 0) }
    }

    public static func tally(_ seats: [GroupSeat], beers: [BeerEvent], water: [WaterEvent]) -> [SeatTally] {
        ordered(seats).map { seat in
            SeatTally(side: seat.side, drinks: beers.filter { $0.side == seat.side }.count, water: water.filter { $0.side == seat.side }.count)
        }
    }

    public static func tab(_ seats: [GroupSeat], notes: [Note]) -> (own: [Side: Int64], shared: Int64) {
        let sides = Set(seats.map(\.side))
        var own = Dictionary(uniqueKeysWithValues: sides.map { ($0, Int64(0)) })
        var shared: Int64 = 0
        for n in notes {
            let amount = n.amountMinor ?? 0
            if let side = n.side, sides.contains(side) { own[side, default: 0] += amount } else { shared += amount }
        }
        return (own, shared)
    }

    /// Shared part split evenly; leftover cents go to the first seats so the sum is exact.
    public static func split(_ seats: [GroupSeat], notes: [Note], splitShared: Bool) -> [SeatPays] {
        let (own, shared) = tab(seats, notes: notes)
        let ordered = ordered(seats)
        guard !ordered.isEmpty else { return [] }
        let n = Int64(ordered.count)
        let base = splitShared ? shared / n : 0
        let extra = splitShared ? Int(shared % n) : 0
        return ordered.enumerated().map { i, seat in
            let mine = own[seat.side] ?? 0
            return SeatPays(side: seat.side, own: mine, pays: mine + base + (i < extra ? 1 : 0))
        }
    }
}
