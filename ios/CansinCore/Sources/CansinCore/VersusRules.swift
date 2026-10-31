public struct VersusSetup: Equatable, Sendable {
    public var redName: String?
    public var blueName: String?
    public var redFriendId: Int64?
    public var blueFriendId: Int64?
    public var meSide: Side?

    public init(redName: String? = nil, blueName: String? = nil, redFriendId: Int64? = nil,
                blueFriendId: Int64? = nil, meSide: Side? = nil) {
        self.redName = redName; self.blueName = blueName; self.redFriendId = redFriendId
        self.blueFriendId = blueFriendId; self.meSide = meSide
    }
}

public struct TabTotals: Equatable, Sendable {
    public var red: Int64 = 0
    public var blue: Int64 = 0
    public var shared: Int64 = 0
    public var total: Int64 { red + blue + shared }

    public init(red: Int64 = 0, blue: Int64 = 0, shared: Int64 = 0) {
        self.red = red; self.blue = blue; self.shared = shared
    }
}

public enum VersusRules {
    public static func cleanName(_ raw: String?) -> String? { raw?.cleaned(max: FriendProfile.maxName) }

    public static func apply(_ session: Session, _ setup: VersusSetup) -> Session {
        var s = session
        s.redName = cleanName(setup.redName)
        s.blueName = cleanName(setup.blueName)
        s.redFriendId = setup.redFriendId
        s.blueFriendId = setup.blueFriendId
        s.meSide = setup.meSide == .SOLO ? nil : setup.meSide
        return s
    }

    public static func fits(_ side: Side, mode: SessionMode?) -> Bool {
        side == .SOLO ? mode != .VERSUS : mode == .VERSUS
    }

    public static func meSide(_ session: Session?) -> Side? {
        session?.mode == .VERSUS ? session?.meSide : .SOLO
    }

    public static func isMine(_ event: BeerEvent, in session: Session) -> Bool { event.side == meSide(session) }

    public static func noteSide(_ side: Side?, mode: SessionMode) -> Side? {
        guard let side, mode == .VERSUS, side != .SOLO else { return nil }
        return side
    }

    public static func totals(_ notes: [Note]) -> TabTotals {
        notes.reduce(into: TabTotals()) { acc, note in
            let amount = note.amountMinor ?? 0
            switch note.side {
            case .RED: acc.red += amount
            case .BLUE: acc.blue += amount
            default: acc.shared += amount
            }
        }
    }
}
