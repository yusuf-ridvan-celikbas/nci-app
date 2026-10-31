import Foundation

public enum AmountInput: Equatable, Sendable {
    case empty
    case invalid
    case valid(Int64)
}

public enum NoteRules {
    public static let maxAmountMinor: Int64 = 100_000_000

    public static func cleanText(_ raw: String) -> String? { raw.cleaned(max: Note.maxText) }

    public static func cleanPerson(_ raw: String?) -> String? { raw?.cleaned(max: Note.maxPerson) }

    public static func isValidAmount(_ minor: Int64?) -> Bool {
        guard let minor else { return true }
        return (0...maxAmountMinor).contains(minor)
    }

    public static func parseAmount(_ raw: String) -> AmountInput {
        if raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return .empty }
        guard let p = Digits.split(raw, maxWhole: 9, maxFraction: 2) else { return .invalid }
        let fraction = Int64(p.fraction.padding(toLength: 2, withPad: "0", startingAt: 0))!
        let minor = p.whole * 100 + fraction
        return minor <= maxAmountMinor ? .valid(minor) : .invalid
    }

    public static func formatAmount(_ minor: Int64, separator: Character) -> String {
        let cents = minor % 100
        return "\(minor / 100)\(separator)\(cents < 10 ? "0" : "")\(cents)"
    }

    /// The picked wall-clock time on the day before, of, or after `createdAt` — whichever is closest.
    public static func pickTime(createdAt: Millis, hour: Int, minute: Int, calendar: Calendar) -> Millis {
        let created = Date(timeIntervalSince1970: Double(createdAt) / 1000)
        guard let sameDay = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: created) else { return createdAt }
        return [-1, 0, 1]
            .compactMap { calendar.date(byAdding: .day, value: $0, to: sameDay) }
            .map { Millis(($0.timeIntervalSince1970 * 1000).rounded()) }
            .min { abs($0 - createdAt) < abs($1 - createdAt) } ?? createdAt
    }
}
