import DeviceActivity
import ManagedSettings
import Foundation

/// Closes the loop on the gentle-mode pass: 5 minutes after
/// ShieldActionExtension lets an app through, this fires and re-adds it to
/// the shield — same promise as Android's "then the pause returns".
///
/// Target template in Xcode: "Device Activity Monitor Extension". Needs the
/// Family Controls capability. Must share an App Group with the main app and
/// AtzorShieldAction (see AtzorShared.swift) so all three agree on the token.
class ActivityMonitorExtension: DeviceActivityMonitor {

    private let store = ManagedSettingsStore()
    private let sharedDefaults = UserDefaults(suiteName: AtzorShared.appGroupID)

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        guard event == DeviceActivityEvent.Name("gentlePassExpired"),
              let data = sharedDefaults?.data(forKey: "gentlePassToken"),
              let token = try? JSONDecoder().decode(ApplicationToken.self, from: data)
        else { return }

        var current = store.shield.applications ?? []
        current.insert(token)
        store.shield.applications = current
        sharedDefaults?.removeObject(forKey: "gentlePassToken")
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        // The schedule window closed without the threshold firing (e.g. the
        // full lock ended first) — nothing to restore beyond the normal
        // shield state AtzorModel already manages.
    }
}
