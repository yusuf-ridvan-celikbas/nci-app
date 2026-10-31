import SwiftUI
import CansinCore

func formatPromille(_ v: Double) -> String {
    String(format: "%.2f", locale: Locale.current, v)
}

func sizeLabel(_ s: BeerSize) -> String {
    L("beer_size_value", BeerRules.formatCl(s.sizeMl, separator: decimalSeparator), BeerRules.formatAbv(s.abv, separator: decimalSeparator))
}

private var nowMs: Int64 { AppStore.now }

/// Card on the counter: setup prompt, or the current estimate. Refreshes every minute while visible.
struct PromilleCard: View {
    @Environment(AppModel.self) private var model
    @State private var editingProfile = false

    var body: some View {
        Group {
            if model.prefs.profile == nil {
                if !model.prefs.profilePromptDismissed {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L("promille_title")).font(.subheadline.bold())
                        Text(L("profile_prompt_body"))
                        HStack {
                            Spacer()
                            Button(L("action_not_now")) { model.updatePrefs { $0.profilePromptDismissed = true } }
                            Button(L("action_set_up")) { editingProfile = true }.bold()
                        }
                    }
                    .padding().background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                }
            } else if !model.myBeers.isEmpty {
                TimelineView(.periodic(from: .now, by: 60)) { _ in
                    if let e = model.estimate(now: nowMs) {
                        Button { model.path.append(.promille) } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(L("promille_current")).font(.caption)
                                Text(L("promille_value", formatPromille(e.current))).font(.title2.bold())
                                if let w = warnings(e.current).first { WarningBanner(warning: w) }
                                Text(L("promille_disclaimer")).font(.caption2).foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding().background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                        }
                        .foregroundStyle(.primary)
                        .accessibilityHint(L("promille_open"))
                    }
                }
            }
        }
        .sheet(isPresented: $editingProfile) { ProfileSheet() }
    }

    private func warnings(_ v: Double) -> [Warning] {
        WarningRules.warnings(promille: v, license: model.prefs.profile?.license ?? .NONE, turkish: appIsTurkish,
                              hour: Calendar.current.component(.hour, from: .now))
    }
}

struct PromilleView: View {
    @Environment(AppModel.self) private var model
    @State private var editing: BeerEvent?
    @State private var editingProfile = false

    var body: some View {
        List {
            Section {
                Text(L("promille_disclaimer")).font(.footnote).foregroundStyle(.secondary)
                TimelineView(.periodic(from: .now, by: 60)) { _ in
                    if let e = model.estimate(now: nowMs) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(L("promille_current")).font(.headline)
                            Text(L("promille_value", formatPromille(e.current))).font(.system(size: 48, weight: .bold)).monospacedDigit()
                            Text(L("promille_peak", formatPromille(e.peak)))
                            if let z = e.zeroAt { Text(L("promille_zero_at", timeText(z))) }
                            ForEach(Array(warnings(e.current).enumerated()), id: \.offset) { WarningBanner(warning: $0.element) }
                        }
                    }
                }
                if model.myBeers.isEmpty { Text(L("promille_no_beers")) }
                Button(L("profile_title")) { editingProfile = true }
            }
            Section {
                ForEach(model.myBeers) { beer in
                    Button { editing = beer } label: {
                        VStack(alignment: .leading) {
                            Text(sizeLabel(BeerSize(sizeMl: beer.sizeMl, abv: beer.abv)))
                            Text(L("beer_started", timeText(beer.at)) + (beer.finishedAt.map { " · " + L("beer_finished", timeText($0)) } ?? ""))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .foregroundStyle(.primary)
                }
            }
        }
        .navigationTitle(L("promille_title"))
        .sheet(item: $editing) { BeerEditSheet(beer: $0) }
        .sheet(isPresented: $editingProfile) { ProfileSheet() }
    }

    private func warnings(_ v: Double) -> [Warning] {
        WarningRules.warnings(promille: v, license: model.prefs.profile?.license ?? .NONE, turkish: appIsTurkish,
                              hour: Calendar.current.component(.hour, from: .now))
    }
}

/// Over-limit and poisoning use the fixed danger colors; never implies fitness to drive.
struct WarningBanner: View {
    let warning: Warning
    @Environment(\.openURL) private var openURL

    var body: some View {
        let danger: Bool = { if case .overLimit = warning { return true }; return warning == .poisoning }()
        let colors = danger ? Palette.danger : Palette.caution
        VStack(alignment: .leading, spacing: 8) {
            Text(text).font(.subheadline.weight(danger ? .bold : .regular))
            if case .overLimit = warning {
                Button(L("action_transport")) { open("maps://?q=" + (L("transport_query").addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")) }
            }
            if warning == .poisoning { Button(L("action_call_112")) { open("tel:112") } }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(colors.bg, in: RoundedRectangle(cornerRadius: 8))
        .foregroundStyle(colors.fg)
        .buttonStyle(.bordered)
    }

    private var text: String {
        switch warning {
        case .generic: return L("warning_generic")
        case .overLimit(let limit): return L("warning_over", formatPromille(limit))
        case .underLimit: return L("warning_under")
        case .hello(let g): return L(g == .morning ? "greeting_morning" : g == .day ? "greeting_day" : "greeting_evening")
        case .slowDown: return L("warning_slow_down")
        case .poisoning: return L("warning_poisoning")
        }
    }

    private func open(_ s: String) { if let url = URL(string: s) { openURL(url) } }
}

struct ProfileSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var sex = Sex.UNSPECIFIED
    @State private var age = ""
    @State private var height = ""
    @State private var weight = ""
    @State private var license = License.NONE

    private var parsed: BodyProfile? {
        guard let a = BodyProfile.parse(age, range: BodyProfile.ageRange), let h = BodyProfile.parse(height, range: BodyProfile.heightRange),
              let w = BodyProfile.parse(weight, range: BodyProfile.weightRange) else { return nil }
        return BodyProfile(sex: sex, age: a, heightCm: h, weightKg: w, license: license)
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker(L("profile_sex"), selection: $sex) {
                    Text(L("sex_male")).tag(Sex.MALE)
                    Text(L("sex_female")).tag(Sex.FEMALE)
                    Text(L("sex_unspecified")).tag(Sex.UNSPECIFIED)
                }
                field("profile_age", $age, BodyProfile.ageRange)
                field("profile_height", $height, BodyProfile.heightRange)
                field("profile_weight", $weight, BodyProfile.weightRange)
                Picker(L("profile_license"), selection: $license) {
                    ForEach(License.allCases, id: \.self) { Text(L("license_" + $0.rawValue.lowercased())).tag($0) }
                }
                Section { Text(L("profile_note")).font(.footnote).foregroundStyle(.secondary) }
                if model.prefs.profile != nil {
                    Button(L("profile_clear"), role: .destructive) { model.updatePrefs { $0.profile = nil }; dismiss() }
                }
            }
            .navigationTitle(L("profile_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("action_cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("action_save")) { model.updatePrefs { $0.profile = parsed }; dismiss() }.disabled(parsed == nil)
                }
            }
            .onAppear {
                guard let p = model.prefs.profile else { return }
                sex = p.sex; age = "\(p.age)"; height = "\(p.heightCm)"; weight = "\(p.weightKg)"; license = p.license
            }
        }
    }

    private func field(_ key: String, _ value: Binding<String>, _ range: ClosedRange<Int>) -> some View {
        VStack(alignment: .leading) {
            TextField(L(key), text: value).keyboardType(.numberPad)
            if !value.wrappedValue.isEmpty && BodyProfile.parse(value.wrappedValue, range: range) == nil {
                Text(L("invalid_range")).font(.caption).foregroundStyle(.red)
            }
        }
    }
}

struct BeerEditSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let beer: BeerEvent
    @State private var start = Date()
    @State private var finish: Date?
    @State private var cl = ""
    @State private var abv = ""

    var body: some View {
        NavigationStack {
            Form {
                DatePicker(L("beer_started", ""), selection: $start, in: ...Date(), displayedComponents: [.date, .hourAndMinute])
                if let f = finish {
                    DatePicker(L("beer_finished", ""), selection: Binding(get: { f }, set: { finish = $0 }),
                               in: start...max(start, Date()), displayedComponents: [.date, .hourAndMinute])
                    Button(L("action_not_finished")) { finish = nil }
                } else {
                    Button(L("beer_finished_unset")) { finish = max(start, min(Date(), start.addingTimeInterval(20 * 60))) }
                }
                SizeFields(cl: $cl, abv: $abv)
            }
            .navigationTitle(L("edit_beer"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("action_cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("action_save")) { save() }.disabled(BeerRules.parseCl(cl) == nil || BeerRules.parseAbv(abv) == nil)
                }
            }
            .onAppear {
                start = Date(timeIntervalSince1970: Double(beer.at) / 1000)
                finish = beer.finishedAt.map { Date(timeIntervalSince1970: Double($0) / 1000) }
                cl = BeerRules.formatCl(beer.sizeMl, separator: decimalSeparator)
                abv = BeerRules.formatAbv(beer.abv, separator: decimalSeparator)
            }
        }
    }

    private func save() {
        guard let size = BeerRules.parseCl(cl), let a = BeerRules.parseAbv(abv) else { return }
        var e = beer
        e.at = Int64(start.timeIntervalSince1970 * 1000)
        e.finishedAt = finish.map { Int64($0.timeIntervalSince1970 * 1000) }
        e.sizeMl = size
        e.abv = a
        model.updateBeer(e)
        dismiss()
    }
}

struct SizeFields: View {
    @Binding var cl: String
    @Binding var abv: String

    var body: some View {
        VStack(alignment: .leading) {
            TextField(L("size_custom_cl"), text: $cl).keyboardType(.decimalPad)
            if BeerRules.parseCl(cl) == nil { Text(L("invalid_size")).font(.caption).foregroundStyle(.red) }
        }
        VStack(alignment: .leading) {
            TextField(L("abv_label"), text: $abv).keyboardType(.decimalPad)
            if BeerRules.parseAbv(abv) == nil { Text(L("invalid_abv")).font(.caption).foregroundStyle(.red) }
        }
    }
}

/// 33 / 50 cl presets or custom, ABV prefilled. Used for long-press +1 and the default size setting.
struct BeerSizeSheet: View {
    @Environment(\.dismiss) private var dismiss
    let title: String
    let initial: BeerSize
    let confirm: String
    let onConfirm: (BeerSize) -> Void
    @State private var cl = ""
    @State private var abv = ""

    var body: some View {
        NavigationStack {
            Form {
                Section(L("pick_size")) {
                    HStack {
                        ForEach(BeerRules.presetsMl, id: \.self) { ml in
                            Button(L("size_cl", BeerRules.formatCl(ml, separator: decimalSeparator))) {
                                cl = BeerRules.formatCl(ml, separator: decimalSeparator)
                            }
                            .buttonStyle(.bordered)
                            .tint(BeerRules.parseCl(cl) == ml ? .accentColor : .secondary)
                        }
                    }
                }
                Section(L("size_custom")) { SizeFields(cl: $cl, abv: $abv) }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("action_cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(confirm) {
                        guard let ml = BeerRules.parseCl(cl), let a = BeerRules.parseAbv(abv) else { return }
                        onConfirm(BeerSize(sizeMl: ml, abv: a))
                        dismiss()
                    }
                    .disabled(BeerRules.parseCl(cl) == nil || BeerRules.parseAbv(abv) == nil)
                }
            }
            .onAppear {
                cl = BeerRules.formatCl(initial.sizeMl, separator: decimalSeparator)
                abv = BeerRules.formatAbv(initial.abv, separator: decimalSeparator)
            }
        }
        .presentationDetents([.medium, .large])
    }
}
