import Foundation

public typealias Millis = Int64

public enum SessionMode: String, Codable, CaseIterable, Sendable { case SOLO, VERSUS }

public enum Side: String, Codable, CaseIterable, Sendable { case SOLO, RED, BLUE }

public struct Session: Equatable, Identifiable, Sendable {
    public var id: Int64
    public var startedAt: Millis
    public var endedAt: Millis?
    public var mode: SessionMode
    public var redName: String?
    public var blueName: String?
    public var redFriendId: Int64?
    public var blueFriendId: Int64?
    public var meSide: Side?

    public init(id: Int64 = 0, startedAt: Millis, endedAt: Millis? = nil, mode: SessionMode = .SOLO,
                redName: String? = nil, blueName: String? = nil, redFriendId: Int64? = nil,
                blueFriendId: Int64? = nil, meSide: Side? = nil) {
        self.id = id; self.startedAt = startedAt; self.endedAt = endedAt; self.mode = mode
        self.redName = redName; self.blueName = blueName; self.redFriendId = redFriendId
        self.blueFriendId = blueFriendId; self.meSide = meSide
    }
}

public struct BeerEvent: Equatable, Identifiable, Sendable {
    public static let defaultSizeMl = 500
    public static let defaultAbv = 5.0

    public var id: Int64
    public var sessionId: Int64
    public var side: Side
    public var sizeMl: Int
    public var abv: Double
    public var at: Millis
    public var finishedAt: Millis?

    public init(id: Int64 = 0, sessionId: Int64, side: Side = .SOLO, sizeMl: Int = BeerEvent.defaultSizeMl,
                abv: Double = BeerEvent.defaultAbv, at: Millis, finishedAt: Millis? = nil) {
        self.id = id; self.sessionId = sessionId; self.side = side; self.sizeMl = sizeMl
        self.abv = abv; self.at = at; self.finishedAt = finishedAt
    }
}

public struct WaterEvent: Equatable, Identifiable, Sendable {
    public var id: Int64
    public var sessionId: Int64
    public var at: Millis

    public init(id: Int64 = 0, sessionId: Int64, at: Millis) {
        self.id = id; self.sessionId = sessionId; self.at = at
    }
}

public struct Note: Equatable, Identifiable, Sendable {
    public static let maxText = 1000
    public static let maxPerson = 30

    public var id: Int64
    public var sessionId: Int64
    public var text: String
    public var person: String?
    public var amountMinor: Int64?
    public var createdAt: Millis
    public var editedTime: Millis?
    public var side: Side?

    public init(id: Int64 = 0, sessionId: Int64, text: String, person: String? = nil, amountMinor: Int64? = nil,
                createdAt: Millis, editedTime: Millis? = nil, side: Side? = nil) {
        self.id = id; self.sessionId = sessionId; self.text = text; self.person = person
        self.amountMinor = amountMinor; self.createdAt = createdAt; self.editedTime = editedTime; self.side = side
    }
}

public struct FriendProfile: Equatable, Identifiable, Sendable {
    public static let maxName = 30

    public var id: Int64
    public var name: String
    public var color: Int

    public init(id: Int64 = 0, name: String, color: Int) {
        self.id = id; self.name = name; self.color = color
    }
}

extension Comparable {
    public func clamped(_ lo: Self, _ hi: Self) -> Self { Swift.min(Swift.max(self, lo), hi) }
}

extension String {
    /// Trimmed, cut to `max` characters, nil when empty.
    func cleaned(max: Int) -> String? {
        let t = String(trimmingCharacters(in: .whitespacesAndNewlines).prefix(max))
        return t.isEmpty ? nil : t
    }
}

enum Digits {
    /// Parses `digits{1,maxWhole}` optionally followed by `.`/`,` and `digits{1,maxFraction}`.
    static func split(_ raw: String, maxWhole: Int, maxFraction: Int) -> (whole: Int64, fraction: String)? {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = s.split(omittingEmptySubsequences: false, whereSeparator: { $0 == "." || $0 == "," })
        guard parts.count == 1 || parts.count == 2 else { return nil }
        let whole = String(parts[0])
        guard (1...maxWhole).contains(whole.count), isAsciiDigits(whole) else { return nil }
        var fraction = ""
        if parts.count == 2 {
            fraction = String(parts[1])
            guard (1...maxFraction).contains(fraction.count), isAsciiDigits(fraction) else { return nil }
        }
        return (Int64(whole)!, fraction)
    }

    static func isAsciiDigits(_ s: String) -> Bool {
        !s.isEmpty && s.unicodeScalars.allSatisfy { $0.value >= 48 && $0.value <= 57 }
    }
}
