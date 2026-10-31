import Foundation

/// A finished-session result shared between friends as text (P13, no network). Same format as Android.
public struct ResultCard: Equatable, Sendable {
    public var name: String
    /// ISO yyyy-MM-dd (calendar day, no time zone).
    public var day: String
    public var beers: Int
    public var durationMin: Int
    public var water: Int

    public init(name: String, day: String, beers: Int, durationMin: Int, water: Int) {
        self.name = name; self.day = day; self.beers = beers; self.durationMin = durationMin; self.water = water
    }
}

public enum ResultCards {
    public static let prefix = "cansin:1:"
    public static let maxBeers = 100
    public static let maxWater = 100
    public static let maxDurationMin = 72 * 60
    private static let maxText = 5_000
    private static let maxPayload = 400
    private static let earliest = "2020-01-01"

    /// Trimmed, ≤30 chars, no control or bidi-override characters (anti-spoofing).
    public static func cleanName(_ raw: String?) -> String? {
        guard let name = VersusRules.cleanName(raw), !name.unicodeScalars.contains(where: isUnsafe) else { return nil }
        return name
    }

    public static func card(name: String?, day: String, beers: Int, durationMs: Int64, water: Int) -> ResultCard? {
        guard let clean = cleanName(name), isValidDay(day) else { return nil }
        return ResultCard(name: clean, day: day, beers: beers.clamped(0, maxBeers),
                          durationMin: Int((durationMs / 60_000).clamped(0, Int64(maxDurationMin))), water: water.clamped(0, maxWater))
    }

    public static func encode(_ card: ResultCard) -> String {
        let body = [card.name, card.day, String(card.beers), String(card.durationMin), String(card.water)].joined(separator: "\n")
        let b64 = Data(body.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        return prefix + b64
    }

    /// Finds the first code anywhere in pasted text. `today` is yyyy-MM-dd; cards up to one day ahead are accepted.
    public static func decode(_ text: String, today: String) -> ResultCard? {
        guard text.count <= maxText, let payload = findPayload(text), let data = base64url(payload),
              let body = String(data: data, encoding: .utf8) else { return nil }
        let parts = body.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 5, let name = cleanName(parts[0]), name == parts[0], isValidDay(parts[1]),
              parts[1] >= earliest, parts[1] <= dayAfter(today),
              let beers = number(parts[2], max: maxBeers), let duration = number(parts[3], max: maxDurationMin),
              let water = number(parts[4], max: maxWater) else { return nil }
        return ResultCard(name: name, day: parts[1], beers: beers, durationMin: duration, water: water)
    }

    public static func day(of date: Date, calendar: Calendar = .current) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    private static func findPayload(_ text: String) -> String? {
        let lower = text.lowercased()
        guard let range = lower.range(of: prefix) else { return nil }
        let rest = text[range.upperBound...]
        let payload = rest.prefix { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") }
        return (8...maxPayload).contains(payload.count) ? String(payload) : nil
    }

    private static func base64url(_ s: String) -> Data? {
        var b64 = s.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        if b64.count % 4 == 1 { return nil }
        b64 += String(repeating: "=", count: (4 - b64.count % 4) % 4)
        return Data(base64Encoded: b64)
    }

    private static func number(_ raw: String, max: Int) -> Int? {
        guard (1...4).contains(raw.count), Digits.isAsciiDigits(raw), let v = Int(raw), v <= max else { return nil }
        return v
    }

    private static func isValidDay(_ s: String) -> Bool {
        let p = s.split(separator: "-", omittingEmptySubsequences: false)
        guard s.count == 10, p.count == 3, p.allSatisfy({ Digits.isAsciiDigits(String($0)) }),
              let y = Int(p[0]), let m = Int(p[1]), let d = Int(p[2]) else { return false }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        guard let date = cal.date(from: DateComponents(year: y, month: m, day: d)) else { return false }
        let back = cal.dateComponents([.year, .month, .day], from: date)
        return back.year == y && back.month == m && back.day == d
    }

    private static func dayAfter(_ day: String) -> String {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let p = day.split(separator: "-").compactMap { Int($0) }
        guard p.count == 3, let date = cal.date(from: DateComponents(year: p[0], month: p[1], day: p[2])),
              let next = cal.date(byAdding: .day, value: 1, to: date) else { return day }
        return Self.day(of: next, calendar: cal)
    }

    private static func isUnsafe(_ u: Unicode.Scalar) -> Bool {
        let v = u.value
        return v < 0x20 || (0x7F...0x9F).contains(v) || (0x202A...0x202E).contains(v) || (0x2066...0x2069).contains(v)
            || v == 0x200E || v == 0x200F || v == 0x2028 || v == 0x2029 || v == 0xFEFF
    }
}
