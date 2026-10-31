import AppIntents
import WidgetKit
import CansinCore

// Widget-only intents: not discoverable in Shortcuts/Siri, and they need the device unlocked
// because the store uses complete file protection. Each one re-checks state before writing.

struct AddBeerIntent: AppIntent {
    static var title: LocalizedStringResource = "add_beer"
    static var isDiscoverable = false
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @MainActor func perform() async throws -> some IntentResult {
        // In Versus the widget opens the app instead; addBeer(side: .SOLO) is also refused there.
        Effects.makeRepository()?.addBeer()
        return .result()
    }
}

struct AddWaterIntent: AppIntent {
    static var title: LocalizedStringResource = "water_add"
    static var isDiscoverable = false
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @MainActor func perform() async throws -> some IntentResult {
        if let repo = Effects.makeRepository(), repo.snapshot().hasMe { repo.addWater() }
        return .result()
    }
}

struct ToggleTimerIntent: AppIntent {
    static var title: LocalizedStringResource = "setting_timer"
    static var isDiscoverable = false
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @MainActor func perform() async throws -> some IntentResult {
        guard let repo = Effects.makeRepository() else { return .result() }
        let settings = SettingsStore.shared
        if TimerRules.isCurrent(settings.timer, sessionId: repo.snapshot().sessionId) {
            DrinkTimer.stop(repository: repo)
        } else {
            DrinkTimer.start(repository: repo)
        }
        return .result()
    }
}

struct WidgetPageIntent: AppIntent {
    static var title: LocalizedStringResource = "widget_next"
    static var isDiscoverable = false

    @Parameter(title: "delta") var delta: Int

    init() { delta = 1 }
    init(delta: Int) { self.delta = delta }

    func perform() async throws -> some IntentResult {
        let store = SettingsStore.shared
        store.widgetPage = TimerRules.step(store.widgetPage, delta.clamped(-1, 1))
        return .result()
    }
}
