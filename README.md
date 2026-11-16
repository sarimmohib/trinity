# Trinity

**Calls, messages and contacts in one app.**

Trinity replaces three of the stock apps on your phone — Phone, Messages and Contacts
— with a single app that does all three, in one look. It is open source, has no ads,
no trackers and no telemetry, and it does not need an account. Everything it shows
comes from your own phone: the call log, your SMS, your contacts.

Glass surfaces, rounded shapes and a calm green/blue/teal palette. One look, light
and dark, the same on every screen.

## What it does

### Calls
* Your call log, newest first, grouped by person, with direction, duration and time.
* Dial pad, with in-call controls: mute, speaker, keypad and hang up.
* Incoming calls show who is calling, even when the number arrives in a different
  format from the one saved in your contacts — `+92 300…` on the line is matched to
  `0300…` in the address book.
* When a call ends you get a short summary: whether it was answered, missed,
  declined, busy or failed, how long it lasted, and who hung up.

### Messages
* Conversations and threads, grouped by person, with quick reply from the
  notification.
* Sending, forwarding, copying and deleting; muting a conversation; starting a new
  message from a number.
* If Trinity holds the SMS role, your texts come to it directly instead of the stock
  app.

### Contacts
* Your address book, with per-person call history and message history.
* Add, edit and delete contacts; set a contact photo straight into the phone's
  contacts, without Trinity keeping a copy.
* WhatsApp availability when WhatsApp shares its contacts, plus chat and call
  shortcuts.
* A Google-sync indicator that says whether your contacts are actually syncing.

### Blocking
* **Name** and **number** rules hide a whole conversation and can silence its calls.
* **Keyword** rules flag or hide individual messages.
* **Combined rules** join conditions with AND / OR — for example a name *and* a word
  in the text.
* Blocked texts are held aside instead of deleted, so you can see what was caught and
  let a sender through later.
* Calls and texts can be blocked per surface: calls only, messages only, or both.

### Updates
Settings can check GitHub for a newer release and tell you once, with the release
notes. No background service, no account, and it can be skipped for any version.

## Install

Download the latest `app-release.apk` from
[Releases](https://github.com/sarimmohib/trinity/releases) and open it on your phone.
Android 8.0 (API 26) or newer is required.

Trinity replaces the Phone, Messages and Contacts roles, so Android will ask you to
confirm which app handles calls, SMS and contacts. Grant the permissions it asks for;
each one is used for that screen only.

## Build from source

You need JDK 17 or newer (JDK 21 works), the Android SDK with platform 34, and the
SDK's `local.properties` pointing at it:

```properties
sdk.dir=/path/to/Android/sdk
```

Then:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

A debug build installs the same way:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The release build is signed with the debug key so it installs and updates over
itself; use your own key if you publish your own builds.

## Permissions

| Permission | Why |
| --- | --- |
| `READ_CALL_LOG`, `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `CALL_PHONE` | The call log, and placing and answering calls |
| `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS` | Showing, sending and receiving texts |
| `READ_CONTACTS`, `WRITE_CONTACTS` | Your address book, and saving photos to it |
| `POST_NOTIFICATIONS` | Notifications for texts, missed calls and updates |
| `INTERNET` | Only to ask GitHub for the latest release; nothing is sent anywhere else |

## Contributing

Ideas, bug reports and pull requests are welcome —
[open an issue](https://github.com/sarimmohib/trinity/issues) to start. Please keep the
look: glass surfaces and Material 3 Expressive shapes, no gradients and no
wallpaper-derived colours.

## Licence

MIT. See [LICENSE](LICENSE).
