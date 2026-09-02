import Foundation
import SwiftUI
import FamilyControls
import ManagedSettings

/// Central state: which apps are shielded, session state, and the physical keys.
@MainActor
final class AtzorModel: ObservableObject {

    /// How long the emergency escape takes, mirroring the product promise.
    static let emergencyDelay: TimeInterval = 15 * 60

    private let store = ManagedSettingsStore()
    private let defaults = UserDefaults.standard

    /// The user's picks from the system FamilyActivityPicker.
    @Published var selection = FamilyActivitySelection() {
        didSet { persistSelection() }
    }

    /// `.distantFuture` means "locked until a key opens it".
    @Published var sessionEndAt: Date? {
        didSet { defaults.set(sessionEndAt, forKey: "sessionEndAt"); applyShield() }
    }

    @Published var nfcTagId: String? {
        didSet { defaults.set(nfcTagId, forKey: "nfcTagId") }
    }

    @Published var qrSecret: String? {
        didSet { defaults.set(qrSecret, forKey: "qrSecret") }
    }

    @Published var emergencyRequestedAt: Date? {
        didSet { defaults.set(emergencyRequestedAt, forKey: "emergencyRequestedAt") }
    }

    @Published var authorizationError: String?

    var sessionActive: Bool {
        guard let end = sessionEndAt else { return false }
        return end > Date()
    }

    var keyOnly: Bool { sessionEndAt == .distantFuture }
    var hasKey: Bool { nfcTagId != nil || qrSecret != nil }

    init() {
        sessionEndAt = defaults.object(forKey: "sessionEndAt") as? Date
        nfcTagId = defaults.string(forKey: "nfcTagId")
        qrSecret = defaults.string(forKey: "qrSecret")
        emergencyRequestedAt = defaults.object(forKey: "emergencyRequestedAt") as? Date
        restoreSelection()
        applyShield()
    }

    // MARK: session

    func startSession(duration: TimeInterval?) {
        emergencyRequestedAt = nil
        sessionEndAt = duration.map { Date().addingTimeInterval($0) } ?? .distantFuture
    }

    func endSession() {
        emergencyRequestedAt = nil
        sessionEndAt = nil
    }

    /// NFC tag id or QR payload presented; toggles the lock when it matches.
    @discardableResult
    func keyPresented(_ value: String) -> Bool {
        guard value == nfcTagId || value == qrSecret else { return false }
        if sessionActive { endSession() } else { startSession(duration: nil) }
        return true
    }

    func ensureQrSecret() -> String {
        if let existing = qrSecret { return existing }
        let secret = "atzor:" + UUID().uuidString
        qrSecret = secret
        return secret
    }

    // MARK: emergency escape

    func requestEmergency() { emergencyRequestedAt = Date() }
    func cancelEmergency() { emergencyRequestedAt = nil }

    var emergencyReady: Bool {
        guard let at = emergencyRequestedAt else { return false }
        return Date().timeIntervalSince(at) >= Self.emergencyDelay
    }

    // MARK: gentle mode
    //
    // Unlike Android (which needed a hand-rolled accessibility overlay because
    // there's no OS-level "soft block" concept), iOS already gives every shield
    // screen a secondary button. AtzorShieldAction handles its tap directly —
    // no code here needed, and it works even if this app isn't running. See
    // AtzorShieldAction/ShieldActionExtension.swift and
    // AtzorActivityMonitor/ActivityMonitorExtension.swift.

    // MARK: shield plumbing

    /// The actual blocking: ManagedSettings applies the shield instantly,
    /// system-wide, no accessibility tricks needed. This is Apple's sanctioned path.
    private func applyShield() {
        if sessionActive {
            store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
            store.shield.applicationCategories = selection.categoryTokens.isEmpty
                ? nil
                : .specific(selection.categoryTokens)
        } else {
            store.shield.applications = nil
            store.shield.applicationCategories = nil
        }
    }

    private func persistSelection() {
        if let data = try? JSONEncoder().encode(selection) {
            defaults.set(data, forKey: "selection")
        }
        applyShield()
    }

    private func restoreSelection() {
        guard let data = defaults.data(forKey: "selection"),
              let restored = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
        else { return }
        selection = restored
    }
}
