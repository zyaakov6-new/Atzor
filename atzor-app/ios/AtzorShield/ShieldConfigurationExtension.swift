import ManagedSettings
import ManagedSettingsUI
import UIKit

/// The screen iOS shows when a shielded app is opened.
/// This is a separate app-extension target ("Shield Configuration Extension"
/// template in Xcode) and needs the Family Controls capability too.
class ShieldConfigurationExtension: ShieldConfigurationDataSource {

    private var atzorShield: ShieldConfiguration {
        ShieldConfiguration(
            backgroundBlurStyle: .systemUltraThinMaterialDark,
            backgroundColor: UIColor(red: 0.145, green: 0.204, blue: 0.227, alpha: 1),   // ink
            icon: UIImage(systemName: "lock.fill"),
            title: .init(text: "נעול", color: UIColor(red: 0.949, green: 0.933, blue: 0.894, alpha: 1)),
            subtitle: .init(text: "האפליקציה הזאת מחכה. שתחכה.", color: UIColor(red: 0.949, green: 0.933, blue: 0.894, alpha: 0.7)),
            primaryButtonLabel: .init(text: "חזרה לחיים", color: UIColor(red: 0.145, green: 0.204, blue: 0.227, alpha: 1)),
            primaryButtonBackgroundColor: UIColor(red: 0.949, green: 0.933, blue: 0.894, alpha: 1),
            // The gentle-mode affordance: AtzorShieldAction's secondary-button
            // handler grants a 5-minute pass instead of closing outright.
            secondaryButtonLabel: .init(text: "להיכנס בכל זאת, ל-5 דקות", color: UIColor(red: 0.949, green: 0.933, blue: 0.894, alpha: 0.75))
        )
    }

    override func configuration(shielding application: Application) -> ShieldConfiguration { atzorShield }
    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration { atzorShield }
    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration { atzorShield }
}
