import ManagedSettings
import DeviceActivity
import Foundation

/// The iOS-native equivalent of Android's "gentle mode": Apple already gives
/// every shield screen a primary AND a secondary button for free. We use the
/// secondary button as the "enter anyway, for 5 minutes" pass — no accessibility
/// overlay hacks needed, this is exactly what the API is for.
///
/// Target template in Xcode: "Shield Action Extension". Needs the Family
/// Controls capability, same as the main app and AtzorShield.
class ShieldActionExtension: ShieldActionDelegate {

    private let store = ManagedSettingsStore()
    private let center = DeviceActivityCenter()
    private let sharedDefaults = UserDefaults(suiteName: AtzorShared.appGroupID)

    override func handle(
        action: ShieldAction,
        for application: ApplicationToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        switch action {
        case .primaryButtonPressed:
            // "חזרה לחיים" — just close the shield, same as Android's hard block.
            completionHandler(.close)
        case .secondaryButtonPressed:
            grantGentlePass(for: application)
            completionHandler(.close)
        @unknown default:
            completionHandler(.close)
        }
    }

    override func handle(
        action: ShieldAction,
        for webDomain: WebDomainToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        completionHandler(.close)
    }

    override func handle(
        action: ShieldAction,
        for category: ActivityCategoryToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        completionHandler(.close)
    }

    /// Removes just this one app from the shield for 5 minutes, then a
    /// DeviceActivityMonitor event (AtzorActivityMonitor) puts it back —
    /// the extension process itself is too short-lived to hold a timer.
    private func grantGentlePass(for application: ApplicationToken) {
        var current = store.shield.applications ?? []
        current.remove(application)
        store.shield.applications = current

        // Remember which token to re-shield; the monitor extension reads this.
        if let data = try? JSONEncoder().encode(application) {
            sharedDefaults?.set(data, forKey: "gentlePassToken")
        }

        let event = DeviceActivityEvent(
            applications: [application],
            threshold: DateComponents(minute: 1)
        )
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59),
            repeats: false
        )
        let activityName = DeviceActivityName("atzor.gentlePass")
        let eventName = DeviceActivityEvent.Name("gentlePassExpired")

        try? center.stopMonitoring([activityName])
        try? center.startMonitoring(
            activityName,
            during: schedule,
            events: [eventName: event]
        )
    }
}
