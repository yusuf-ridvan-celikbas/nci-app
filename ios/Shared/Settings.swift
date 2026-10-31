import Foundation
import CansinCore

enum AppName: String, CaseIterable { case SOY_TU_TENTACION, SALGAM_BALGAM
    var labelKey: String { self == .SOY_TU_TENTACION ? "name_soy" : "name_salgam" }
}

enum ThemeMode: String, CaseIterable { case SYSTEM, LIGHT, DARK
    var labelKey: String { ["SYSTEM": "theme_system", "LIGHT": "theme_light", "DARK": "theme_dark"][rawValue]! }
}

enum Accent: String, CaseIterable { case AMBER, RED, BLUE, YELLOW, GREEN, PURPLE
    var labelKey: String { "color_" + rawValue.lowercased() }
}

struct ReminderState: Equatable { var sessionId: Int64?; var stoppedSessionId: Int64? }

/// App Group UserDefaults (shared with the widget). Same keys as the Android DataStore.
struct SettingsStore {
    static let shared = SettingsStore()
    private let d = UserDefaults(suiteName: AppStore.appGroup) ?? .standard

    /// Kept by "Delete all data" (same as Android).
    private static let keptOnWipe: Set<String> = ["age_confirmed", "app_name", "theme_mode", "accent"]

    var ageConfirmed: Bool { get { d.bool(forKey: "age_confirmed") } nonmutating set { d.set(newValue, forKey: "age_confirmed") } }
    var appName: AppName { get { enumValue("app_name", .SOY_TU_TENTACION) } nonmutating set { d.set(newValue.rawValue, forKey: "app_name") } }
    var themeMode: ThemeMode { get { enumValue("theme_mode", .SYSTEM) } nonmutating set { d.set(newValue.rawValue, forKey: "theme_mode") } }
    var accent: Accent { get { enumValue("accent", .AMBER) } nonmutating set { d.set(newValue.rawValue, forKey: "accent") } }

    var remindersEnabled: Bool { get { d.bool(forKey: "reminders_enabled") } nonmutating set { d.set(newValue, forKey: "reminders_enabled") } }
    var reminderIntervalMin: Int {
        get { ReminderPolicy.clampInterval(int("reminder_interval") ?? ReminderPolicy.defaultIntervalMin) }
        nonmutating set { d.set(ReminderPolicy.clampInterval(newValue), forKey: "reminder_interval") }
    }
    var reminderSilent: Bool { get { d.bool(forKey: "reminder_silent") } nonmutating set { d.set(newValue, forKey: "reminder_silent") } }
    var reminderState: ReminderState {
        get { ReminderState(sessionId: int64("reminder_session"), stoppedSessionId: int64("reminder_stopped_session")) }
        nonmutating set { setOpt(newValue.sessionId, "reminder_session"); setOpt(newValue.stoppedSessionId, "reminder_stopped_session") }
    }

    var beerSize: BeerSize {
        get { BeerRules.clamp(BeerSize(sizeMl: int("default_size_ml") ?? BeerEvent.defaultSizeMl,
                                       abv: d.object(forKey: "default_abv") as? Double ?? BeerEvent.defaultAbv)) }
        nonmutating set {
            let c = BeerRules.clamp(newValue)
            d.set(c.sizeMl, forKey: "default_size_ml"); d.set(c.abv, forKey: "default_abv")
        }
    }

    var profile: BodyProfile? {
        get {
            guard let sex = d.string(forKey: "profile_sex").flatMap(Sex.init(rawValue:)),
                  let license = d.string(forKey: "profile_license").flatMap(License.init(rawValue:)),
                  let age = int("profile_age"), let height = int("profile_height"), let weight = int("profile_weight")
            else { return nil }
            let p = BodyProfile(sex: sex, age: age, heightCm: height, weightKg: weight, license: license)
            return p.isValid ? p : nil
        }
        nonmutating set {
            guard let p = newValue, p.isValid else {
                for key in ["profile_sex", "profile_license", "profile_age", "profile_height", "profile_weight"] { d.removeObject(forKey: key) }
                return
            }
            d.set(p.sex.rawValue, forKey: "profile_sex"); d.set(p.license.rawValue, forKey: "profile_license")
            d.set(p.age, forKey: "profile_age"); d.set(p.heightCm, forKey: "profile_height"); d.set(p.weightKg, forKey: "profile_weight")
        }
    }
    var profilePromptDismissed: Bool { get { d.bool(forKey: "profile_prompt_dismissed") } nonmutating set { d.set(newValue, forKey: "profile_prompt_dismissed") } }

    var waterEnabled: Bool { get { d.object(forKey: "water_enabled") as? Bool ?? true } nonmutating set { d.set(newValue, forKey: "water_enabled") } }
    var waterEvery: Int {
        get { WaterRules.clamp(int("water_every") ?? WaterRules.defaultEvery) }
        nonmutating set { d.set(WaterRules.clamp(newValue), forKey: "water_every") }
    }

    var timerEnabled: Bool { get { d.object(forKey: "timer_enabled") as? Bool ?? true } nonmutating set { d.set(newValue, forKey: "timer_enabled") } }
    var timer: TimerState {
        get { TimerState(beerId: int64("timer_beer_id"), sessionId: int64("timer_session_id"), startedAt: int64("timer_started_at")) }
        nonmutating set {
            setOpt(newValue.beerId, "timer_beer_id"); setOpt(newValue.sessionId, "timer_session_id")
            setOpt(newValue.startedAt, "timer_started_at")
        }
    }

    /// One page for all widget instances: WidgetKit intents don't know which instance was tapped.
    var widgetPage: WidgetPage { get { TimerRules.page(d.integer(forKey: "widget_page")) } nonmutating set { d.set(newValue.rawValue, forKey: "widget_page") } }

    func wipe() {
        for key in d.dictionaryRepresentation().keys where !Self.keptOnWipe.contains(key) { d.removeObject(forKey: key) }
    }

    private func enumValue<E: RawRepresentable>(_ key: String, _ fallback: E) -> E where E.RawValue == String {
        d.string(forKey: key).flatMap(E.init(rawValue:)) ?? fallback
    }
    private func int(_ key: String) -> Int? { d.object(forKey: key) as? Int }
    private func int64(_ key: String) -> Int64? { (d.object(forKey: key) as? NSNumber)?.int64Value }
    private func setOpt(_ v: Int64?, _ key: String) { if let v { d.set(NSNumber(value: v), forKey: key) } else { d.removeObject(forKey: key) } }
}

func L(_ key: String, _ args: CVarArg...) -> String {
    String(format: NSLocalizedString(key, comment: ""), locale: Locale.current, arguments: args)
}

func L(plural key: String, _ count: Int) -> String {
    String.localizedStringWithFormat(NSLocalizedString(key, comment: ""), count)
}

/// Warnings use the app's effective language (same rule as Android).
var appIsTurkish: Bool { Bundle.main.preferredLocalizations.first?.hasPrefix("tr") == true }

var decimalSeparator: Character { Character(Locale.current.decimalSeparator ?? ".") }
