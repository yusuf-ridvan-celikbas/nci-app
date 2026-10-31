import Foundation
import SwiftData
import CansinCore

@Model final class SessionRecord {
    @Attribute(.unique) var id: Int64
    var startedAt: Int64
    var endedAt: Int64?
    var modeRaw: String
    var redName: String?
    var blueName: String?
    var redFriendId: Int64?
    var blueFriendId: Int64?
    var meSideRaw: String?
    @Relationship(deleteRule: .cascade, inverse: \BeerRecord.session) var beers: [BeerRecord] = []
    @Relationship(deleteRule: .cascade, inverse: \WaterRecord.session) var water: [WaterRecord] = []
    @Relationship(deleteRule: .cascade, inverse: \NoteRecord.session) var notes: [NoteRecord] = []

    init(_ s: Session) {
        id = s.id; startedAt = s.startedAt; endedAt = s.endedAt; modeRaw = s.mode.rawValue
        redName = s.redName; blueName = s.blueName; redFriendId = s.redFriendId
        blueFriendId = s.blueFriendId; meSideRaw = s.meSide?.rawValue
    }

    var core: Session {
        Session(id: id, startedAt: startedAt, endedAt: endedAt, mode: SessionMode(rawValue: modeRaw) ?? .SOLO,
                redName: redName, blueName: blueName, redFriendId: redFriendId, blueFriendId: blueFriendId,
                meSide: meSideRaw.flatMap(Side.init(rawValue:)))
    }

    func apply(_ s: Session) {
        endedAt = s.endedAt; modeRaw = s.mode.rawValue; redName = s.redName; blueName = s.blueName
        redFriendId = s.redFriendId; blueFriendId = s.blueFriendId; meSideRaw = s.meSide?.rawValue
    }
}

@Model final class BeerRecord {
    @Attribute(.unique) var id: Int64
    var sideRaw: String
    var sizeMl: Int
    var abv: Double
    var at: Int64
    var finishedAt: Int64?
    var session: SessionRecord?

    init(id: Int64, side: Side, size: BeerSize, at: Int64) {
        self.id = id; sideRaw = side.rawValue; sizeMl = size.sizeMl; abv = size.abv; self.at = at
    }

    var core: BeerEvent {
        BeerEvent(id: id, sessionId: session?.id ?? 0, side: Side(rawValue: sideRaw) ?? .SOLO,
                  sizeMl: sizeMl, abv: abv, at: at, finishedAt: finishedAt)
    }
}

@Model final class WaterRecord {
    @Attribute(.unique) var id: Int64
    var at: Int64
    var session: SessionRecord?

    init(id: Int64, at: Int64) { self.id = id; self.at = at }

    var core: WaterEvent { WaterEvent(id: id, sessionId: session?.id ?? 0, at: at) }
}

@Model final class NoteRecord {
    @Attribute(.unique) var id: Int64
    var text: String
    var person: String?
    var amountMinor: Int64?
    var createdAt: Int64
    var editedTime: Int64?
    var sideRaw: String?
    var session: SessionRecord?

    init(id: Int64, text: String, amountMinor: Int64?, createdAt: Int64, side: Side?) {
        self.id = id; self.text = text; self.amountMinor = amountMinor; self.createdAt = createdAt
        sideRaw = side?.rawValue
    }

    var core: Note {
        Note(id: id, sessionId: session?.id ?? 0, text: text, person: person, amountMinor: amountMinor,
             createdAt: createdAt, editedTime: editedTime, side: sideRaw.flatMap(Side.init(rawValue:)))
    }

    var sortTime: Int64 { editedTime ?? createdAt }
}

@Model final class FriendRecord {
    @Attribute(.unique) var id: Int64
    var name: String
    var color: Int

    init(id: Int64, name: String, color: Int) { self.id = id; self.name = name; self.color = color }

    var core: FriendProfile { FriendProfile(id: id, name: name, color: color) }
}

enum AppStore {
    static let appGroup = "group.com.cansin.app"
    static let schema = Schema([SessionRecord.self, BeerRecord.self, WaterRecord.self, NoteRecord.self, FriendRecord.self])

    /// Store lives in the App Group so the widget can read it. Files use complete protection:
    /// unreadable while the device is locked (the widget then shows a neutral placeholder).
    /// Retried on each access until it opens, since the first attempt may happen while locked.
    @MainActor static var container: ModelContainer? {
        if let cached { return cached }
        cached = makeContainer()
        return cached
    }

    @MainActor private static var cached: ModelContainer?

    private static func makeContainer() -> ModelContainer? {
        guard let base = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) else { return nil }
        let dir = base.appendingPathComponent("Store", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true,
                                                 attributes: [.protectionKey: FileProtectionType.complete])
        let config = ModelConfiguration(schema: schema, url: dir.appendingPathComponent("cansin.store"),
                                        allowsSave: true, cloudKitDatabase: .none)
        return try? ModelContainer(for: schema, configurations: [config])
    }

    static var now: Int64 { Int64((Date().timeIntervalSince1970 * 1000).rounded()) }

    /// Random ids below 2^53 so they stay exact in JSON exports.
    static func newId() -> Int64 { Int64.random(in: 1...(Int64(1) << 53)) }
}
