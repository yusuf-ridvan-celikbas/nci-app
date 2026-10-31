import SwiftUI
import CansinCore

struct CounterView: View {
    @Environment(AppModel.self) private var model
    @State private var pickingSize = false
    @State private var settingUpVersus = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                PromilleCard()
                Text(L(model.session != nil ? "counter_label" : "no_session")).foregroundStyle(.secondary)
                Text("\(model.snapshot.count)").font(.system(size: 80, weight: .bold, design: .rounded)).monospacedDigit()
                AddButton(color: Palette.primary(model.prefs.accent), foreground: Palette.onPrimary(model.prefs.accent),
                          label: L("add_beer"), onTap: { model.addBeer() }, onLongPress: { pickingSize = true })
                Text(L("counter_prompt")).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                Button { model.removeLatest() } label: { Text(L("minus_one")).font(.title2).frame(minWidth: 72) }
                    .buttonStyle(.bordered)
                    .disabled(model.snapshot.count == 0)
                    .accessibilityLabel(L("remove_beer"))
                TimerSection()
                WaterSection()
            }
            .padding()
        }
        .navigationTitle(L(model.prefs.appName.labelKey))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { CounterToolbar(onVersus: { settingUpVersus = true }) }
        .safeAreaInset(edge: .bottom) { EndSessionBar() }
        .sheet(isPresented: $pickingSize) {
            BeerSizeSheet(title: L("pick_size_title"), initial: model.prefs.beerSize, confirm: L("action_add")) { model.addBeer(size: $0) }
        }
        .sheet(isPresented: $settingUpVersus) { VersusSetupSheet(editing: false) }
    }
}

struct CounterToolbar: ToolbarContent {
    @Environment(AppModel.self) private var model
    var onVersus: () -> Void

    var body: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button { model.path.append(.tab) } label: { Image(systemName: "list.bullet.rectangle") }.accessibilityLabel(L("open_tab"))
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button { model.path.append(.history) } label: { Image(systemName: "clock.arrow.circlepath") }.accessibilityLabel(L("history_title"))
            Button(action: onVersus) { Image(systemName: "person.2") }.accessibilityLabel(L(model.isVersus ? "versus_edit" : "versus_start"))
            Button { model.path.append(.settings) } label: { Image(systemName: "gearshape") }.accessibilityLabel(L("settings"))
        }
    }
}

/// Big +1: tap adds, long-press picks a size.
struct AddButton: View {
    var color: Color
    var foreground: Color
    var label: String
    var text = L("plus_one")
    var size: CGFloat = 160
    var onTap: () -> Void
    var onLongPress: (() -> Void)?

    var body: some View {
        Text(text)
            .font(.system(size: size * 0.4, weight: .bold, design: .rounded))
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .background(color, in: Circle())
            .contentShape(Circle())
            .onTapGesture(perform: onTap)
            .onLongPressGesture(minimumDuration: 0.5) { onLongPress?() }
            .accessibilityElement()
            .accessibilityLabel(label)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction(perform: onTap)
            .accessibilityAction(named: L("pick_size")) { onLongPress?() }
    }
}

struct EndSessionBar: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Button(L("end_session")) { model.endSession() }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .frame(maxWidth: .infinity)
            .disabled(model.session == nil)
            .padding(.vertical, 8)
            .background(.bar)
    }
}

struct TimerSection: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        if model.prefs.timerEnabled && model.session != nil && model.meSide != nil {
            HStack {
                if model.timerRunningHere, let started = model.prefs.timer.startedAt {
                    Label(L("timer_running"), systemImage: "timer")
                    Text(Date(timeIntervalSince1970: Double(started) / 1000), style: .timer).monospacedDigit()
                    Spacer()
                    Button { model.stopTimer() } label: { Image(systemName: "stop.fill") }.accessibilityLabel(L("timer_stop"))
                } else {
                    Label(L("timer_idle"), systemImage: "timer").foregroundStyle(.secondary)
                    Spacer()
                    Button { model.startTimer() } label: { Image(systemName: "play.fill") }
                        .disabled(!model.canStartTimer)
                        .accessibilityLabel(L("timer_start"))
                }
            }
            .buttonStyle(.bordered)
            .padding(12)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
        }
    }
}

struct WaterSection: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        if model.session != nil && model.meSide != nil {
            VStack(spacing: 8) {
                if model.showWaterNudge {
                    HStack {
                        Image(systemName: "drop.fill")
                        Text(L("water_nudge"))
                        Spacer()
                        Button(L("action_not_now")) { model.waterDismissedAt = model.beersSinceWater }
                    }
                    .padding(12)
                    .background(Palette.container(model.prefs.accent), in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(Palette.onContainer(model.prefs.accent))
                }
                HStack {
                    Text(L("water_count", model.water.count))
                    Spacer()
                    Button(L("water_add")) { model.addWater() }.buttonStyle(.bordered)
                }
            }
        }
    }
}
