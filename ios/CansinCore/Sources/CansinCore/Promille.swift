import Foundation

public struct Drink: Equatable, Sendable {
    public var at: Millis
    public var finishedAt: Millis?
    public var sizeMl: Int
    public var abv: Double

    public init(at: Millis, finishedAt: Millis?, sizeMl: Int, abv: Double) {
        self.at = at; self.finishedAt = finishedAt; self.sizeMl = sizeMl; self.abv = abv
    }
}

public struct PromilleEstimate: Equatable, Sendable {
    public var current: Double
    public var peak: Double
    public var zeroAt: Millis?

    public init(current: Double, peak: Double, zeroAt: Millis?) {
        self.current = current; self.peak = peak; self.zeroAt = zeroAt
    }
}

public enum PromilleModel {
    public static let minuteMs: Millis = 60_000
    public static let defaultDrinkMs: Millis = 20 * minuteMs
    public static let absorptionTailMs: Millis = 30 * minuteMs
    public static let eliminationPerMin = 0.15 / 60
    private static let ethanolDensity = 0.789
    private static let bloodWater = 0.8
    private static let bloodDensity = 1.055
    private static let maxMinutes = 72 * 60

    public static func grams(sizeMl: Int, abv: Double) -> Double { Double(sizeMl) * abv / 100 * ethanolDensity }

    public static func totalBodyWater(_ p: BodyProfile) -> Double {
        switch p.sex {
        case .MALE: return 2.447 - 0.09516 * Double(p.age) + 0.1074 * Double(p.heightCm) + 0.3362 * Double(p.weightKg)
        case .FEMALE, .UNSPECIFIED: return -2.097 + 0.1069 * Double(p.heightCm) + 0.2466 * Double(p.weightKg)
        }
    }

    public static func drinkEnd(_ drinks: [Drink], index: Int) -> Millis {
        let drink = drinks[index]
        if let finished = drink.finishedAt { return max(finished, drink.at) }
        let fallback = drink.at + defaultDrinkMs
        if index + 1 < drinks.count, drinks[index + 1].at > drink.at { return min(fallback, drinks[index + 1].at) }
        return fallback
    }

    public static func estimate(profile: BodyProfile, drinks: [Drink], now: Millis) -> PromilleEstimate {
        let sorted = drinks.sorted { $0.at < $1.at }
        guard let first = sorted.first else { return PromilleEstimate(current: 0, peak: 0, zeroAt: nil) }
        let tbw = totalBodyWater(profile)
        let windows = sorted.indices.map { i in
            (start: sorted[i].at, end: drinkEnd(sorted, index: i) + absorptionTailMs,
             grams: grams(sizeMl: sorted[i].sizeMl, abv: sorted[i].abv))
        }
        let absorbedUntil = windows.map(\.end).max()!
        var bac = 0.0, peak = 0.0, current = 0.0
        var zeroAt: Millis?
        for step in 0..<maxMinutes {
            let from = first.at + Millis(step) * minuteMs
            let to = from + minuteMs
            let absorbed = windows.reduce(0.0) { acc, w in
                acc + w.grams * Double(overlap(from, to, w.start, w.end)) / Double(w.end - w.start)
            }
            bac = max(0, bac + absorbed * bloodWater / tbw - eliminationPerMin)
            peak = max(peak, bac)
            if to <= now { current = bac }
            if to >= absorbedUntil && bac == 0 {
                zeroAt = to
                break
            }
        }
        return PromilleEstimate(current: toPromille(current), peak: toPromille(peak),
                                zeroAt: zeroAt.flatMap { $0 > now ? $0 : nil })
    }

    public static func toPromille(_ gramsPerLitre: Double) -> Double {
        (gramsPerLitre / bloodDensity * 100 - 1e-9).rounded(.up) / 100
    }

    private static func overlap(_ from: Millis, _ to: Millis, _ start: Millis, _ end: Millis) -> Millis {
        max(0, min(to, end) - max(from, start))
    }
}
