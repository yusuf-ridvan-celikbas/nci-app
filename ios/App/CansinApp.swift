import SwiftUI
import UserNotifications

@main
struct CansinApp: App {
    @UIApplicationDelegateAdaptor(NotificationDelegate.self) private var delegate
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .tint(Palette.primary(model.prefs.accent))
                .preferredColorScheme(model.prefs.themeMode.colorScheme)
                .onOpenURL { model.open(url: $0) }
                .onAppear { delegate.model = model }
        }
        .onChange(of: phase) { _, new in if new == .active { model.onForeground() } }
    }
}

/// Handles reminder actions (they require unlock, so the store is readable).
final class NotificationDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    @MainActor weak var model: AppModel?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let action = response.actionIdentifier
        let info = response.notification.request.content.userInfo
        await MainActor.run {
            let model = self.model ?? AppModel()
            model.handleNotification(action: action, userInfo: info)
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        if !model.prefs.ageConfirmed {
            AgeGateView()
        } else {
            NavigationStack(path: $model.path) {
                Group { if model.isVersus { VersusView() } else { CounterView() } }
                    .navigationDestination(for: Route.self) { route in
                        switch route {
                        case .tab: NotesView()
                        case .promille: PromilleView()
                        case .history: HistoryView()
                        case .session(let id): SessionDetailView(sessionId: id)
                        case .settings: SettingsView()
                        }
                    }
            }
            .overlay(alignment: .bottom) { UndoBar() }
        }
    }
}

struct AgeGateView: View {
    @Environment(AppModel.self) private var model
    @State private var declined = false

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Text(L("age_title")).font(.title.bold()).multilineTextAlignment(.center)
            Text(L(declined ? "age_declined" : "age_body")).multilineTextAlignment(.center).foregroundStyle(.secondary)
            Spacer()
            Button(L("age_confirm")) { model.updatePrefs { $0.ageConfirmed = true } }
                .buttonStyle(.borderedProminent).controlSize(.large)
            // iOS apps must not quit themselves; declining just keeps the gate closed.
            Button(L("age_decline")) { declined = true }.controlSize(.large)
        }
        .padding(32)
    }
}

struct UndoBar: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        if let item = model.undo {
            HStack {
                Text(L(item.messageKey))
                Spacer()
                Button(L("undo")) { model.undoLast() }.bold()
            }
            .padding()
            .background(.thickMaterial, in: RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal)
            .padding(.bottom, 72)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .accessibilityElement(children: .contain)
        }
    }
}
