# עצור · iOS — setup

The Swift sources here are complete but **cannot compile on this machine**
(no Xcode installed) and **cannot run** until Apple grants the Family
Controls entitlement. This is the honest path:

## 1. Install Xcode
App Store → Xcode (15+). Then: `sudo xcode-select -s /Applications/Xcode.app`

## 2. Create the project
1. Xcode → New Project → iOS App → name **Atzor**, interface SwiftUI, language Swift.
2. Drag the `Atzor/` folder (App, Keys, UI groups) into the project.
3. File → New → Target → **Shield Configuration Extension** → name **AtzorShield**.
   Replace its generated `ShieldConfigurationExtension.swift` with the one here.
4. File → New → Target → **Shield Action Extension** → name **AtzorShieldAction**.
   Replace its generated file with `AtzorShieldAction/ShieldActionExtension.swift`.
   This is what makes the shield's secondary button work as a gentle-mode pass.
5. File → New → Target → **Device Activity Monitor Extension** → name **AtzorActivityMonitor**.
   Replace its generated file with `AtzorActivityMonitor/ActivityMonitorExtension.swift`.
   This is what re-shields the app 5 minutes after a gentle pass — the
   extension process itself is too short-lived to hold a timer, so Apple's
   DeviceActivityMonitor schedule does the waiting instead.

## 3. Capabilities (all four targets: Atzor, AtzorShield, AtzorShieldAction, AtzorActivityMonitor)
- **Family Controls** (Signing & Capabilities → + Capability)
- **App Groups**: create/select `group.app.atzor.shared` on all four targets —
  this is how the shield action extension and the activity monitor extension
  agree on which app token to re-shield (see `App/AtzorShared.swift`; change
  the group ID there if you use a different one).
- App target (Atzor) also needs: **Near Field Communication Tag Reading**

## 4. Info.plist (app target)
| Key | Value |
|---|---|
| `NSCameraUsageDescription` | סריקת קוד QR שמשמש כמפתח לנעילה |
| `NFCReaderUsageDescription` | קריאת תג NFC שמשמש כמפתח לנעילה |
| `com.apple.developer.nfc.readersession.formats` | `TAG` |

## 5. Apple approvals
- Paid Apple Developer account ($99/yr) is required for Family Controls.
- **Development on your own device works as soon as the capability is added.**
- Distribution (TestFlight/App Store) requires requesting the entitlement:
  https://developer.apple.com/contact/request/family-controls-distribution

## What each file does
- `App/AtzorApp.swift` — entry point, requests Screen Time authorization.
- `App/AtzorModel.swift` — state + the actual blocking via `ManagedSettingsStore` shields.
- `App/AtzorShared.swift` — the App Group ID shared by all four targets.
- `Keys/NfcKeyReader.swift` — reads a tag UID with CoreNFC; the UID is the key.
- `Keys/QrKeyScanner.swift` — AVFoundation QR scanner sheet.
- `UI/RootView.swift` — the whole UI, styled after the landing page (paper/ink/coral).
- `../AtzorShield/ShieldConfigurationExtension.swift` — the "נעול" screen iOS shows over blocked apps, with a primary ("חזרה לחיים") and secondary ("להיכנס בכל זאת, ל-5 דקות") button.
- `../AtzorShieldAction/ShieldActionExtension.swift` — handles those two button taps. Primary closes the shield. Secondary removes just that one app from the shield and arms a 5-minute DeviceActivity event.
- `../AtzorActivityMonitor/ActivityMonitorExtension.swift` — fires when that 5-minute event threshold is reached and puts the app back behind the shield.

Unlike Android, iOS blocking does not need an accessibility service:
`ManagedSettingsStore.shield` blocks selected apps system-wide, and the
15-minute emergency delay is enforced by the model before it clears the shield.

## Gentle mode: how it differs from Android, on purpose
Android had to hand-roll a breathing-pause overlay because there's no OS
concept of a "soft block." iOS already gives every shield screen a secondary
button, so AtzorShieldAction uses it directly — no overlay, no accessibility
service, and it works even if the app isn't running. Right now the secondary
button always appears (shipped behavior). Android's per-app and global
gentle-mode *toggles* (choosing whether an app even gets a soft option) aren't
ported yet — the natural next step is having `ShieldConfigurationExtension`
read the same App Group defaults AtzorModel writes to, and only include
`secondaryButtonLabel` for apps the user has marked "gentle." Not done here
since it's unverified without a working Xcode build; flagged so it's not lost.
