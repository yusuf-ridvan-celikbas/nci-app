import SwiftUI
import CansinCore

func sideName(_ session: Session?, _ side: Side) -> String {
    let custom = side == .RED ? session?.redName : session?.blueName
    let name = custom ?? L(side == .RED ? "side_red" : "side_blue")
    return session?.meSide == side ? L("versus_name_me", name) : name
}

struct VersusView: View {
    @Environment(AppModel.self) private var model
    @State private var editing = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                PromilleCard()
                let red = model.beers.filter { $0.side == .RED }.count
                let blue = model.beers.filter { $0.side == .BLUE }.count
                Text(L("versus_score", red, blue))
                    .font(.largeTitle.bold()).monospacedDigit()
                    .accessibilityLabel(L("versus_score_description", red, blue))
                HStack(alignment: .top, spacing: 16) {
                    SideColumn(side: .RED, count: red)
                    SideColumn(side: .BLUE, count: blue)
                }
                TimerSection()
                WaterSection()
            }
            .padding()
        }
        .navigationTitle(L("versus_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { CounterToolbar(onVersus: { editing = true }) }
        .safeAreaInset(edge: .bottom) { EndSessionBar() }
        .sheet(isPresented: $editing) { VersusSetupSheet(editing: true) }
    }
}

private struct SideColumn: View {
    @Environment(AppModel.self) private var model
    let side: Side
    let count: Int

    var body: some View {
        let name = sideName(model.session, side)
        VStack(spacing: 12) {
            Text(name).font(.headline).lineLimit(2).multilineTextAlignment(.center)
            Text("\(count)").font(.system(size: 56, weight: .bold, design: .rounded)).monospacedDigit()
            AddButton(color: Palette.side(side), foreground: Palette.onSide, label: L("versus_add", name), size: 110,
                      onTap: { model.addBeer(side: side) }, onLongPress: nil)
            Button { model.removeLatest(side: side) } label: { Text(L("minus_one")).font(.title3).frame(minWidth: 56) }
                .buttonStyle(.bordered)
                .disabled(count == 0)
                .accessibilityLabel(L("versus_remove", name))
        }
        .frame(maxWidth: .infinity)
    }
}

struct VersusSetupSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let editing: Bool

    @State private var red = SideDraft()
    @State private var blue = SideDraft()
    @State private var me: Side?
    @State private var loaded = false

    struct SideDraft { var name = ""; var friendId: Int64?; var save = false }

    var body: some View {
        NavigationStack {
            Form {
                if !editing && model.session != nil {
                    Text(L("versus_ends_session")).font(.footnote).foregroundStyle(.secondary)
                }
                sideSection(.RED, $red)
                sideSection(.BLUE, $blue)
                Section {
                    Picker(L("versus_me"), selection: $me) {
                        Text(L("side_red")).tag(Side?.some(.RED))
                        Text(L("side_blue")).tag(Side?.some(.BLUE))
                        Text(L("versus_me_none")).tag(Side?.none)
                    }
                    .pickerStyle(.inline)
                } footer: { Text(L("versus_me_note")) }
            }
            .navigationTitle(L("versus_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("action_cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(L(editing ? "action_save" : "action_start")) { save() } }
            }
            .onAppear(perform: load)
        }
    }

    private func sideSection(_ side: Side, _ draft: Binding<SideDraft>) -> some View {
        Section(L(side == .RED ? "side_red" : "side_blue")) {
            TextField(L(side == .RED ? "side_red" : "side_blue"), text: draft.name)
                .onChange(of: draft.wrappedValue.name) { _, new in
                    let value = String(new.prefix(FriendProfile.maxName))
                    if value != new { draft.wrappedValue.name = value }
                    if let id = draft.wrappedValue.friendId,
                       !model.friends.contains(where: { $0.id == id && $0.name == value.trimmingCharacters(in: .whitespaces) }) {
                        draft.wrappedValue.friendId = nil
                    }
                }
            if !model.friends.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(model.friends, id: \.id) { f in
                            Button(f.name) { draft.wrappedValue.name = f.name; draft.wrappedValue.friendId = f.id }
                                .buttonStyle(.bordered)
                                .tint(draft.wrappedValue.friendId == f.id ? Palette.side(side) : .secondary)
                        }
                    }
                }
            }
            let name = draft.wrappedValue.name.trimmingCharacters(in: .whitespaces)
            if draft.wrappedValue.friendId == nil && !name.isEmpty
                && !model.friends.contains(where: { $0.name.caseInsensitiveCompare(name) == .orderedSame }) {
                Toggle(L("versus_save_friend"), isOn: draft.save)
            }
        }
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        guard editing, let s = model.session else { return }
        red = SideDraft(name: s.redName ?? "", friendId: s.redFriendId)
        blue = SideDraft(name: s.blueName ?? "", friendId: s.blueFriendId)
        me = s.meSide
    }

    private func save() {
        var setup = VersusSetup(redName: red.name, blueName: blue.name, redFriendId: red.friendId,
                                blueFriendId: blue.friendId, meSide: me)
        if red.save, setup.redFriendId == nil { setup.redFriendId = model.saveFriend(name: red.name, side: .RED) }
        if blue.save, setup.blueFriendId == nil { setup.blueFriendId = model.saveFriend(name: blue.name, side: .BLUE) }
        if editing { model.updateVersus(setup) } else { model.startVersus(setup) }
        dismiss()
    }
}
