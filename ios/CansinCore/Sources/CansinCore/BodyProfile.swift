import Foundation

public enum Sex: String, Codable, CaseIterable, Sendable { case MALE, FEMALE, UNSPECIFIED }

public enum License: String, Codable, CaseIterable, Sendable { case NONE, CANDIDATE, NORMAL, COMMERCIAL }

public struct BodyProfile: Equatable, Codable, Sendable {
    public static let ageRange = 18...100
    public static let heightRange = 120...230
    public static let weightRange = 35...250

    public var sex: Sex
    public var age: Int
    public var heightCm: Int
    public var weightKg: Int
    public var license: License

    public init(sex: Sex, age: Int, heightCm: Int, weightKg: Int, license: License) {
        self.sex = sex; self.age = age; self.heightCm = heightCm; self.weightKg = weightKg; self.license = license
    }

    public var isValid: Bool {
        Self.ageRange.contains(age) && Self.heightRange.contains(heightCm) && Self.weightRange.contains(weightKg)
    }

    public static func parse(_ raw: String, range: ClosedRange<Int>) -> Int? {
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard t.count <= 3, Digits.isAsciiDigits(t), let v = Int(t), range.contains(v) else { return nil }
        return v
    }
}
