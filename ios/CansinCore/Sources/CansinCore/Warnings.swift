public enum TrDrivingLaw {
    public static let lawVersion = "TR-KTK-48"
    public static let lastVerified = "unverified (values from spec, verify before release)"
    public static let limits: [License: Double] = [.CANDIDATE: 0.20, .COMMERCIAL: 0.20, .NORMAL: 0.50]
}

public enum Greeting: Sendable { case morning, day, evening }

public enum Warning: Equatable, Sendable {
    case generic
    case overLimit(Double)
    case underLimit
    case hello(Greeting)
    case slowDown
    case poisoning
}

public enum WarningRules {
    public static let slowDown = 1.50
    public static let poisoning = 3.00

    public static func greeting(hour: Int) -> Greeting {
        switch hour {
        case 5...11: return .morning
        case 12...17: return .day
        default: return .evening
        }
    }

    public static func warnings(promille: Double, license: License, turkish: Bool, hour: Int) -> [Warning] {
        guard turkish else { return [.generic] }
        var result: [Warning] = []
        if let limit = TrDrivingLaw.limits[license] {
            if promille >= limit { result.append(.overLimit(limit)) } else if promille > 0 { result.append(.underLimit) }
        } else {
            result.append(.hello(greeting(hour: hour)))
        }
        if promille >= poisoning { result.append(.poisoning) } else if promille >= slowDown { result.append(.slowDown) }
        return result
    }
}
