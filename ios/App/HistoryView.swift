import SwiftUI
import Charts
import CansinCore

struct SessionSummary: Identifiable {
    let session: Session
    let beers: Int
    let notes: Int
    let water: Int
    var id: Int64 { session.id }
}

func durationText(_ ms: Int64) -> String {
    let minutes = Int(ms / 60_000)
    return L("duration_value", minutes / 60, minutes % 60)
}

func dateTimeText(_ ms: Int64) -> String {
    Date(timeIntervalSince1970: Double(ms) / 1000).formatted(date: .abbreviated, time: .shortened)
}

struct HistoryView: View {
    @Environment(AppModel.self) private var model
    @State private var tab = 0
    @State private var summaries: [SessionSummary] = []
    @State private var overview = Overview()

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $tab) {
                Text(L("history_sessions")).tag(0)
                Text(L("history_stats")).tag(1)
            }
            .pickerStyle(.segmented)
            .padding()
            if tab == 0 { sessions } else { StatsView(overview: overview) }
        }
        .navigationTitle(L("history_title"))
        .onAppear(perform: load)
    }

    @ViewBuilder private var sessions: some View {
        if summaries.isEmpty {
            Spacer()
            Text(L("history_empty")).foregroundStyle(.secondary)
            Spacer()
        } else {
            List(summaries) { s in
                Button { model.path.append(.session(s.id)) } label: { row(s) }.foregroundStyle(.primary)
            }
        }
    }

    private func row(_ s: SessionSummary) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            let date = dateTimeText(s.session.startedAt)
            Text(s.session.mode == .VERSUS ? L("history_versus_row", date) : date).font(.headline)
            HStack(spacing: 8) {
                Text(L(plural: "beers_count", s.beers))
                if s.notes > 0 { Text(L(plural: "notes_count", s.notes)) }
                if s.water > 0 { Text(L("water_count", s.water)) }
                if let d = StatsRules.durationMs(s.session) { Text(durationText(d)) }
            }
            .font(.caption).foregroundStyle(.secondary)
        }
    }

    private func load() {
        guard let repo = model.repository else { return }
        let records = repo.endedSessions()
        summaries = records.map { SessionSummary(session: $0.core, beers: $0.beers.count, notes: $0.notes.count, water: $0.water.count) }
        overview = StatsRules.overview(sessions: records.map(\.core), beers: records.flatMap { repo.beers($0) },
                                       waterBySession: Dictionary(uniqueKeysWithValues: records.map { ($0.id, $0.water.count) }),
                                       profile: model.prefs.profile)
    }
}

struct StatsView: View {
    let overview: Overview

    var body: some View {
        List {
            Section {
                Text(L(plural: "stats_sessions", overview.sessions)).font(.headline)
                if let v = overview.avgBeers { Text(L("stats_avg_beers", oneDecimal(v))) }
                if let v = overview.avgPaceMin { Text(L("stats_pace", oneDecimal(v))) }
                if let v = overview.waterPerBeer { Text(L("stats_water_ratio", oneDecimal(v))) }
            }
            if !overview.perSession.isEmpty {
                Section(L("stats_chart_beers")) {
                    chart(overview.perSession.map { Double($0.beers) }, format: { "\(Int($0))" })
                }
                let peaks = overview.perSession.compactMap(\.peak)
                if !peaks.isEmpty {
                    Section(L("stats_chart_peak")) { chart(overview.perSession.map { $0.peak ?? 0 }, format: formatPromille) }
                }
                Text(L("stats_peak_note")).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    private func chart(_ values: [Double], format: @escaping (Double) -> String) -> some View {
        Chart(Array(values.enumerated()), id: \.offset) { item in
            BarMark(x: .value("#", item.offset + 1), y: .value("", item.element))
        }
        .frame(height: 160)
        .accessibilityElement()
        .accessibilityLabel(L("stats_chart_description", values.map(format).joined(separator: ", ")))
    }

    private func oneDecimal(_ v: Double) -> String { String(format: "%.1f", locale: Locale.current, v) }
}

struct SessionDetailView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let sessionId: Int64
    @State private var confirming = false

    var body: some View {
        let repo = model.repository
        let record = repo?.session(sessionId)
        List {
            if let record, let repo {
                let s = record.core
                let beers = repo.beers(record)
                let notes = repo.notes(record)
                Section {
                    Text(dateTimeText(s.startedAt)).font(.headline)
                    if let d = StatsRules.durationMs(s) { Text(durationText(d)) }
                    if s.mode == .VERSUS {
                        Text(L("tab_side_total", sideName(s, .RED), "\(beers.filter { $0.side == .RED }.count)"))
                        Text(L("tab_side_total", sideName(s, .BLUE), "\(beers.filter { $0.side == .BLUE }.count)"))
                    } else {
                        Text(L("detail_beers", beers.count))
                    }
                    Text(L("water_count", record.water.count))
                    let totals = VersusRules.totals(notes)
                    if totals.total > 0 { Text(L("tab_total", formatAmount(totals.total))) }
                }
                if !beers.isEmpty {
                    Section {
                        ForEach(beers) { b in
                            HStack {
                                Text(timeText(b.at))
                                Text(sizeLabel(BeerSize(sizeMl: b.sizeMl, abv: b.abv))).foregroundStyle(.secondary)
                                if b.side != .SOLO { Spacer(); Circle().fill(Palette.side(b.side)).frame(width: 10, height: 10) }
                            }
                        }
                    }
                }
                if !notes.isEmpty {
                    Section(L("tab_title")) {
                        ForEach(notes) { n in
                            HStack {
                                Text(n.text)
                                Spacer()
                                if let a = n.amountMinor { Text(formatAmount(a)).monospacedDigit() }
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(L("session_detail_title"))
        .toolbar {
            Button(role: .destructive) { confirming = true } label: { Image(systemName: "trash") }
                .accessibilityLabel(L("action_delete"))
        }
        .alert(L("delete_session_title"), isPresented: $confirming) {
            Button(L("action_delete"), role: .destructive) {
                repo?.deleteEnded(sessionId)
                model.reload()
                dismiss()
            }
            Button(L("action_cancel"), role: .cancel) {}
        } message: { Text(L("delete_session_body")) }
    }
}
