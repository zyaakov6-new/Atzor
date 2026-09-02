# Play compliance pack — עצור (Atzor)

Materials for Google Play review: Accessibility Service declaration, Data Safety,
and Play Integrity posture.

## 1. Accessibility Service declaration (Play Console form)

**Service label:** עצור · חסימת אפליקציות  
**Permission:** `BIND_ACCESSIBILITY_SERVICE`  
**Config:** `res/xml/accessibility_service_config.xml`

### Why the service is required
עצור blocks distracting apps while a user-initiated “vault” lock is active.
Android does not expose a public API to detect when a third-party app enters the
foreground without Accessibility (or privileged Device Owner APIs). The service
is the only sanctioned way for a consumer focus app to:

1. Observe which package is currently in the foreground.
2. Bounce the user home and show the lock / gentle-pause overlay.

### What we access
| Capability | Used? | Purpose |
|------------|-------|---------|
| `canRetrieveWindowContent` | **No** | Explicitly off — we never read screen text |
| Package / window events | Yes | Foreground package name only |
| Gestures / global actions | Yes | `GLOBAL_ACTION_HOME` to leave blocked apps |
| Keystrokes / clipboard | **No** | Not requested |
| Screenshots | **No** | Not requested |

### User control
- Service is off until the user enables it in system Accessibility settings.
- Onboarding explains the need in plain Hebrew before deep-linking to Settings.
- Blocking only runs while a lock session / schedule / Shabbat window is active.

### Declaration text (copy into Play Console)
> Atzor (עצור) is a digital-wellbeing app that locks user-selected apps behind a
> physical-key vault (NFC/QR). The Accessibility Service is used solely to detect
> when a blocked app comes to the foreground during an active lock, so the app can
> return the user to the home screen and show a lock overlay. We do not read
> on-screen content, keystrokes, passwords, or messages. No accessibility data
> leaves the device. The service is optional until the user enables it and is
> inactive when no lock is running.

## 2. Data Safety summary
- **No advertising ID** (permissions stripped in the manifest).
- **Firebase Analytics:** anonymous opens / retention only; no blocked-app list.
- **Crashlytics:** stack traces only (when `google-services.json` is present).
- **Local DataStore:** block list, schedules, keys, streaks — stay on device.
- **No account system** in current builds.

## 3. Play Integrity
Dependency: `com.google.android.play:integrity:1.4.0`

Recommended production use (next iteration if abuse appears):
1. Request an integrity token on sensitive actions (start key-only lock, emergency unlock).
2. Verify server-side with Google’s API (needs a small backend).
3. On device-only builds, optional client check can gate features with a soft warn.

Current ship: library linked for future use; no hard block if Integrity fails
(avoids locking out legitimate users during rollout).

## 4. Sensitive permissions checklist
| Permission | Reason |
|------------|--------|
| Accessibility | Foreground package for blocking |
| NFC | Physical vault key |
| Camera | QR key scan |
| Bluetooth Connect | Optional auto-lock on *user-selected* device only |
| Notification listener | Hold notifications from blocked apps while locked |
| Battery exemption | Keep blocker alive mid-session |
| Vibrate | Optional seal haptic |

## 5. Versioning note
Always upload a single AAB with a versionCode higher than any previous release.
Do not keep older APKs/AABs in the same Play release draft.
