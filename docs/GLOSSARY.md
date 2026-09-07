# Glossary

Vocabulary used across this project — Android platform terms first, then project-specific names,
then the MIUI-specific ones that cause most of the confusion.

---

## Android platform

**AccessibilityService**
A system-bound service that receives UI events from other apps and can act on their views. Requires
explicit user consent in Settings. It is how this app reads and writes WhatsApp's message box without
being a keyboard. Because it is system-bound, it needs no foreground service to stay alive.

**AccessibilityNodeInfo**
A snapshot handle to one view in another app's hierarchy. Goes stale quickly — the underlying view
may be recycled or re-laid-out. Never hold one across an async boundary; re-resolve it. `recycle()`
is deprecated since API 33 and is a no-op, since the object pooling it managed was removed.

**`ACTION_SET_TEXT`**
The accessibility action that replaces a text field's contents. Takes the new text in a bundle under
`ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`. Only usable if it appears in the node's `actionList`.

**`ACTION_SET_SELECTION`**
Sets the caret or selection range. Used here twice: to move the caret to the end after a successful
insertion, and to select-all before a clipboard paste.

**`FOCUS_INPUT`**
The focus type meaning "the view currently receiving keyboard input". `root.findFocus(FOCUS_INPUT)`
is the primary way this app finds WhatsApp's compose field.

**`rootInActiveWindow`**
The root node of the window that currently has input focus. Used to decide which app is in front.
Briefly reports *this* app's package while the overlay window is being torn down — a real source of
bugs.

**`TYPE_APPLICATION_OVERLAY`**
The window type for drawing over other apps on Android 8+. Requires `SYSTEM_ALERT_WINDOW`. Adding
such a window before the permission is granted throws `BadTokenException`.

**`SYSTEM_ALERT_WINDOW`**
The "display over other apps" permission. It is an **app-op**, not a runtime permission, so it is
granted through a Settings screen rather than a permission dialog.

**app-op (`AppOpsManager`)**
A per-app permission-like toggle the system tracks separately from manifest permissions. Readable
with `adb shell appops get <pkg> <OP>`, settable with `appops set`. Reset by `adb install -r`.

**`FLAG_NOT_FOCUSABLE`**
A window flag meaning "never take input focus". Critical here: a focusable overlay would steal focus
from WhatsApp's `EditText` and break text insertion. Kept set in every overlay state.

**`FLAG_NOT_TOUCH_MODAL`**
Lets touches outside the window's bounds reach the app behind it. Without it, a small bubble would
swallow taps across the whole screen.

**`FLAG_WATCH_OUTSIDE_TOUCH`**
Delivers a `MotionEvent.ACTION_OUTSIDE` when the user taps outside the window. How "tap outside to
dismiss" works without making the window focusable.

**`ViewTreeLifecycleOwner` / `ViewTreeViewModelStoreOwner` / `ViewTreeSavedStateRegistryOwner`**
Owners that Compose looks up from the view tree. An Activity supplies them automatically; a
`ComposeView` added directly to `WindowManager` does not, and crashes without them. Supplied here by
`OverlayLifecycleOwner`.

**`BuildConfig`**
Generated constants compiled into the APK. Used for API keys sourced from `local.properties`, and for
`BuildConfig.DEBUG` to gate logging. Values are plaintext in the APK — extractable by anyone holding it.

**DataStore**
Jetpack's replacement for `SharedPreferences`, coroutine- and Flow-based. Stores provider choice, key
overrides and bubble position. Wiped by "clear app data".

**`WRITE_SECURE_SETTINGS`**
A privileged permission that allows writing `Settings.Secure`. Not grantable by an ordinary app, but
**can** be granted to a sideloaded app once over adb. That is what lets the setup screen re-enable
its own accessibility service.

**master accessibility switch**
`Settings.Secure.ACCESSIBILITY_ENABLED`. Separate from the per-service list. Both must be right, and
flipping this alone does not rebind a service.

**bound vs. crashed service**
`adb shell dumpsys accessibility` reports `Bound services` and `Crashed services`. A service can be
listed as enabled while sitting in `Crashed services` and receiving no events. MIUI marks a service
crashed when its process is merely **killed**, not only on an exception — so a crashed entry with no
stack trace usually means process death, not a code fault.

---

## Project-specific

**bubble**
The collapsed overlay: a small circular draggable sparkle button.

**panel**
The expanded overlay: the "Rewrites" card with suggestion chips.

**chip**
One suggestion row inside the panel. Wraps to multiple lines rather than truncating.

**suppression**
The state after long-pressing the bubble: hidden until WhatsApp is re-entered. Cleared on a
non-WhatsApp → WhatsApp foreground transition.

**placeholder**
WhatsApp's hint text ("Message"), which `getText()` returns for an empty field. Detected via
`textSelectionStart == -1`, not via `isShowingHintText`, which WhatsApp never sets.

**`FocusedTextBridge`**
The two-method interface (`readFocusedText`, `writeFocusedText`) implemented by the accessibility
service and consumed by the overlay. The only channel between those layers.

**`ProviderPreferences`**
A two-method interface (`currentProvider`, `keyFor`) that decouples `ParaphraseRepository` from
Android DataStore so fallback policy can be unit-tested with fakes.

**`ParaphraseOutcome`**
What the repository returns on success: the suggestions, which provider answered, and whether that
was a fallback.

**`ParaphraseFailure`**
The typed failure reason on `ParaphraseException`. Determines whether fallback is attempted — see
TECHNICAL.md §3.

**transient failure**
`TRANSPORT`, `RATE_LIMITED`, `SERVER_ERROR`, `MODEL_UNAVAILABLE`. The only failures worth retrying on
another provider.

**self-repair**
The "Turn the service back on" button, which re-binds the accessibility service using
`WRITE_SECURE_SETTINGS`.

**stage 1 … stage 7**
The seven build stages from the original spec, used as the verification structure in TESTING.md.

---

## MIUI / vendor

**MIUI, HyperOS**
Xiaomi's Android skin. The test device runs MIUI 14 on Android 12.

**"Display pop-up windows while running in background"**
A MIUI permission separate from `SYSTEM_ALERT_WINDOW`, found under
Settings → Apps → Manage apps → *app* → Other permissions. Without it the overlay silently never
appears even though `canDrawOverlays()` returns true. **No public API can read it.**

**Autostart**
MIUI's per-app switch controlling whether an app may start in the background. Off by default; the
accessibility service is far less likely to survive without it.

**"service is malfunctioning"**
MIUI's wording when an accessibility service is toggled on but not bound. Corresponds to a non-empty
`Crashed services` in `dumpsys accessibility`.

**`miui.intent.action.APP_PERM_EDITOR`**
Xiaomi's intent, with `extra_pkgname`, that opens one app's permission page directly. Used instead of
the stock overlay screen, which on MIUI is an unfiltered list of every installed app.

**Install via USB**
A MIUI developer option required for `adb install` to succeed at all; usually demands a signed-in Mi
account. Distinct from USB debugging.

---

## Domain

**Hinglish**
Romanised Hindi mixed with English, as commonly typed in Indian chat. The app corrects it **in
place** rather than translating it — see TECHNICAL.md §4.

**time reference**
Any day, date or clock expression. Preserving these exactly is an explicit prompt rule after
measuring the model changing `kal` (tomorrow) into "today" and inventing days that were never written.
