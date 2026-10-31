import SwiftUI
import CansinCore

func formatAmount(_ minor: Int64) -> String { NoteRules.formatAmount(minor, separator: decimalSeparator) }

func timeText(_ ms: Int64) -> String {
    Date(timeIntervalSince1970: Double(ms) / 1000).formatted(date: .omitted, time: .shortened)
}

struct NotesView: View {
    @Environment(AppModel.self) private var model
    @State private var text = ""
    @State private var amount = ""
    @State private var side: Side?
    @State private var editing: Note?

    private var amountInput: AmountInput { NoteRules.parseAmount(amount) }

    var body: some View {
        List {
            Section { totals }
            if model.notes.isEmpty {
                Text(L("no_notes")).foregroundStyle(.secondary)
            } else {
                Section {
                    ForEach(model.notes, id: \.id) { note in
                        Button { editing = note } label: { NoteRow(note: note, session: model.session) }
                            .foregroundStyle(.primary)
                    }
                }
            }
        }
        .navigationTitle(L("tab_title"))
        .safeAreaInset(edge: .bottom) { quickAdd }
        .sheet(item: $editing) { NoteEditSheet(note: $0) }
    }

    @ViewBuilder private var totals: some View {
        let t = model.tabTotals
        Text(L("tab_total", formatAmount(t.total))).font(.headline)
        if model.isVersus {
            Text(L("tab_side_total", sideName(model.session, .RED), formatAmount(t.red)))
            Text(L("tab_side_total", sideName(model.session, .BLUE), formatAmount(t.blue)))
            Text(L("tab_side_total", L("side_shared"), formatAmount(t.shared)))
        }
    }

    private var quickAdd: some View {
        VStack(spacing: 8) {
            if model.isVersus {
                Picker("", selection: $side) {
                    Text(L("side_red")).tag(Side?.some(.RED))
                    Text(L("side_blue")).tag(Side?.some(.BLUE))
                    Text(L("side_shared")).tag(Side?.none)
                }
                .pickerStyle(.segmented)
            }
            HStack {
                TextField(L("note_hint"), text: $text, axis: .vertical).lineLimit(1...3)
                    .onChange(of: text) { _, new in if new.count > Note.maxText { text = String(new.prefix(Note.maxText)) } }
                TextField(L("amount_hint"), text: $amount).keyboardType(.decimalPad).frame(width: 90)
                Button { add() } label: { Image(systemName: "plus.circle.fill").font(.title2) }
                    .accessibilityLabel(L("add_note"))
                    .disabled(NoteRules.cleanText(text) == nil || amountInput == .invalid)
            }
            if amountInput == .invalid { Text(L("invalid_amount")).font(.caption).foregroundStyle(.red) }
        }
        .padding()
        .background(.bar)
    }

    private func add() {
        var minor: Int64?
        if case .valid(let v) = amountInput { minor = v }
        model.addNote(text: text, amountMinor: minor, side: side)
        text = ""
        amount = ""
    }
}

private struct NoteRow: View {
    let note: Note
    let session: Session?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(note.text).lineLimit(3)
                Spacer()
                if let a = note.amountMinor { Text(formatAmount(a)).monospacedDigit().bold() }
            }
            HStack(spacing: 6) {
                Text(timeText(note.editedTime ?? note.createdAt))
                if note.editedTime != nil { Text(L("note_edited")) }
                if let p = note.person { Text("· \(p)") }
                if let s = note.side { Text("· \(sideName(session, s))").foregroundStyle(Palette.side(s)) }
            }
            .font(.caption).foregroundStyle(.secondary)
        }
    }
}

struct NoteEditSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let note: Note

    @State private var text = ""
    @State private var person = ""
    @State private var amount = ""
    @State private var side: Side?
    @State private var time = Date()
    @State private var timeChanged = false
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Form {
                TextField(L("note_text"), text: $text, axis: .vertical).lineLimit(1...6)
                TextField(L("note_person"), text: $person)
                    .onChange(of: person) { _, new in if new.count > Note.maxPerson { person = String(new.prefix(Note.maxPerson)) } }
                TextField(L("note_amount"), text: $amount).keyboardType(.decimalPad)
                if NoteRules.parseAmount(amount) == .invalid { Text(L("invalid_amount")).font(.caption).foregroundStyle(.red) }
                if model.isVersus && model.session?.id == note.sessionId {
                    Picker("", selection: $side) {
                        Text(L("side_red")).tag(Side?.some(.RED))
                        Text(L("side_blue")).tag(Side?.some(.BLUE))
                        Text(L("side_shared")).tag(Side?.none)
                    }
                    .pickerStyle(.segmented)
                }
                DatePicker(L("pick_time"), selection: $time, displayedComponents: .hourAndMinute)
                    .onChange(of: time) { if loaded { timeChanged = true } }
                Button(L("action_delete"), role: .destructive) { model.deleteNote(note); dismiss() }
            }
            .navigationTitle(L("edit_note"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("action_cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("action_save")) { save() }
                        .disabled(NoteRules.cleanText(text) == nil || NoteRules.parseAmount(amount) == .invalid)
                }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard !loaded else { return }
        text = note.text
        person = note.person ?? ""
        amount = note.amountMinor.map(formatAmount) ?? ""
        side = note.side
        time = Date(timeIntervalSince1970: Double(note.editedTime ?? note.createdAt) / 1000)
        DispatchQueue.main.async { loaded = true }
    }

    private func save() {
        var updated = note
        updated.text = text
        updated.person = person
        switch NoteRules.parseAmount(amount) {
        case .valid(let v): updated.amountMinor = v
        default: updated.amountMinor = nil
        }
        updated.side = side
        if timeChanged {
            let c = Calendar.current.dateComponents([.hour, .minute], from: time)
            updated.editedTime = NoteRules.pickTime(createdAt: note.createdAt, hour: c.hour ?? 0, minute: c.minute ?? 0, calendar: .current)
        }
        model.updateNote(updated)
        dismiss()
    }
}
