import Foundation

/// Constants shared by the main app and its two extensions (AtzorShieldAction,
/// AtzorActivityMonitor). All three targets must belong to the same App Group
/// in Xcode's Signing & Capabilities for UserDefaults(suiteName:) to actually
/// share storage — see ios/SETUP.md.
enum AtzorShared {
    static let appGroupID = "group.app.atzor.shared"
}
