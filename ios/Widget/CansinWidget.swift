import SwiftUI
import WidgetKit
import CansinCore

struct WidgetData {
    var appName: String
    var page: WidgetPage
    var accent: Accent
    var snapshot: CounterSnapshot
    var timerEnabled: Bool
    var timerStartedAt: Date?
    var canStartTimer: Bool
}

struct CounterEntry: TimelineEntry {
    let date: Date
    /// nil = store unreadable (device locked, complete protection): show a neutral placeholder.
    let data: WidgetData?
}

struct CounterProvider: TimelineProvider {
    func placeholder(in context: Context) -> CounterEntry { CounterEntry(date: .now, data: nil) }

    func getSnapshot(in context: Context, completion: @escaping (CounterEntry) -> Void) {
        Task { @MainActor in completion(CounterEntry(date: .now, data: Self.load())) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CounterEntry>) -> Void) {
        Task { @MainActor in
            let data = Self.load()
            var entries = [CounterEntry(date: .now, data: data)]
            // A session goes stale after 6 h without activity: show it as empty from then on.
            if var stale = data, let repo = Effects.makeRepository(), let s = repo.openSessionRecord() {
                let last = max(s.startedAt, s.beers.map(\.at).max() ?? 0, s.notes.map(\.createdAt).max() ?? 0, s.water.map(\.at).max() ?? 0)
                let staleAt = Date(timeIntervalSince1970: Double(last + SessionRules.defaultInactivityMs) / 1000)
                if staleAt > .now {
                    stale.snapshot = CounterSnapshot()
                    stale.timerStartedAt = nil
                    entries.append(CounterEntry(date: staleAt, data: stale))
                }
            }
            completion(Timeline(entries: entries, policy: .never))
        }
    }

    @MainActor static func load() -> WidgetData? {
        guard let repo = Effects.makeRepository() else { return nil }
        let settings = SettingsStore.shared
        let snapshot = repo.snapshot()
        let timer = settings.timer
        let running = settings.timerEnabled && TimerRules.isCurrent(timer, sessionId: snapshot.sessionId)
        return WidgetData(
            appName: L(settings.appName.labelKey), page: settings.widgetPage, accent: settings.accent, snapshot: snapshot,
            timerEnabled: settings.timerEnabled,
            timerStartedAt: running ? timer.startedAt.map { Date(timeIntervalSince1970: Double($0) / 1000) } : nil,
            canStartTimer: TimerRules.canStart(enabled: settings.timerEnabled, state: timer, myLatestBeer: snapshot.myLatestBeer))
    }
}

struct CounterWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CounterEntry

    var body: some View {
        Group {
            if let data = entry.data {
                VStack(spacing: 6) {
                    header(data)
                    Spacer(minLength: 0)
                    switch data.page {
                    case .beer: beerPage(data)
                    case .tab: tabPage(data)
                    case .water: waterPage(data)
                    }
                    Spacer(minLength: 0)
                }
            } else {
                Text(L("widget_locked")).font(.footnote).multilineTextAlignment(.center).foregroundStyle(.secondary)
            }
        }
        .containerBackground(for: .widget) { Color(.systemBackground) }
    }

    private var wide: Bool { family != .systemSmall }

    private func pageTitle(_ page: WidgetPage) -> String {
        L(page == .beer ? "widget_page_beer" : page == .tab ? "widget_page_tab" : "widget_page_water")
    }

    private func header(_ d: WidgetData) -> some View {
        HStack(spacing: 4) {
            Button(intent: WidgetPageIntent(delta: -1)) { Image(systemName: "chevron.left") }
                .accessibilityLabel(L("widget_prev"))
            Spacer(minLength: 0)
            VStack(spacing: 2) {
                Text(wide ? "\(d.appName) · \(pageTitle(d.page))" : pageTitle(d.page)).font(.caption.bold()).lineLimit(1)
                HStack(spacing: 3) {
                    ForEach(WidgetPage.allCases, id: \.self) { p in
                        Circle().fill(p == d.page ? Palette.primary(d.accent) : Color.secondary.opacity(0.4)).frame(width: 5, height: 5)
                    }
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L("widget_page_position", pageTitle(d.page), d.page.rawValue + 1, WidgetPage.allCases.count))
            Spacer(minLength: 0)
            Button(intent: WidgetPageIntent(delta: 1)) { Image(systemName: "chevron.right") }
                .accessibilityLabel(L("widget_next"))
        }
        .buttonStyle(.plain)
        .font(.caption)
    }

    @ViewBuilder private func beerPage(_ d: WidgetData) -> some View {
        let s = d.snapshot
        if s.versus {
            Link(destination: URL(string: "cansin://app")!) {
                VStack {
                    Text(L("versus_score", s.red, s.blue)).font(.title.bold()).monospacedDigit()
                    Text(L("widget_open")).font(.caption)
                }
            }
            .accessibilityLabel(L("versus_score_description", s.red, s.blue))
        } else {
            HStack(spacing: 12) {
                Text("\(s.count)").font(.system(size: 40, weight: .bold, design: .rounded)).monospacedDigit()
                Button(intent: AddBeerIntent()) {
                    Text(L("plus_one")).font(.title2.bold()).foregroundStyle(Palette.onPrimary(d.accent))
                        .frame(width: 56, height: 44)
                        .background(Palette.primary(d.accent), in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("add_beer"))
                if wide { timer(d) }
            }
        }
    }

    @ViewBuilder private func timer(_ d: WidgetData) -> some View {
        if d.timerEnabled && d.snapshot.hasMe {
            if let started = d.timerStartedAt {
                Button(intent: ToggleTimerIntent()) {
                    HStack(spacing: 4) {
                        Image(systemName: "stop.fill")
                        Text(started, style: .timer).monospacedDigit().frame(maxWidth: 64)
                    }
                }
                .accessibilityLabel(L("timer_stop"))
            } else if d.canStartTimer {
                Button(intent: ToggleTimerIntent()) { Label(L("timer_idle"), systemImage: "play.fill") }
                    .accessibilityLabel(L("timer_start"))
            }
        }
    }

    private func tabPage(_ d: WidgetData) -> some View {
        Link(destination: URL(string: "cansin://tab")!) {
            VStack(spacing: 2) {
                Text(NoteRules.formatAmount(d.snapshot.tabMinor, separator: decimalSeparator)).font(.title.bold()).monospacedDigit()
                if wide { Text(L(plural: "notes_count", d.snapshot.notes)).font(.caption) }
                Text(L("widget_open_tab")).font(.caption2).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder private func waterPage(_ d: WidgetData) -> some View {
        HStack(spacing: 12) {
            Text("\(d.snapshot.water)").font(.system(size: 40, weight: .bold, design: .rounded)).monospacedDigit()
            if d.snapshot.hasMe {
                Button(intent: AddWaterIntent()) {
                    Image(systemName: "drop.fill").font(.title2).foregroundStyle(Palette.onPrimary(d.accent))
                        .frame(width: 56, height: 44)
                        .background(Palette.primary(d.accent), in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("water_add"))
            } else {
                Link(L("widget_open"), destination: URL(string: "cansin://app")!).font(.caption)
            }
        }
    }
}

@main
struct CansinWidgetBundle: WidgetBundle {
    var body: some Widget { CounterWidget() }
}

struct CounterWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "CounterWidget", provider: CounterProvider()) { CounterWidgetView(entry: $0) }
            .configurationDisplayName(L("widget_name"))
            .description(L("widget_description"))
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}
