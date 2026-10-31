public enum SessionRules {
    public static let defaultInactivityMs: Millis = 6 * 60 * 60 * 1000

    public static func isStale(lastActivityAt: Millis, now: Millis, timeoutMs: Millis = defaultInactivityMs) -> Bool {
        now - lastActivityAt >= timeoutMs
    }
}
