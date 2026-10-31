import SwiftUI
import UserNotifications
import CansinCore

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var pickingSize = false
    @State private var editingProfile = false

    var body: some View {
        let p = model.prefs
        Form {
            Section(L("section_general")) {
                Picker(L("setting_app_name"), selection: binding(\.appName) { s, v in s.appName = v }) {
                    ForEach(AppName.allCases, id: \.self) { Text(L($0.labelKey)).tag($0) }
                }
                Text(L("name_change_note_ios")).font(.footnote).foregroundStyle(.secondary)
                Button(L("setting_language")) { openSettings() }
                Text(L("language_ios_note")).font(.footnote).foregroundStyle(.secondary)
            }
            Section(L("section_appearance")) {
                Picker(L("setting_theme_mode"), selection: binding(\.themeMode) { s, v in s.themeMode = v }) {
                    ForEach(ThemeMode.allCases, id: \.self) { Text(L($0.labelKey)).tag($0) }
                }
                Picker(L("setting_color"), selection: binding(\.accent) { s, v in s.accent = v }) {
                    ForEach(Accent.allCases, id: \.self) { a in
                        Label { Text(L(a.labelKey)) } icon: { Circle().fill(Palette.primary(a)).frame(width: 14) }.tag(a)
                    }
                }
            }
            Section(L("section_beer")) {
                Button { pickingSize = true } label: {
                    LabeledContent(L("setting_default_beer"), value: sizeLabel(p.beerSize))
                }
                .foregroundStyle(.primary)
                Text(L("pick_size_hint")).font(.footnote).foregroundStyle(.secondary)
                Toggle(L("setting_timer"), isOn: Binding(get: { p.timerEnabled }, set: { model.setTimerEnabled($0) }))
                Text(L("setting_timer_note")).font(.footnote).foregroundStyle(.secondary)
            }
            ReminderSection()
            Section(L("section_water")) {
                Toggle(L("setting_water"), isOn: binding(\.waterEnabled) { s, v in s.waterEnabled = v })
                if p.waterEnabled {
                    Stepper(L(plural: "water_every", p.waterEvery),
                            value: binding(\.waterEvery) { s, v in s.waterEvery = v },
                            in: WaterRules.minEvery...WaterRules.maxEvery)
                }
            }
            Section(L("section_estimate")) {
                Button { editingProfile = true } label: {
                    LabeledContent(L("profile_title"), value: p.profile.map { "\($0.age) · \($0.heightCm) cm · \($0.weightKg) kg" } ?? L("profile_not_set"))
                }
                .foregroundStyle(.primary)
            }
            if !model.friends.isEmpty {
                Section(L("section_friends")) {
                    ForEach(model.friends) { f in Text(f.name) }
                        .onDelete { idx in idx.map { model.friends[$0].id }.forEach(model.deleteFriend) }
                }
            }
            DataSection()
        }
        .navigationTitle(L("settings"))
        .sheet(isPresented: $pickingSize) {
            BeerSizeSheet(title: L("setting_default_beer"), initial: p.beerSize, confirm: L("action_save")) { size in
                model.updatePrefs { $0.beerSize = size }
            }
        }
        .sheet(isPresented: $editingProfile) { ProfileSheet() }
    }

    private func binding<T>(_ key: KeyPath<Prefs, T>, _ set: @escaping (SettingsStore, T) -> Void) -> Binding<T> {
        Binding(get: { model.prefs[keyPath: key] }, set: { v in model.updatePrefs { set($0, v) } })
    }

    private func openSettings() { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }
}

private struct ReminderSection: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var rationale = false
    @State private var blocked = false

    var body: some View {
        let p = model.prefs
        Section(L("section_reminders")) {
            Toggle(L("setting_reminders"), isOn: Binding(get: { p.remindersEnabled }, set: { on in
                if on { rationale = true } else { model.setRemindersEnabled(false) }
            }))
            if p.remindersEnabled {
                Stepper(L("setting_reminder_interval", p.reminderIntervalMin),
                        value: Binding(get: { p.reminderIntervalMin }, set: { v in model.updatePrefs { $0.reminderIntervalMin = v } }),
                        in: ReminderPolicy.minIntervalMin...ReminderPolicy.maxIntervalMin, step: 5)
                Toggle(L("setting_reminder_silent"), isOn: Binding(get: { p.reminderSilent }, set: { v in model.updatePrefs { $0.reminderSilent = v } }))
            }
            if blocked {
                Text(L("notifications_blocked")).foregroundStyle(.secondary)
                Button(L("open_settings")) { if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } }
            }
        }
        .alert(L("reminder_rationale_title"), isPresented: $rationale) {
            Button(L("action_continue")) { Task { await requestAndEnable() } }
            Button(L("action_cancel"), role: .cancel) {}
        } message: { Text(L("reminder_rationale_body")) }
        .task { await refreshBlocked() }
    }

    private func requestAndEnable() async {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
        if granted { model.setRemindersEnabled(true) }
        await refreshBlocked()
    }

    private func refreshBlocked() async {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        blocked = status == .denied
    }
}

private struct DataSection: View {
    @Environment(AppModel.self) private var model
    @State private var includeProfile = false
    @State private var exportURL: URL?
    @State private var confirmingDelete = false

    var body: some View {
        Section(L("section_data")) {
            Text(L("export_body")).font(.footnote).foregroundStyle(.secondary)
            Toggle(L("export_include_profile"), isOn: $includeProfile)
                .onChange(of: includeProfile) { exportURL = nil }
            if let url = exportURL {
                ShareLink(item: url) { Label(L("action_share"), systemImage: "square.and.arrow.up") }
            } else {
                Button(L("export_create")) {
                    guard let repo = model.repository else { return }
                    exportURL = Exporter.write(repo.exportData(includeProfile: includeProfile))
                }
            }
            Button(L("delete_all_data"), role: .destructive) { confirmingDelete = true }
        }
        .alert(L("delete_all_title"), isPresented: $confirmingDelete) {
            Button(L("action_delete"), role: .destructive) { exportURL = nil; model.deleteAllData() }
            Button(L("action_cancel"), role: .cancel) {}
        } message: { Text(L("delete_all_body")) }
    }
}

/// Export file in tmp/export (replaced each time, wiped on delete-all), complete file protection.
enum Exporter {
    private static var dir: URL { FileManager.default.temporaryDirectory.appendingPathComponent("export", isDirectory: true) }

    static func write(_ data: ExportData) -> URL? {
        let json = ExportJson.build(data, includeProfile: data.profile != nil, exportedAt: AppStore.now)
        wipe()
        let url = dir.appendingPathComponent("cansin-export.json")
        do {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try Data(json.utf8).write(to: url, options: [.atomic, .completeFileProtection])
            return url
        } catch { return nil }
    }

    static func wipe() { try? FileManager.default.removeItem(at: dir) }
}
