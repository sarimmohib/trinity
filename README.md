# Trinity

One Android app in place of the stock **Phone**, **Messages** and **Contacts** apps,
with name-based and keyword-based blocking for messages and calls.

```
Calls  ·  Messages  ·  Contacts        one surface, three tabs
```

> **Work in progress.** Trinity is being used day to day by its author on a Xiaomi
> `spes` running Android 16, and is not released. Expect rough edges.

## The look — translucent Expressive glass

Every tappable thing is a rounded, faintly translucent block with a colour of its
own that lets the page show through it. Nothing sits on a solid slab, and **there
are no gradients anywhere** — the frosted quality comes from texture, not gloss:
a 1px diagonal weave every 7dp under a white hairline edge.

The recipe lives in one place, `Modifier.glassBlock(tint, radius, strength)`
(`ui/components/Common.kt`):

| Part | Value |
| --- | --- |
| tint alpha | `0.26` light / `0.28` dark, scaled by `strength`, capped at `0.9` |
| edge | `0.8dp` white, `60%` light / `12%` dark |
| texture | diagonal white lines, `34%` light / `5%` dark |
| radius | `18dp`–`26dp` (`20dp` default) |

Colour is fixed, not taken from the wallpaper: the Expressive scheme in
`ui/theme/Theme.kt` uses three roles only, so a wallpaper can never drag purple or
indigo into the app.

| Role | Light | Dark | Used for |
| --- | --- | --- | --- |
| primary (green) | `#2E7A61` | `#8FD5BC` | active tab, primary action, selection bar |
| secondary (blue) | `#2B5D8C` | `#A9C8EA` | search fields, secondary actions |
| tertiary (teal) | `#1F7A8C` | `#8FD1DE` | WhatsApp actions |
| background | `#F3F8F5` | `#0E1412` | page behind the glass |
| surface | `#FAFDFB` | `#111816` | sheets and composers |
| on surface | `#171D1A` | `#DDE5E0` | text |

Each person also gets a tint cycled from the container roles, so a list reads as a
stack of differently coloured pieces rather than one repeated block.

Brightness is the only setting: **Follow the system**, Light or Dark, and it only
picks the light or dark Expressive scheme. It never falls back to the old Material
look. If you add a colour, add a role; if you add a gradient, you are changing the
brief.

## Selection

Long-press any row in **Calls**, **Messages**, **Contacts** or a chat to start
picking; tapping then adds or removes rows instead of opening them. The tab's
bottom action is replaced by a selection bar with the count and what can be done:

| Where | Actions |
| --- | --- |
| Conversations | Read, Mute/Unmute, Delete, All (+ Actions opens the single-row sheet) |
| Calls | Copy numbers, Block, Delete, All (+ Info) |
| Contacts | Copy numbers, Block, Delete, All (+ Open) |
| Chat messages | Copy, Forward, Delete, All (+ Actions for details) |

`CANCEL` or the back gesture leaves the selection; destructive actions confirm
first and name how many items they will remove.

## The contact screen

Beyond call and text:

* **Photo** — tap the avatar to pick an image. It is cropped square, scaled and
  written into the contact's own photo row in the phone's provider. Nothing is
  copied into Trinity. `Remove photo` puts the contact back.
* **Availability** — WhatsApp publishes its identities into the contacts provider
  (raw contacts under the `com.whatsapp` account, JID in `SYNC1`), so the screen can
  say **AVAILABLE** for a person who is actually on WhatsApp instead of guessing
  from the installed app. Numbers on WhatsApp are marked `WA`.
* **WhatsApp** — chat and WhatsApp voice actions, plus the WhatsApp mark on every
  call-log row.
* **Call history for this contact** — last calls with direction, duration and
  timestamp, each opening the call-log detail screen.
* **Google contacts sync** — read from the provider's accounts and sync flags
  (`ON`, `OFF`, `AUTO-SYNC OFF`, `NO GOOGLE ACCOUNT`); tapping opens the system sync
  settings.

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
app/src/main/java/com/sarim/trinity/
  MainActivity.kt     route state, permission gate, tab bar
  call/               InCallService, in-call screen, call notification + ring
  data/               BlockStore + BlockMatcher, repositories, ContactWriter,
                      ContactChannels (WhatsApp + sync), ContactPhotos,
                      QuarantineStore, ThreadPrefs
  sms/                SMS_DELIVER / WAP_PUSH receivers, sender, notifier
  ui/theme/Theme.kt   one Expressive scheme (light/dark) + Palette roles
  ui/components/      glassBlock, PersonAvatar, Selection (long-press picking),
                      MonoLabel, Hairline, InkSheet, BottomAction, ScreenHeader
  ui/screens/         Messages, Thread, Calls, Contacts, Person, Blocking, Held,
                      Dialpad, NewContact, Settings
```

Kotlin + Jetpack Compose, single `:app` module, minSdk 26 / compile+target 34.
No Room, no DataStore, no navigation library: `SharedPreferences` + `org.json` and a
hand-rolled `Route` sealed interface. Photos are decoded with `BitmapFactory`; there
is no image-loading dependency.

## Screens

| Screen | File |
| --- | --- |
| Calls | `CallsScreen` |
| Messages | `MessagesScreen` |
| Conversation | `ThreadScreen` |
| Contacts | `ContactsScreen` |
| Contact detail | `PersonScreen` |
| Blocking / Held | `BlockingScreen`, `HeldScreen` |
| Dial pad | `DialpadScreen` |
| In call | `InCallActivity` |
| New contact / message | `NewContactScreen`, `NewMessageScreen` |
| Settings | `SettingsScreen` |

## Build the APK

JDK 21 is required (system Java 25 will not work with Gradle 8.7); `gradle.properties`
already pins it. `local.properties` points at the SDK. There is no Gradle wrapper
checked in yet, so point at a Gradle 8.7 install:

```bash
# from the repo root
"$HOME/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle" --no-daemon assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Design canvas is out of date

`design/build-design.js`, `design/trinity.op` and `design/shots/*.png` are the
original paper-and-ink mockups, kept for history. The app has moved to the glass
Expressive look described above, so those frames no longer match. The source of
truth for the look is `ui/theme/Theme.kt` and `glassBlock` in `ui/components/Common.kt`.

## Status

Running on the author's Xiaomi `spes` (Android 16). Exercised on the device so far:
the three tabs, contact photos (attach, display, remove), WhatsApp availability and
chat hand-off, the Google sync indicator, and the long-press selection actions.

Implemented and building, but not yet through a full pass on this phone: the
incoming-call notification and live in-call card (counter, mute, speaker, end call),
the SMS role handoff, and the `SMS_DELIVER` quarantine path. When WhatsApp's contact
sync is off the availability row honestly reads `NO RECORD` rather than assuming the
number is not on WhatsApp.

## Licence

MIT. See [LICENSE](LICENSE).
