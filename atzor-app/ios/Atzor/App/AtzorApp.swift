import SwiftUI
import FamilyControls

/// עצור — iOS entry point.
///
/// REQUIREMENTS BEFORE THIS COMPILES AND RUNS:
/// 1. Xcode 15+ with an iOS 17 SDK.
/// 2. A paid Apple Developer account.
/// 3. The Family Controls entitlement, requested at:
///    https://developer.apple.com/contact/request/family-controls-distribution
///    (approval can take weeks; the app runs on your own device with the
///    development entitlement once Xcode adds the capability).
/// 4. In Xcode: Signing & Capabilities → add "Family Controls" to BOTH the
///    app target and the AtzorShield extension target.
@main
struct AtzorApp: App {
    @StateObject private var model = AtzorModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environment(\.layoutDirection, .rightToLeft)
                .task {
                    // Ask for Screen Time authorization on first launch.
                    do {
                        try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                    } catch {
                        model.authorizationError = error.localizedDescription
                    }
                }
        }
    }
}
