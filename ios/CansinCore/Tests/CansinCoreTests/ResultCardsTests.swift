import Foundation
import XCTest
@testable import CansinCore

final class ResultCardsTests: XCTestCase {
    private let today = "2026-10-31"
    private let card = ResultCard(name: "Çağrı Ş", day: "2026-10-30", beers: 4, durationMin: 190, water: 2)
    /// Produced by the Android encoder for the same card: both platforms must stay compatible.
    private let androidCode = "cansin:1:w4dhxJ9yxLEgxZ4KMjAyNi0xMC0zMAo0CjE5MAoy"

    private func raw(_ body: String) -> String {
        ResultCards.prefix + Data(body.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }

    func testMatchesAndroidFormat() {
        XCTAssertEqual(ResultCards.encode(card), androidCode)
        XCTAssertEqual(ResultCards.decode("Here's mine 🍺\n\(androidCode)\nsee you!", today: today), card)
        XCTAssertEqual(ResultCards.decode("CANSIN:1:" + androidCode.dropFirst(ResultCards.prefix.count), today: today), card)
    }

    func testBuildsCard() {
        XCTAssertEqual(ResultCards.card(name: "  Ali ", day: today, beers: 4, durationMs: 190 * 60_000 + 59_000, water: 2),
                       ResultCard(name: "Ali", day: today, beers: 4, durationMin: 190, water: 2))
        XCTAssertEqual(ResultCards.card(name: "Ali", day: today, beers: 4, durationMs: Int64.max / 2, water: 2)?.durationMin,
                       ResultCards.maxDurationMin)
        XCTAssertNil(ResultCards.card(name: " ", day: today, beers: 4, durationMs: 0, water: 0))
    }

    func testRejectsGarbage() {
        for text in ["", "hello", "cansin:1:", "cansin:1:!!!!!!!!!!", "cansin:2:" + androidCode.dropFirst(9), String(repeating: "x", count: 6_000)] {
            XCTAssertNil(ResultCards.decode(text, today: today), String(text.prefix(40)))
        }
    }

    func testRejectsBadFields() {
        for body in ["Ali\n2026-10-30\n101\n10\n0", "Ali\n2026-10-30\n4\n4321\n0", "Ali\n2026-10-30\n4\n10\n101",
                     "Ali\n2026-10-30\n-1\n10\n0", "Ali\n2026-10-30\n4\n10", "Ali\n2026-10-30\n4\n10\n0\nextra",
                     "Ali\n2026-13-01\n4\n10\n0", "Ali\n2026-02-30\n4\n10\n0", "Ali\n2026-11-02\n4\n10\n0",
                     "Ali\n2019-12-31\n4\n10\n0", "\n2026-10-30\n4\n10\n0", " Ali\n2026-10-30\n4\n10\n0",
                     "A\u{202E}li\n2026-10-30\n4\n10\n0", "A\tli\n2026-10-30\n4\n10\n0",
                     String(repeating: "x", count: 31) + "\n2026-10-30\n4\n10\n0"] {
            XCTAssertNil(ResultCards.decode(raw(body), today: today), body)
        }
    }

    func testTomorrowAndDayFormatting() {
        XCTAssertEqual(ResultCards.decode(raw("Ali\n2026-11-01\n1\n0\n0"), today: today)?.beers, 1)
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        XCTAssertEqual(ResultCards.day(of: Date(timeIntervalSince1970: 0), calendar: cal), "1970-01-01")
    }
}
