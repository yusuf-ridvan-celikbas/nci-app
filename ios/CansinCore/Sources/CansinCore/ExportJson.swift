import Foundation

public struct ExportData: Sendable {
    public var sessions: [Session]
    public var beers: [BeerEvent]
    public var water: [WaterEvent]
    public var notes: [Note]
    public var friends: [FriendProfile]
    public var profile: BodyProfile?

    public init(sessions: [Session], beers: [BeerEvent], water: [WaterEvent], notes: [Note],
                friends: [FriendProfile], profile: BodyProfile?) {
        self.sessions = sessions; self.beers = beers; self.water = water; self.notes = notes
        self.friends = friends; self.profile = profile
    }
}

/// Same "cansin-export" v1 format as Android. Ordered keys, so written by hand.
public enum ExportJson {
    public static let formatVersion = 1

    private indirect enum Json {
        case null, string(String), int(Int64), double(Double), object([(String, Json)]), array([Json])
    }

    public static func build(_ data: ExportData, includeProfile: Bool, exportedAt: Millis) -> String {
        let beers = Dictionary(grouping: data.beers, by: \.sessionId)
        let water = Dictionary(grouping: data.water, by: \.sessionId)
        let notes = Dictionary(grouping: data.notes, by: \.sessionId)
        let sessions: [Json] = data.sessions.sorted { $0.startedAt < $1.startedAt }.map { s in
            .object([
                ("id", .int(s.id)),
                ("startedAt", .int(s.startedAt)),
                ("endedAt", opt(s.endedAt)),
                ("mode", .string(s.mode.rawValue)),
                ("redName", opt(s.redName)),
                ("blueName", opt(s.blueName)),
                ("redFriendId", opt(s.redFriendId)),
                ("blueFriendId", opt(s.blueFriendId)),
                ("meSide", opt(s.meSide?.rawValue)),
                ("beers", .array((beers[s.id] ?? []).map { b in
                    .object([("side", .string(b.side.rawValue)), ("sizeMl", .int(Int64(b.sizeMl))),
                             ("abv", .double(b.abv)), ("at", .int(b.at)), ("finishedAt", opt(b.finishedAt))])
                })),
                ("water", .array((water[s.id] ?? []).map { .object([("at", .int($0.at))]) })),
                ("notes", .array((notes[s.id] ?? []).map { n in
                    .object([("text", .string(n.text)), ("person", opt(n.person)), ("amountMinor", opt(n.amountMinor)),
                             ("side", opt(n.side?.rawValue)), ("createdAt", .int(n.createdAt)),
                             ("editedTime", opt(n.editedTime))])
                })),
            ])
        }
        var root: [(String, Json)] = [
            ("format", .string("cansin-export")),
            ("version", .int(Int64(formatVersion))),
            ("exportedAt", .int(exportedAt)),
            ("sessions", .array(sessions)),
            ("friends", .array(data.friends.map { .object([("id", .int($0.id)), ("name", .string($0.name))]) })),
        ]
        if includeProfile, let p = data.profile {
            root.append(("bodyProfile", .object([
                ("sex", .string(p.sex.rawValue)), ("age", .int(Int64(p.age))), ("heightCm", .int(Int64(p.heightCm))),
                ("weightKg", .int(Int64(p.weightKg))), ("license", .string(p.license.rawValue)),
            ])))
        }
        var out = ""
        write(&out, .object(root))
        return out
    }

    private static func opt(_ v: String?) -> Json { v.map { .string($0) } ?? .null }
    private static func opt(_ v: Int64?) -> Json { v.map { .int($0) } ?? .null }

    private static func write(_ out: inout String, _ value: Json) {
        switch value {
        case .null: out += "null"
        case .string(let s): string(&out, s)
        case .int(let i): out += String(i)
        case .double(let d): out += d.isFinite ? "\(d)" : "null"
        case .object(let entries):
            out += "{"
            for (i, (k, v)) in entries.enumerated() {
                if i > 0 { out += "," }
                string(&out, k)
                out += ":"
                write(&out, v)
            }
            out += "}"
        case .array(let items):
            out += "["
            for (i, v) in items.enumerated() {
                if i > 0 { out += "," }
                write(&out, v)
            }
            out += "]"
        }
    }

    private static func string(_ out: inout String, _ s: String) {
        out += "\""
        for u in s.unicodeScalars {
            switch u {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default:
                if u.value < 0x20 || u.value == 0x2028 || u.value == 0x2029 {
                    let hex = String(u.value, radix: 16)
                    out += "\\u" + String(repeating: "0", count: 4 - hex.count) + hex
                } else {
                    out.unicodeScalars.append(u)
                }
            }
        }
        out += "\""
    }
}
