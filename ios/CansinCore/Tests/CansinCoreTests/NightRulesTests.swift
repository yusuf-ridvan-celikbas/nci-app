import Foundation
import XCTest
@testable import CansinCore

/// Mirrors the Android P14–P23 tests (CurrencyTest, CareRulesTest, SettleRulesTest, CalendarRulesTest, DrinksTest, GroupRulesTest).
final class NightRulesTests: XCTestCase {
    func testCurrencyPlacement() {
        XCTAssertEqual(Currency.TRY.format(1250, separator: ",", turkish: true), "12,50 ₺")
        XCTAssertEqual(Currency.EUR.format(1250, separator: ".", turkish: false), "€12.50")
        XCTAssertEqual(Currency.EUR.format(1250, separator: ",", turkish: true), "12,50 €")
        XCTAssertEqual(Currency.NONE.format(1250, separator: ".", turkish: false), "12.50")
    }

    func testCareLimitsFirstThenLateThenSlowDown() {
        let base = CareInput(enabled: true, sessionActive: true, now: 0, hour: 21, myBeers: 4, beerLimit: 4)
        XCTAssertEqual(CareRules.pick(base, dismissed: CareDismissals()), .beerLimit)
        XCTAssertNil(CareRules.pick(base, dismissed: CareDismissals(limitAtBeers: 4)))
        var off = base; off.enabled = false
        XCTAssertEqual(CareRules.pick(off, dismissed: CareDismissals()), .beerLimit)
        var late = base; late.beerLimit = nil; late.hour = 2
        XCTAssertEqual(CareRules.pick(late, dismissed: CareDismissals()), .late)
        XCTAssertNil(CareRules.pick(late, dismissed: CareDismissals(lateAt: -1000)))
        var slow = base; slow.beerLimit = nil; slow.myBeers = 5
        XCTAssertEqual(CareRules.pick(slow, dismissed: CareDismissals()), .slowDown)
        XCTAssertTrue(CareRules.isLate(hour: 23, lateHour: 22))
        XCTAssertFalse(CareRules.isLate(hour: 6, lateHour: 22))
        XCTAssertEqual(CareRules.cleanHome("  Home\u{0007} "), "Home")
    }

    func testMySpendPerMode() {
        let notes = [
            Note(sessionId: 1, text: "a", amountMinor: 500, createdAt: 0, side: .GREEN),
            Note(sessionId: 1, text: "b", amountMinor: 200, createdAt: 0),
            Note(sessionId: 1, text: "c", amountMinor: 900, createdAt: 0, side: .RED),
        ]
        XCTAssertEqual(CareRules.mySpend(Session(id: 1, startedAt: 0, mode: .GROUP, meSide: .GREEN), notes: notes), 700)
        XCTAssertEqual(CareRules.mySpend(Session(id: 1, startedAt: 0, mode: .GROUP), notes: notes), 1600)
        XCTAssertEqual(CareRules.mySpend(Session(id: 1, startedAt: 0), notes: notes), 1600)
    }

    func testSettle() {
        let notes = [
            Note(sessionId: 1, text: "a", person: "ali", amountMinor: 100, createdAt: 0),
            Note(sessionId: 1, text: "b", person: "Ali", amountMinor: 50, createdAt: 0),
            Note(sessionId: 1, text: "c", amountMinor: 10, createdAt: 0),
        ]
        XCTAssertEqual(SettleRules.byPerson(notes), [PersonTotal(person: "ali", minor: 150), PersonTotal(person: nil, minor: 10)])
        XCTAssertEqual(SettleRules.eachPays(100, people: 3), 34)
        let (red, blue) = SettleRules.splitShared(TabTotals(red: 10, blue: 20, shared: 5))
        XCTAssertEqual(red, 13); XCTAssertEqual(blue, 22)
    }

    func testCalendarGridAndMonths() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 3 * 3600)!
        cal.firstWeekday = 2
        let nov = YearMonth(year: 2026, month: 11)
        let grid = CalendarRules.grid(nov, calendar: cal)
        XCTAssertEqual(grid.prefix { $0 == nil }.count, 6)
        XCTAssertEqual(grid.count, 36)
        XCTAssertEqual(nov.adding(months: -11), YearMonth(year: 2025, month: 12))
        XCTAssertEqual(nov.adding(months: 2), YearMonth(year: 2027, month: 1))
        func at(_ y: Int, _ m: Int, _ d: Int) -> Millis {
            Millis(cal.date(from: DateComponents(year: y, month: m, day: d, hour: 21))!.timeIntervalSince1970 * 1000)
        }
        let list = [DaySession(sessionId: 1, startedAt: at(2026, 9, 10), spentMinor: 2000),
                    DaySession(sessionId: 2, startedAt: at(2026, 11, 1), spentMinor: 1000),
                    DaySession(sessionId: 3, startedAt: at(2026, 11, 20), spentMinor: 0)]
        XCTAssertEqual(CalendarRules.inMonth(list, nov, calendar: cal).map(\.sessionId), [2, 3])
        XCTAssertEqual(CalendarRules.summary(CalendarRules.inMonth(list, nov, calendar: cal)), MonthSummary(sessions: 2, spentMinor: 1000, avgSpentMinor: 1000))
        XCTAssertEqual(CalendarRules.spentPerMonth(list, last: nov, calendar: cal, count: 3).map(\.1), [2000, 0, 1000])
    }

    func testDrinks() {
        let beer = BeerSize(sizeMl: 330, abv: 4.5)
        XCTAssertEqual(Drinks.defaultChoice(.BEER, beer: beer), DrinkChoice(kind: .BEER, size: beer))
        XCTAssertEqual(Drinks.defaultChoice(.CUSTOM, beer: beer).kind, .BEER)
        for k in DrinkKind.allCases { XCTAssertEqual(BeerRules.clamp(Drinks.preset(k, beer: beer)), Drinks.preset(k, beer: beer)) }
        let b = Drinks.breakdown([.SHOT, .BEER, .BEER, .WINE])
        XCTAssertEqual(b.map(\.0), [.BEER, .WINE, .SHOT])
        XCTAssertEqual(b.map(\.1), [2, 1, 1])
        XCTAssertFalse(Drinks.showBreakdown([.BEER]))
    }

    func testGroup() {
        let seats = GroupRules.seats(7, GroupSetup(names: [" Ali ", nil, "Can"]))
        XCTAssertEqual(seats.map(\.side), [.RED, .BLUE, .GREEN])
        XCTAssertEqual(seats.map(\.name), ["Ali", nil, "Can"])
        XCTAssertNil(GroupRules.me(GroupSetup(names: ["a", "b"], me: .GREEN)))
        let beers = [Side.GREEN, .GREEN, .RED].map { BeerEvent(sessionId: 7, side: $0, at: 0) }
        let t = GroupRules.tally(seats.reversed(), beers: beers, water: [WaterEvent(sessionId: 7, at: 0, side: .BLUE)])
        XCTAssertEqual(t.map(\.drinks), [1, 0, 2])
        XCTAssertEqual(t.map(\.water), [0, 1, 0])
        let odd = GroupRules.split(seats, notes: [Note(sessionId: 7, text: "x", amountMinor: 100, createdAt: 0)], splitShared: true)
        XCTAssertEqual(odd.map(\.pays), [34, 33, 33])
        XCTAssertTrue(VersusRules.fits(.GREEN, mode: .GROUP))
        XCTAssertFalse(VersusRules.fits(.GREEN, mode: .VERSUS))
        XCTAssertEqual(VersusRules.noteSide(.YELLOW, mode: .GROUP), .YELLOW)
        XCTAssertNil(VersusRules.noteSide(.YELLOW, mode: .VERSUS))
    }
}
