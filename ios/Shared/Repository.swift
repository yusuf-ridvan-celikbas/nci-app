import Foundation
import SwiftData
import CansinCore

struct CounterSnapshot: Equatable {
    var count = 0
    var lastBeerAt: Int64?
    var sessionId: Int64?
    var versus = false
    var red = 0
    var blue = 0
    var notes = 0
    var tabMinor: Int64 = 0
    var water = 0
    var hasMe = false
    var myLatestBeer: BeerEvent?
}

enum RepoEvent {
    case beerAdded(BeerEvent, mine: Bool)
    case sessionClosed(Int64)
}

/// All writes go through here, on the main actor (one writer per process). Mirrors Android SessionRepository.
@MainActor final class Repository {
    private let container: ModelContainer
    private(set) var context: ModelContext
    private let settings: SettingsStore
    private let clock: () -> Int64
    private var pending: [RepoEvent] = []
    var onEvents: ([RepoEvent]) -> Void = { _ in }

    init(container: ModelContainer, settings: SettingsStore = .shared, clock: @escaping () -> Int64 = { AppStore.now }) {
        self.container = container
        context = ModelContext(container)
        context.autosaveEnabled = false
        self.settings = settings
        self.clock = clock
    }

    /// Fresh context so writes made by the other process (app ↔ widget) are seen.
    func refresh() {
        context = ModelContext(container)
        context.autosaveEnabled = false
    }

    // MARK: Reads

    func openSessionRecord() -> SessionRecord? {
        var fd = FetchDescriptor<SessionRecord>(predicate: #Predicate { $0.endedAt == nil }, sortBy: [SortDescriptor(\.startedAt, order: .reverse)])
        fd.fetchLimit = 1
        return try? context.fetch(fd).first
    }

    func session(_ id: Int64) -> SessionRecord? {
        var fd = FetchDescriptor<SessionRecord>(predicate: #Predicate { $0.id == id })
        fd.fetchLimit = 1
        return try? context.fetch(fd).first
    }

    func endedSessions() -> [SessionRecord] {
        let fd = FetchDescriptor<SessionRecord>(predicate: #Predicate { $0.endedAt != nil }, sortBy: [SortDescriptor(\.startedAt, order: .reverse)])
        return (try? context.fetch(fd)) ?? []
    }

    func friends() -> [FriendProfile] {
        ((try? context.fetch(FetchDescriptor<FriendRecord>(sortBy: [SortDescriptor(\.name)]))) ?? []).map(\.core)
    }

    func beers(_ s: SessionRecord) -> [BeerEvent] { s.beers.map(\.core).sorted { $0.at < $1.at } }
    func notes(_ s: SessionRecord) -> [Note] { s.notes.sorted { $0.sortTime < $1.sortTime }.map(\.core) }
    func water(_ s: SessionRecord) -> [WaterEvent] { s.water.map(\.core).sorted { $0.at < $1.at } }

    func snapshot() -> CounterSnapshot {
        guard let s = openSessionRecord(), !SessionRules.isStale(lastActivityAt: lastActivity(s), now: clock()) else { return CounterSnapshot() }
        let session = s.core
        let me = VersusRules.meSide(session)
        let versus = session.mode == .VERSUS
        let beers = beers(s)
        return CounterSnapshot(
            count: beers.count, lastBeerAt: beers.last?.at, sessionId: s.id, versus: versus,
            red: versus ? beers.filter { $0.side == .RED }.count : 0,
            blue: versus ? beers.filter { $0.side == .BLUE }.count : 0,
            notes: s.notes.count, tabMinor: s.notes.reduce(0) { $0 + ($1.amountMinor ?? 0) }, water: s.water.count,
            hasMe: me != nil, myLatestBeer: me.flatMap { side in beers.last { $0.side == side } })
    }

    // MARK: Beers

    @discardableResult
    func addBeer(finishPrevious: Bool = false, size: BeerSize? = nil, side: Side = .SOLO) -> BeerEvent? {
        let chosen = BeerRules.clamp(size ?? settings.beerSize)
        return write { insertBeer(finishPrevious: finishPrevious, size: chosen) { _ in side } }
    }

    @discardableResult
    func addMyBeer(finishPrevious: Bool = false) -> BeerEvent? {
        let chosen = BeerRules.clamp(settings.beerSize)
        return write { insertBeer(finishPrevious: finishPrevious, size: chosen) { VersusRules.meSide($0?.core) } }
    }

    private func insertBeer(finishPrevious: Bool, size: BeerSize, pickSide: (SessionRecord?) -> Side?) -> BeerEvent? {
        let now = clock()
        let active = activeSession(now)
        guard let side = pickSide(active), VersusRules.fits(side, mode: active?.core.mode) else { return nil }
        if finishPrevious, let active, let latest = latestBeer(active, side: side) { latest.finishedAt = now }
        let session = active ?? newSession(Session(startedAt: now))
        let beer = BeerRecord(id: AppStore.newId(), side: side, size: size, at: now)
        context.insert(beer)
        beer.session = session
        let event = beer.core
        pending.append(.beerAdded(event, mine: VersusRules.isMine(event, in: session.core)))
        return event
    }

    func finishBeer(id: Int64, sessionId: Int64) {
        write {
            guard let s = activeSession(clock()), s.id == sessionId, let beer = s.beers.first(where: { $0.id == id }) else { return }
            beer.finishedAt = clock()
        }
    }

    func updateBeer(_ e: BeerEvent) {
        write {
            guard let beer = beerRecord(e.id) else { return }
            let now = clock()
            let size = BeerRules.clamp(BeerSize(sizeMl: e.sizeMl, abv: e.abv))
            let at = min(e.at, now)
            beer.at = at
            beer.finishedAt = e.finishedAt.map { $0.clamped(at, now) }
            beer.sizeMl = size.sizeMl
            beer.abv = size.abv
        }
    }

    func undo(_ e: BeerEvent) {
        write {
            guard let beer = beerRecord(e.id) else { return }
            context.delete(beer)
            deleteIfEmpty(e.sessionId)
        }
    }

    func removeLatest(side: Side = .SOLO) {
        write {
            guard let s = activeSession(clock()), let latest = latestBeer(s, side: side) else { return }
            context.delete(latest)
            deleteIfEmpty(s.id)
        }
    }

    // MARK: Sessions

    func startVersus(_ setup: VersusSetup) {
        write {
            let now = clock()
            if let s = activeSession(now) { close(s) }
            _ = newSession(VersusRules.apply(Session(startedAt: now, mode: .VERSUS), setup))
        }
    }

    func updateVersus(_ setup: VersusSetup) {
        write {
            guard let s = activeSession(clock()), s.core.mode == .VERSUS else { return }
            s.apply(VersusRules.apply(s.core, setup))
        }
    }

    func endSession() {
        write { if let s = openSessionRecord() { close(s) } }
    }

    func closeIfStale() { write { _ = activeSession(clock()) } }

    func deleteEnded(_ id: Int64) {
        write {
            guard let s = session(id), s.endedAt != nil else { return }
            context.delete(s)
        }
    }

    // MARK: Notes

    @discardableResult
    func addNote(text: String, amountMinor: Int64?, side: Side? = nil) -> Note? {
        write {
            guard let clean = NoteRules.cleanText(text) else { return nil }
            let now = clock()
            let session = activeSession(now) ?? newSession(Session(startedAt: now))
            let note = NoteRecord(id: AppStore.newId(), text: clean,
                                  amountMinor: NoteRules.isValidAmount(amountMinor) ? amountMinor : nil,
                                  createdAt: now, side: VersusRules.noteSide(side, mode: session.core.mode))
            context.insert(note)
            note.session = session
            return note.core
        }
    }

    func updateNote(_ n: Note) {
        write {
            guard let clean = NoteRules.cleanText(n.text), let note = noteRecord(n.id) else { return }
            let mode = openSessionRecord().flatMap { $0.id == n.sessionId ? $0.core.mode : nil } ?? .SOLO
            note.text = clean
            note.sideRaw = VersusRules.noteSide(n.side, mode: mode)?.rawValue
            note.person = NoteRules.cleanPerson(n.person)
            note.amountMinor = NoteRules.isValidAmount(n.amountMinor) ? n.amountMinor : nil
            note.editedTime = n.editedTime
        }
    }

    func deleteNote(_ n: Note) {
        write {
            guard let note = noteRecord(n.id) else { return }
            context.delete(note)
            deleteIfEmpty(n.sessionId)
        }
    }

    // MARK: Water

    @discardableResult
    func addWater() -> WaterEvent? {
        write {
            guard let s = activeSession(clock()) else { return nil }
            let w = WaterRecord(id: AppStore.newId(), at: clock())
            context.insert(w)
            w.session = s
            return w.core
        }
    }

    func undoWater(_ e: WaterEvent) {
        write {
            let id = e.id
            guard let w = fetchOne(WaterRecord.self, #Predicate { $0.id == id }) else { return }
            context.delete(w)
            deleteIfEmpty(e.sessionId)
        }
    }

    // MARK: Friends

    func saveFriend(name: String, color: Int) -> Int64? {
        write {
            guard let clean = VersusRules.cleanName(name) else { return nil }
            if let existing = friends().first(where: { $0.name == clean }) { return existing.id }
            let f = FriendRecord(id: AppStore.newId(), name: clean, color: color)
            context.insert(f)
            return f.id
        }
    }

    func deleteFriend(_ id: Int64) {
        write {
            guard let f = fetchOne(FriendRecord.self, #Predicate { $0.id == id }) else { return }
            context.delete(f)
            for s in (try? context.fetch(FetchDescriptor<SessionRecord>())) ?? [] {
                if s.redFriendId == id { s.redFriendId = nil }
                if s.blueFriendId == id { s.blueFriendId = nil }
            }
        }
    }

    // MARK: Export / wipe

    func exportData(includeProfile: Bool) -> ExportData {
        let sessions = (try? context.fetch(FetchDescriptor<SessionRecord>())) ?? []
        return ExportData(sessions: sessions.map(\.core), beers: sessions.flatMap { $0.beers.map(\.core) },
                          water: sessions.flatMap { $0.water.map(\.core) }, notes: sessions.flatMap { $0.notes.map(\.core) },
                          friends: friends(), profile: includeProfile ? settings.profile : nil)
    }

    func deleteAll() {
        write {
            try? context.delete(model: SessionRecord.self)
            try? context.delete(model: BeerRecord.self)
            try? context.delete(model: WaterRecord.self)
            try? context.delete(model: NoteRecord.self)
            try? context.delete(model: FriendRecord.self)
        }
    }

    // MARK: Internals

    @discardableResult
    private func write<T>(_ block: () -> T) -> T {
        context.rollback()
        let result = block()
        try? context.save()
        let events = pending
        pending = []
        onEvents(events)
        return result
    }

    private func newSession(_ s: Session) -> SessionRecord {
        var copy = s
        copy.id = AppStore.newId()
        let r = SessionRecord(copy)
        context.insert(r)
        return r
    }

    private func activeSession(_ now: Int64) -> SessionRecord? {
        guard let s = openSessionRecord() else { return nil }
        let last = lastActivity(s)
        guard SessionRules.isStale(lastActivityAt: last, now: now) else { return s }
        s.endedAt = last
        pending.append(.sessionClosed(s.id))
        return nil
    }

    private func lastActivity(_ s: SessionRecord) -> Int64 {
        max(s.startedAt, s.beers.map(\.at).max() ?? 0, s.notes.map(\.createdAt).max() ?? 0, s.water.map(\.at).max() ?? 0)
    }

    private func close(_ s: SessionRecord) {
        let id = s.id
        if isEmpty(s) { context.delete(s) } else { s.endedAt = clock() }
        pending.append(.sessionClosed(id))
    }

    private func deleteIfEmpty(_ sessionId: Int64) {
        guard let s = session(sessionId), isEmpty(s), s.endedAt == nil, s.core.mode != .VERSUS else { return }
        context.delete(s)
        pending.append(.sessionClosed(sessionId))
    }

    private func isEmpty(_ s: SessionRecord) -> Bool {
        s.beers.allSatisfy(\.isDeleted) && s.notes.allSatisfy(\.isDeleted) && s.water.allSatisfy(\.isDeleted)
    }

    private func latestBeer(_ s: SessionRecord, side: Side) -> BeerRecord? {
        s.beers.filter { $0.sideRaw == side.rawValue && !$0.isDeleted }.max { $0.at < $1.at }
    }

    private func beerRecord(_ id: Int64) -> BeerRecord? { fetchOne(BeerRecord.self, #Predicate { $0.id == id }) }
    private func noteRecord(_ id: Int64) -> NoteRecord? { fetchOne(NoteRecord.self, #Predicate { $0.id == id }) }

    private func fetchOne<M: PersistentModel>(_ type: M.Type, _ predicate: Predicate<M>) -> M? {
        var fd = FetchDescriptor<M>(predicate: predicate)
        fd.fetchLimit = 1
        return try? context.fetch(fd).first
    }
}
