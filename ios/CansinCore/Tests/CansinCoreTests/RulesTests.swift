import Foundation
import XCTest
@testable import CansinCore

final class BeerRulesTests: XCTestCase {
    func testParsesCentilitres() {
        XCTAssertEqual(BeerRules.parseCl("33"), 330)
        XCTAssertEqual(BeerRules.parseCl("56,8"), 568)
        XCTAssertEqual(BeerRules.parseCl(" 40.0 "), 400)
        XCTAssertEqual(BeerRules.parseCl("10"), BeerRules.minMl)
        XCTAssertEqual(BeerRules.parseCl("200"), BeerRules.maxMl)
    }

    func testRejectsInvalidCentilitres() {
        for raw in ["", "9.9", "200.1", "-33", "33.25", "abc", "1e2", ",5", "٣٣"] { XCTAssertNil(BeerRules.parseCl(raw), raw) }
    }

    func testParsesAbv() {
        XCTAssertEqual(BeerRules.parseAbv("5"), 5.0)
        XCTAssertEqual(BeerRules.parseAbv("4,7")!, 4.7, accuracy: 1e-9)
        XCTAssertEqual(BeerRules.parseAbv("0"), 0.0)
        for raw in ["20.1", "-1", "5.55", ""] { XCTAssertNil(BeerRules.parseAbv(raw), raw) }
    }

    func testFormats() {
        XCTAssertEqual(BeerRules.formatCl(500, separator: ","), "50")
        XCTAssertEqual(BeerRules.formatCl(568, separator: ","), "56,8")
        XCTAssertEqual(BeerRules.formatAbv(5.0, separator: "."), "5.0")
        XCTAssertEqual(BeerRules.formatAbv(4.7, separator: ","), "4,7")
    }

    func testClampsOutOfRange() {
        XCTAssertEqual(BeerRules.clamp(BeerSize(sizeMl: 99_999, abv: -3)), BeerSize(sizeMl: BeerRules.maxMl, abv: BeerRules.minAbv))
    }
}

final class NoteRulesTests: XCTestCase {
    private var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Europe/Istanbul")!
        return c
    }()

    private func at(_ day: Int, _ hour: Int, _ minute: Int) -> Millis {
        let d = calendar.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour, minute: minute))!
        return Millis(d.timeIntervalSince1970 * 1000)
    }

    func testParsesAmounts() {
        XCTAssertEqual(NoteRules.parseAmount("  "), .empty)
        XCTAssertEqual(NoteRules.parseAmount("12.5"), .valid(1250))
        XCTAssertEqual(NoteRules.parseAmount("12,50"), .valid(1250))
        XCTAssertEqual(NoteRules.parseAmount("7"), .valid(700))
        XCTAssertEqual(NoteRules.parseAmount("0.05"), .valid(5))
    }

    func testRejectsInvalidAmounts() {
        for raw in ["-1", "1.234", "abc", "1e5", "1.2.3", ".5", "9999999999"] { XCTAssertEqual(NoteRules.parseAmount(raw), .invalid, raw) }
        XCTAssertEqual(NoteRules.parseAmount("1000000.01"), .invalid)
        XCTAssertEqual(NoteRules.parseAmount("1000000"), .valid(NoteRules.maxAmountMinor))
    }

    func testFormatsAmounts() {
        XCTAssertEqual(NoteRules.formatAmount(1205, separator: ","), "12,05")
        XCTAssertEqual(NoteRules.formatAmount(0, separator: "."), "0.00")
    }

    func testCleansText() {
        XCTAssertNil(NoteRules.cleanText("  "))
        XCTAssertEqual(NoteRules.cleanText("  hi "), "hi")
        XCTAssertNil(NoteRules.cleanPerson(" "))
        XCTAssertEqual(NoteRules.cleanText(String(repeating: "a", count: 2000))?.count, Note.maxText)
    }

    func testPickTime() {
        XCTAssertEqual(NoteRules.pickTime(createdAt: at(30, 22, 0), hour: 21, minute: 15, calendar: calendar), at(30, 21, 15))
        XCTAssertEqual(NoteRules.pickTime(createdAt: at(31, 0, 30), hour: 23, minute: 50, calendar: calendar), at(30, 23, 50))
        XCTAssertEqual(NoteRules.pickTime(createdAt: at(30, 23, 40), hour: 0, minute: 10, calendar: calendar), at(31, 0, 10))
    }
}

final class PromilleTests: XCTestCase {
    private let min = PromilleModel.minuteMs
    private let t0: Millis = 1_000_000_000
    private let male = BodyProfile(sex: .MALE, age: 30, heightCm: 180, weightKg: 80, license: .NORMAL)

    func testGramsAndBodyWater() {
        XCTAssertEqual(PromilleModel.grams(sizeMl: 500, abv: 5), 19.725, accuracy: 1e-9)
        XCTAssertEqual(PromilleModel.totalBodyWater(male), 45.8202, accuracy: 1e-3)
        var female = male
        female.sex = .FEMALE
        XCTAssertEqual(PromilleModel.totalBodyWater(female), -2.097 + 0.1069 * 180 + 0.2466 * 80, accuracy: 1e-9)
    }

    func testSingleBeerPeakAndTimeToZero() {
        let r = PromilleModel.estimate(profile: male, drinks: [Drink(at: t0, finishedAt: nil, sizeMl: 500, abv: 5)], now: t0 + 50 * min)
        XCTAssertEqual(r.peak, 0.21, accuracy: 1e-9)
        XCTAssertEqual(r.current, 0.21, accuracy: 1e-9)
        XCTAssertTrue((t0 + 135 * min...t0 + 140 * min).contains(r.zeroAt!))
    }

    func testBeforeAndAfterDrinkingIsZero() {
        let drinks = [Drink(at: t0, finishedAt: t0 + 10 * min, sizeMl: 330, abv: 5)]
        XCTAssertEqual(PromilleModel.estimate(profile: male, drinks: drinks, now: t0 - min).current, 0)
        let later = PromilleModel.estimate(profile: male, drinks: drinks, now: t0 + 24 * 60 * min)
        XCTAssertEqual(later.current, 0)
        XCTAssertNil(later.zeroAt)
        XCTAssertGreaterThan(later.peak, 0)
        XCTAssertEqual(PromilleModel.estimate(profile: male, drinks: [], now: t0), PromilleEstimate(current: 0, peak: 0, zeroAt: nil))
    }

    func testDrinkEnd() {
        let d = [Drink(at: t0, finishedAt: nil, sizeMl: 500, abv: 5), Drink(at: t0 + 10 * min, finishedAt: nil, sizeMl: 500, abv: 5),
                 Drink(at: t0 + 60 * min, finishedAt: nil, sizeMl: 500, abv: 5)]
        XCTAssertEqual(PromilleModel.drinkEnd(d, index: 0), t0 + 10 * min)
        XCTAssertEqual(PromilleModel.drinkEnd(d, index: 1), t0 + 30 * min)
        XCTAssertEqual(PromilleModel.drinkEnd(d, index: 2), t0 + 80 * min)
        XCTAssertEqual(PromilleModel.drinkEnd([Drink(at: t0, finishedAt: t0 - min, sizeMl: 500, abv: 5)], index: 0), t0)
    }

    func testUnspecifiedIsMoreConservative() {
        var unspecified = male
        unspecified.sex = .UNSPECIFIED
        let d = [Drink(at: t0, finishedAt: nil, sizeMl: 500, abv: 5)]
        XCTAssertGreaterThan(PromilleModel.estimate(profile: unspecified, drinks: d, now: t0 + 50 * min).peak,
                             PromilleModel.estimate(profile: male, drinks: d, now: t0 + 50 * min).peak)
    }

    func testRoundsUp() {
        XCTAssertEqual(PromilleModel.toPromille(0.1055), 0.10, accuracy: 1e-9)
        XCTAssertEqual(PromilleModel.toPromille(0.10551), 0.11, accuracy: 1e-9)
        XCTAssertEqual(PromilleModel.toPromille(0), 0)
    }
}

final class WarningTests: XCTestCase {
    func testEnglishGenericOnly() {
        XCTAssertEqual(WarningRules.warnings(promille: 3.5, license: .NORMAL, turkish: false, hour: 22), [.generic])
    }

    func testLimits() {
        XCTAssertEqual(WarningRules.warnings(promille: 0.50, license: .NORMAL, turkish: true, hour: 22), [.overLimit(0.50)])
        XCTAssertEqual(WarningRules.warnings(promille: 0.49, license: .NORMAL, turkish: true, hour: 22), [.underLimit])
        XCTAssertEqual(WarningRules.warnings(promille: 0.20, license: .CANDIDATE, turkish: true, hour: 22), [.overLimit(0.20)])
        XCTAssertEqual(WarningRules.warnings(promille: 0, license: .NORMAL, turkish: true, hour: 22), [])
        XCTAssertNil(TrDrivingLaw.limits[.NONE])
    }

    func testGreetingsAndHealth() {
        XCTAssertEqual(WarningRules.warnings(promille: 0.3, license: .NONE, turkish: true, hour: 5), [.hello(.morning)])
        XCTAssertEqual(WarningRules.greeting(hour: 12), .day)
        XCTAssertEqual(WarningRules.greeting(hour: 18), .evening)
        XCTAssertEqual(WarningRules.greeting(hour: 4), .evening)
        XCTAssertEqual(WarningRules.warnings(promille: 1.5, license: .NONE, turkish: true, hour: 23), [.hello(.evening), .slowDown])
        XCTAssertEqual(WarningRules.warnings(promille: 3, license: .NORMAL, turkish: true, hour: 23), [.overLimit(0.5), .poisoning])
    }

    func testProfileParsing() {
        XCTAssertEqual(BodyProfile.parse(" 18 ", range: BodyProfile.ageRange), 18)
        XCTAssertNil(BodyProfile.parse("17", range: BodyProfile.ageRange))
        XCTAssertNil(BodyProfile.parse("80.5", range: BodyProfile.weightRange))
        XCTAssertNil(BodyProfile.parse("1000", range: BodyProfile.heightRange))
        XCTAssertFalse(BodyProfile(sex: .MALE, age: 30, heightCm: 119, weightKg: 80, license: .NONE).isValid)
    }
}

final class SmallRulesTests: XCTestCase {
    func testSessionStaleness() {
        let six = SessionRules.defaultInactivityMs
        XCTAssertFalse(SessionRules.isStale(lastActivityAt: 0, now: six - 1))
        XCTAssertTrue(SessionRules.isStale(lastActivityAt: 0, now: six))
        XCTAssertFalse(SessionRules.isStale(lastActivityAt: 10_000, now: 0))
    }

    func testWater() {
        XCTAssertEqual(WaterRules.beersSinceWater(myBeerTimes: [1, 2, 3], lastWaterAt: nil), 3)
        XCTAssertEqual(WaterRules.beersSinceWater(myBeerTimes: [1, 2, 3], lastWaterAt: 2), 1)
        XCTAssertFalse(WaterRules.shouldNudge(enabled: true, every: 2, since: 1, dismissedAt: nil))
        XCTAssertTrue(WaterRules.shouldNudge(enabled: true, every: 2, since: 2, dismissedAt: nil))
        XCTAssertFalse(WaterRules.shouldNudge(enabled: false, every: 2, since: 5, dismissedAt: nil))
        XCTAssertFalse(WaterRules.shouldNudge(enabled: true, every: 2, since: 2, dismissedAt: 2))
        XCTAssertTrue(WaterRules.shouldNudge(enabled: true, every: 2, since: 3, dismissedAt: 2))
        XCTAssertEqual(WaterRules.clamp(0), 1)
        XCTAssertEqual(WaterRules.clamp(99), 10)
    }

    func testTimer() {
        let beer = BeerEvent(id: 3, sessionId: 1, at: 0)
        let running = TimerState(beerId: 3, sessionId: 1, startedAt: 100)
        XCTAssertTrue(TimerRules.canStart(enabled: true, state: TimerState(), myLatestBeer: beer))
        XCTAssertFalse(TimerRules.canStart(enabled: false, state: TimerState(), myLatestBeer: beer))
        XCTAssertFalse(TimerRules.canStart(enabled: true, state: running, myLatestBeer: beer))
        XCTAssertFalse(TimerRules.canStart(enabled: true, state: TimerState(), myLatestBeer: nil))
        var finished = beer
        finished.finishedAt = 5
        XCTAssertFalse(TimerRules.canStart(enabled: true, state: TimerState(), myLatestBeer: finished))
        XCTAssertTrue(TimerRules.isCurrent(running, sessionId: 1))
        XCTAssertFalse(TimerRules.isCurrent(running, sessionId: 2))
        XCTAssertEqual(TimerRules.step(.beer, -1), .water)
        XCTAssertEqual(TimerRules.step(.water, 1), .beer)
        XCTAssertEqual(TimerRules.page(4), .tab)
        XCTAssertEqual(TimerRules.page(-3), .beer)
    }

    func testReminderPolicy() {
        XCTAssertEqual(ReminderPolicy.clampInterval(1), 5)
        XCTAssertEqual(ReminderPolicy.clampInterval(600), 60)
        XCTAssertEqual(ReminderPolicy.nextAt(now: 1_000, intervalMin: 0), 1_000 + 5 * 60_000)
        XCTAssertTrue(ReminderPolicy.shouldSchedule(enabled: true, sessionId: 2, stoppedSessionId: 1))
        XCTAssertFalse(ReminderPolicy.shouldSchedule(enabled: true, sessionId: 1, stoppedSessionId: 1))
        XCTAssertFalse(ReminderPolicy.isCurrent(actionSessionId: 3, openSessionId: nil))
        XCTAssertEqual(ReminderPolicy.restoreAt(nextAt: 5_000, now: 10_000), 10_000 + ReminderPolicy.restoreDelayMs)
        XCTAssertEqual(ReminderPolicy.restoreAt(nextAt: 500_000, now: 10_000), 500_000)
    }
}

final class VersusAndStatsTests: XCTestCase {
    private let min: Millis = 60_000
    private let profile = BodyProfile(sex: .MALE, age: 30, heightCm: 180, weightKg: 80, license: .NONE)

    private func beer(_ session: Int64, _ minute: Millis, _ side: Side = .SOLO) -> BeerEvent {
        BeerEvent(sessionId: session, side: side, at: minute * min)
    }

    func testVersusRules() {
        let notes = [Note(sessionId: 1, text: "n", amountMinor: 1_000, createdAt: 0, side: .RED),
                     Note(sessionId: 1, text: "n", amountMinor: 250, createdAt: 0, side: .BLUE),
                     Note(sessionId: 1, text: "n", amountMinor: 500, createdAt: 0),
                     Note(sessionId: 1, text: "n", createdAt: 0, side: .RED)]
        let totals = VersusRules.totals(notes)
        XCTAssertEqual(totals, TabTotals(red: 1_000, blue: 250, shared: 500))
        XCTAssertEqual(totals.total, 1_750)
        XCTAssertTrue(VersusRules.fits(.SOLO, mode: nil))
        XCTAssertFalse(VersusRules.fits(.SOLO, mode: .VERSUS))
        XCTAssertTrue(VersusRules.fits(.RED, mode: .VERSUS))
        XCTAssertFalse(VersusRules.fits(.BLUE, mode: nil))
        let versus = Session(startedAt: 0, mode: .VERSUS, meSide: .RED)
        XCTAssertTrue(VersusRules.isMine(BeerEvent(sessionId: 1, at: 0), in: Session(startedAt: 0)))
        XCTAssertFalse(VersusRules.isMine(BeerEvent(sessionId: 1, side: .BLUE, at: 0), in: versus))
        XCTAssertNil(VersusRules.cleanName("   "))
        XCTAssertNil(VersusRules.apply(versus, VersusSetup(meSide: .SOLO)).meSide)
        XCTAssertNil(VersusRules.noteSide(.RED, mode: .SOLO))
    }

    func testOverview() {
        let solo = Session(id: 1, startedAt: 0, endedAt: 120 * min)
        let versus = Session(id: 2, startedAt: 1_000 * min, endedAt: 1_100 * min, mode: .VERSUS, meSide: .RED)
        var anonymous = versus
        anonymous.id = 3
        anonymous.meSide = nil
        let beers = [beer(1, 0), beer(1, 30), beer(1, 60), beer(2, 1_000, .RED), beer(2, 1_020, .RED),
                     beer(2, 1_005, .BLUE), beer(3, 1_000, .RED)]
        let o = StatsRules.overview(sessions: [versus, solo, anonymous], beers: beers,
                                    waterBySession: [1: 1, 2: 1, 3: 9], profile: profile)
        XCTAssertEqual(o.sessions, 2)
        XCTAssertEqual(o.perSession.map(\.sessionId), [1, 2])
        XCTAssertEqual(o.perSession.map(\.beers), [3, 2])
        XCTAssertEqual(o.avgBeers!, 2.5, accuracy: 1e-9)
        XCTAssertEqual(o.avgPaceMin!, 80.0 / 3, accuracy: 1e-9)
        XCTAssertEqual(o.waterPerBeer!, 0.4, accuracy: 1e-9)
        XCTAssertEqual(StatsRules.durationMs(solo), 120 * min)
        XCTAssertNil(StatsRules.session(solo, beers: beers, profile: nil)!.peak)
        XCTAssertEqual(StatsRules.overview(sessions: [], beers: [], waterBySession: [:], profile: profile), Overview())
    }

    func testChartKeepsLatest() {
        let many = (1...30).map { Session(id: Int64($0), startedAt: Millis($0) * 10_000 * min, endedAt: Millis($0) * 10_000 * min + 1) }
        let o = StatsRules.overview(sessions: many, beers: many.map { beer($0.id, $0.startedAt / min) }, waterBySession: [:], profile: nil)
        XCTAssertEqual(o.perSession.count, StatsRules.chartSessions)
        XCTAssertEqual(o.perSession.last?.sessionId, 30)
    }
}

final class ExportJsonTests: XCTestCase {
    private let data = ExportData(
        sessions: [Session(id: 1, startedAt: 10, endedAt: 20)],
        beers: [BeerEvent(sessionId: 1, at: 11)],
        water: [WaterEvent(sessionId: 1, at: 12)],
        notes: [Note(sessionId: 1, text: "say \"hi\"\n\\ok\u{1}\u{2028}ç", createdAt: 13, side: .RED)],
        friends: [FriendProfile(id: 4, name: "Ali", color: 0)],
        profile: BodyProfile(sex: .FEMALE, age: 30, heightCm: 170, weightKg: 60, license: .NORMAL))

    func testProfileOnlyWhenTicked() {
        XCTAssertFalse(ExportJson.build(data, includeProfile: false, exportedAt: 0).contains("bodyProfile"))
        XCTAssertTrue(ExportJson.build(data, includeProfile: true, exportedAt: 0).contains("\"bodyProfile\":{\"sex\":\"FEMALE\",\"age\":30"))
    }

    func testEscapes() {
        XCTAssertTrue(ExportJson.build(data, includeProfile: false, exportedAt: 0)
            .contains("\"text\":\"say \\\"hi\\\"\\n\\\\ok\\u0001\\u2028ç\""))
    }

    func testNesting() {
        let json = ExportJson.build(data, includeProfile: false, exportedAt: 99)
        XCTAssertTrue(json.hasPrefix("{\"format\":\"cansin-export\",\"version\":1,\"exportedAt\":99,"))
        XCTAssertTrue(json.contains("\"beers\":[{\"side\":\"SOLO\",\"sizeMl\":500,\"abv\":5.0,\"at\":11,\"finishedAt\":null}]"))
        XCTAssertTrue(json.contains("\"water\":[{\"at\":12}]"))
        XCTAssertTrue(json.contains("\"friends\":[{\"id\":4,\"name\":\"Ali\"}]"))
        XCTAssertNoThrow(try JSONSerialization.jsonObject(with: Data(json.utf8)))
    }

    func testEmpty() {
        let empty = ExportData(sessions: [], beers: [], water: [], notes: [], friends: [], profile: nil)
        XCTAssertEqual(ExportJson.build(empty, includeProfile: true, exportedAt: 0),
                       "{\"format\":\"cansin-export\",\"version\":1,\"exportedAt\":0,\"sessions\":[],\"friends\":[]}")
    }
}
