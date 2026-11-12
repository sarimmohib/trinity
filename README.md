# Trinity

One Android app in place of the stock **Phone**, **Messages** and **Contacts** apps,
with name-based and keyword-based blocking for messages and calls.

```
Calls  ·  Messages  ·  Contacts        one surface, three tabs
```

## The look — a hard rule

**Paper & ink. Strictly achromatic.** No purple, no neon, no deep blue, no red,
green or orange, and **no gradients anywhere**. Hierarchy comes from weight, scale
and visible hairline rules — never from hue.

| Token | Value | Used for |
| --- | --- | --- |
| `BONE` | `#F1EDE4` | app background |
| `PAPER` | `#FAF8F2` | raised surface (headers, tab bar, fields) |
| `INK` | `#17160F` | primary text, filled controls |
| `MUT` | `#6D6A5C` | secondary text |
| `FAINT` | `#9C9788` | tertiary text / labels |
| `LINE` | `#C6BEA8` | **row borders** — dark enough that adjacent contacts never blur together |
| `LINE_SOFT` | `#DCD6C8` | section rules only |
| `WASH` | `#E7E2D5` | inert fill (avatars, flagged cards) |

The same eight values live in `app/src/main/java/com/sarim/trinity/ui/theme/Theme.kt`
(`Palette`) and in `design/build-design.js`. If you add a colour, you are changing
the brief — don't.

## Blocking semantics

Two independent rule lists, matched on every refresh:

* **Name rules** and **number rules** hide a whole conversation and (optionally)
  silence its calls. Names/numbers win over keywords.
* **Keyword rules** hide an individual message. In the thread it stays visible as a
  flagged card with **SHOW ONCE** / **ALLOW SENDER**; in the list it shows as `KEYWORD`.

When Trinity holds the SMS role, a text that matches a rule is filed in the
quarantine store (`data/QuarantineStore.kt`) instead of the system inbox, so the
**Held** screen is a real audit trail, not a filter. `ALLOW SENDER` drops the rule
that caught the message.

## Layout

```
design/
  build-design.js     source of truth for the canvas — edit this, never the .op
  trinity.op          generated OpenPencil 0.8.5 document (9 frames)
  shots/*.png         one exported PNG per frame, all reviewed
app/src/main/java/com/sarim/trinity/
  MainActivity.kt     route state, permission gate, tab bar
  data/               BlockStore + BlockMatcher, repositories, ContactWriter, QuarantineStore
  sms/Sms.kt          SMS_DELIVER / WAP_PUSH receivers, sender, contact-name cache
  ui/theme/Theme.kt   Palette + TrinityTheme (Material3 stable, not Expressive)
  ui/components/      MonoLabel, Hairline, Avatar, ScreenHeader, Pill, RuleChip, IconAction
  ui/screens/         Messages, Thread, Calls, Contacts, Person, Blocking, Held, Dialpad,
                      InCall, NewContact, Settings
```

Kotlin + Jetpack Compose, single `:app` module, minSdk 26 / compile+target 34.
No Room, no DataStore, no navigation library: `SharedPreferences` + `org.json` and a
hand-rolled `Route` sealed interface.

## Screens (design frames ↔ app)

| Frame | App screen |
| --- | --- |
| 01 Messages | `MessagesScreen` |
| 02 Thread | `ThreadScreen` |
| 03 Calls | `CallsScreen` |
| 04 Contacts | `ContactsScreen` (+ `PersonScreen` detail) |
| 05 Blocking | `BlockingScreen` |
| 06 Dial pad | `DialpadScreen` |
| 07 Settings | `SettingsScreen` |
| 08 In call | `InCallScreen` |
| 09 New contact | `NewContactScreen` (also "save this number" from the dial pad) |

## Build the APK

JDK 21 is required (system Java 25 will not work with Gradle 8.7); `gradle.properties`
already pins it. `local.properties` points at the SDK.

```bash
# from the repo root
"$HOME/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle" --no-daemon assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

## Rebuild and view the design

`op open_document` does **not** reload a changed file. The canvas must be relaunched:

```bash
node design/build-design.js                     # rewrites design/trinity.op
taskkill //F //IM openpencil-desktop.exe
"/c/Users/Sarim/Programs/OpenPencil/openpencil-desktop.exe" "C:/Users/Sarim/repos/trinity/design/trinity.op" &
"/c/Users/Sarim/.openpencil/bin/op.exe" export-frames --output-dir "C:/Users/Sarim/repos/trinity/design/shots" --format png
```

Generator gotchas: every node needs an `id`; alignment values are
`start|center|end|space_between|space_around`; padding on a bare text node renders
shifted — wrap the label in a row or frame instead.

## Status

Verified: `assembleDebug` succeeds and the 9 design frames export and render correctly.

Not verified: nothing has run on a device or emulator — no system image is installed
and no phone was connected, so the app is **compile-checked only**. In particular the
SMS role handoff, the `SMS_DELIVER` quarantine path, and `ContactWriter`'s provider
insert are written but untested. `InCallScreen` is Trinity's own in-call surface for
calls Trinity placed; audio still belongs to the system telephony stack, and Trinity
does not hold `ROLE_DIALER`/`InCallService`.

## Licence

MIT. See [LICENSE](LICENSE).
