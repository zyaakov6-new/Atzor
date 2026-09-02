# עצור — the app

Native apps that actually block distracting apps, unlocked by a physical key
(NFC tag or a printed QR code). Design matches the landing page: warm paper,
ink, terracotta coral, Miriam Libre + Assistant.

```
android/   Kotlin + Jetpack Compose — BUILDS TODAY, APK ready
ios/       SwiftUI + Screen Time API — complete source, needs Xcode + Apple entitlement
```

## Android — install right now

**APK:** `android/app/build/outputs/apk/debug/app-debug.apk`

1. Copy the APK to the phone (AirDrop-equivalent: Quick Share, or `adb install`,
   or just WhatsApp-it to yourself).
2. Open it on the phone → allow "install unknown apps" for the file manager.
3. First launch walks you through enabling the **עצור accessibility service**
   (Settings → Accessibility → עצור · חסימת אפליקציות → on).
   That service is what detects a blocked app opening; it reads no content.

### Using it
- **מה חוסמים** — pick the apps to block.
- Choose a duration chip (30 min / 1h / 2h / **עד המפתח** = until the key).
- Tap the big **עצור** circle. Opening a blocked app now lands on the dark
  נעול screen and bounces home.
- **מפתחות** — register any NFC tag/sticker (its hardware ID becomes the key)
  and/or generate a QR code to print. Tap the tag or scan the code to lock/unlock.
- Emergency: from the lock screen, request emergency unlock → 15-minute
  countdown → confirm. (Same policy the landing page promises.)

### Rebuild from source
```bash
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug
```
Toolchain used: OpenJDK 21 (Homebrew), Android SDK 34 at `~/Library/Android/sdk`,
Gradle 8.7 wrapper, AGP 8.4.1, Kotlin 1.9.24.

### Honest limitations (debug build)
- A determined user can uninstall עצור or toggle the accessibility service off
  mid-session. Real commitment-mode hardening (device admin, uninstall
  protection) is a later milestone.
- The debug APK is unsigned-for-store; for Play Store you'll need a release
  keystore and — importantly — a strong justification for the accessibility
  service in the Play Console review.

## iOS — read `ios/SETUP.md`

Complete SwiftUI source using Apple's sanctioned blocking path
(`FamilyControls` + `ManagedSettingsStore` shields — no accessibility hacks
needed there). Blocked-app screen comes from the AtzorShield extension.
Needs: Xcode, paid Apple Developer account, Family Controls capability
(runs on your own device immediately; App Store distribution requires
Apple's entitlement approval, which takes weeks).

## Shared design language
| Token | Value |
|---|---|
| paper | `#FFF7E9` |
| paper-white | `#FFFDF8` |
| ink | `#25343A` |
| ink-soft | `#607078` |
| coral | `#BA654F` |
| coral-deep | `#984D3B` |
| leaf-deep | `#63806D` |
| sun | `#F2CF7B` |

Lock screens on both platforms are dark ink with the coral pulse — the same
"נעול" moment as the landing page phone mockup.

## Signing key (read this before you touch a release)

Release signing reads `android/keystore/keystore.properties` and the keystore
next to it. **Both are excluded from git on purpose**, because that properties
file holds the signing passwords in plain text.

They are also irreplaceable. Lose them and Atzor can never be updated on Play
under `com.atzor.app` again, so keep a copy somewhere that is not this machine
(password manager, or a private cloud folder). Restoring is just putting both
files back at `android/keystore/`.

`app/google-services.json` *is* tracked: it ships inside the APK anyway, and
without it the Firebase Gradle plugins silently do not apply, so Crashlytics
and Analytics quietly stop working.
