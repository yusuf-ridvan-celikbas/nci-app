import SwiftUI
import UIKit
import CansinCore

/// Same tones as Android Palettes.kt (AA-checked there). Neutrals use iOS system colors.
enum Palette {
    struct Tones { let primary: UInt32; let onPrimary: UInt32; let container: UInt32; let onContainer: UInt32 }

    private static let tones: [Accent: (light: Tones, dark: Tones)] = [
        .AMBER: (Tones(primary: 0x7A5900, onPrimary: 0xFFFFFF, container: 0xFFDEA3, onContainer: 0x261900),
                 Tones(primary: 0xF8BD48, onPrimary: 0x402D00, container: 0x5C4200, onContainer: 0xFFDEA3)),
        .RED: (Tones(primary: 0xBA1A1A, onPrimary: 0xFFFFFF, container: 0xFFDAD6, onContainer: 0x410002),
               Tones(primary: 0xFFB4AB, onPrimary: 0x690005, container: 0x93000A, onContainer: 0xFFDAD6)),
        .BLUE: (Tones(primary: 0x0061A4, onPrimary: 0xFFFFFF, container: 0xD1E4FF, onContainer: 0x001D36),
                Tones(primary: 0x9ECAFF, onPrimary: 0x003258, container: 0x00497D, onContainer: 0xD1E4FF)),
        .YELLOW: (Tones(primary: 0x695F00, onPrimary: 0xFFFFFF, container: 0xF7E46C, onContainer: 0x201C00),
                  Tones(primary: 0xDAC851, onPrimary: 0x373100, container: 0x4F4800, onContainer: 0xF7E46C)),
        .GREEN: (Tones(primary: 0x006E1C, onPrimary: 0xFFFFFF, container: 0x94F990, onContainer: 0x002204),
                 Tones(primary: 0x78DC77, onPrimary: 0x00390A, container: 0x005313, onContainer: 0x94F990)),
        .PURPLE: (Tones(primary: 0x6750A4, onPrimary: 0xFFFFFF, container: 0xEADDFF, onContainer: 0x21005D),
                  Tones(primary: 0xD0BCFF, onPrimary: 0x381E72, container: 0x4F378B, onContainer: 0xEADDFF)),
    ]

    static func primary(_ a: Accent) -> Color { dynamic(a, \.primary) }
    static func onPrimary(_ a: Accent) -> Color { dynamic(a, \.onPrimary) }
    static func container(_ a: Accent) -> Color { dynamic(a, \.container) }
    static func onContainer(_ a: Accent) -> Color { dynamic(a, \.onContainer) }

    /// Versus sides stay fixed in every theme (light tones).
    static let red = Color(rgb: 0xBA1A1A)
    static let blue = Color(rgb: 0x0061A4)
    static let onSide = Color(rgb: 0xFFFFFF)
    static let danger = (bg: dynamic(.RED, \.container), fg: dynamic(.RED, \.onContainer))
    static let caution = (bg: dynamic(.AMBER, \.container), fg: dynamic(.AMBER, \.onContainer))

    static func side(_ side: CansinCore.Side?) -> Color { side == .BLUE ? blue : red }

    private static func dynamic(_ a: Accent, _ key: KeyPath<Tones, UInt32>) -> Color {
        let pair = tones[a] ?? tones[.AMBER]!
        return Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(rgb: pair.dark[keyPath: key]) : UIColor(rgb: pair.light[keyPath: key]) })
    }
}

extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(red: CGFloat((rgb >> 16) & 0xFF) / 255, green: CGFloat((rgb >> 8) & 0xFF) / 255,
                  blue: CGFloat(rgb & 0xFF) / 255, alpha: 1)
    }
}

extension Color {
    init(rgb: UInt32) { self.init(uiColor: UIColor(rgb: rgb)) }
}

extension ThemeMode {
    var colorScheme: ColorScheme? {
        switch self {
        case .SYSTEM: return nil
        case .LIGHT: return .light
        case .DARK: return .dark
        }
    }
}
