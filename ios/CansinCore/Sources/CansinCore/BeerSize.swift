public struct BeerSize: Equatable, Codable, Sendable {
    public static let `default` = BeerSize(sizeMl: BeerEvent.defaultSizeMl, abv: BeerEvent.defaultAbv)

    public var sizeMl: Int
    public var abv: Double

    public init(sizeMl: Int, abv: Double) { self.sizeMl = sizeMl; self.abv = abv }
}

public enum BeerRules {
    public static let presetsMl = [330, 500]
    public static let minMl = 20
    public static let maxMl = 2_000
    public static let minAbv = 0.0
    public static let maxAbv = 80.0

    public static func clamp(_ size: BeerSize) -> BeerSize {
        BeerSize(sizeMl: size.sizeMl.clamped(minMl, maxMl), abv: size.abv.clamped(minAbv, maxAbv))
    }

    public static func parseCl(_ raw: String) -> Int? {
        guard let t = parseTenths(raw), (minMl...maxMl).contains(t) else { return nil }
        return t
    }

    public static func parseAbv(_ raw: String) -> Double? {
        guard let t = parseTenths(raw) else { return nil }
        let v = Double(t) / 10
        return (minAbv...maxAbv).contains(v) ? v : nil
    }

    public static func formatCl(_ sizeMl: Int, separator: Character) -> String {
        formatTenths(sizeMl, separator: separator, keepZero: false)
    }

    public static func formatAbv(_ abv: Double, separator: Character) -> String {
        formatTenths(Int((abv * 10).rounded()), separator: separator, keepZero: true)
    }

    private static func parseTenths(_ raw: String) -> Int? {
        guard let p = Digits.split(raw, maxWhole: 3, maxFraction: 1) else { return nil }
        return Int(p.whole) * 10 + (Int(p.fraction) ?? 0)
    }

    private static func formatTenths(_ tenths: Int, separator: Character, keepZero: Bool) -> String {
        tenths % 10 == 0 && !keepZero ? "\(tenths / 10)" : "\(tenths / 10)\(separator)\(tenths % 10)"
    }
}
