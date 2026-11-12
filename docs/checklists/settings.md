# SETTINGS — feature checklist

Scope: the Settings screen and everything it controls. Every row must either do something
or say why it cannot. No dead rows, no fake values.

Priority: **P0** = broken without it. **P1** = expected. **P2** = polish.

Status: `[x]` pass on the device · `[~]` partial · `[!]` absent/failing.

## Roles and defaults (the app's foundation)

- [x] **SET-01** · P0 — Default-SMS row reports the real role state and requests it.
      Verified on device: `Default SMS app · BLOCKING CANNOT STOP DELIVERY · NOT SET`, tapping
      calls `RoleManager.createRequestRoleIntent(ROLE_SMS)`.
- [x] **SET-02** · P0 — The row explains what blocking cannot do without the role.
      `BLOCKING CANNOT STOP DELIVERY`.
- [x] **SET-03** · P0 — Default-dialer row reports the real state and requests `ROLE_DIALER`.
      Added this pass: `Default phone app · CALLS GO THROUGH THE STOCK APP · NOT SET`,
      requesting `ROLE_DIALER`.
- [!] **SET-04** · P1 — Contacts/home role row. Trinity declares a HOME intent filter but
      offers no row to take that role.
- [x] **SET-05** · P1 — Permission overview with a link to app settings.
      Verified: `Permissions · ALL GRANTED · 8/8`, tapping opens
      `ACTION_APPLICATION_DETAILS_SETTINGS`.
- [x] **SET-06** · P1 — Send-from SIM selection showing the real SIM name.
      Verified: `Send from · NO SIMS DETECTED ON THIS DEVICE · SYSTEM DEFAULT`, with a sheet
      listing the system default plus every active subscription.

## Blocking

- [x] **SET-07** · P0 — Links to the rules editor with live counts.
      Verified: `Name & number rules · 1`, `Keyword rules · 1`.
- [~] **SET-08** · P0 — Hide-matched-calls toggle that changes the log.
      The toggle writes `hideMatchedCalls` and the call list filters on it; the filter was not
      re-observed this pass.
- [x] **SET-09** · P0 — Held-messages row with quarantine and inbox counts, opening Held.
      Verified: `Held messages · QUARANTINE AND INBOX MATCHES · 0`.
- [x] **SET-10** · P0 — Allowed senders visible and revocable.
      Verified count row (`Allowed senders · 1`); revocation lives in the rules screen.
- [~] **SET-11** · P1 — Keyword rules listed with match counts. Listed, but each chip still
      shows `0`/`×` rather than a real count.
- [!] **SET-12** · P2 — Export/import rules as text. Absent.
- [!] **SET-13** · P2 — Per-conversation mute list. Mute exists per conversation; no list here.

## Notifications, sound, feedback

- [~] **SET-14** · P1 — Notification toggle for messages, missed calls and held texts.
      Trinity has no in-app toggle; the row opens Android's own notification-settings page for
      the app, where the per-channel switches live (`messages`, `held`).
- [x] **SET-15** · P1 — Sound and vibration rows that open the system channel.
      Delegated through the same row rather than faked locally.
- [!] **SET-16** · P1 — Keypad tone and haptic toggles. Absent; haptics are always on.
- [!] **SET-17** · P2 — Preview option (snippet vs sender). Absent.

## Appearance and accessibility

- [~] **SET-18** · P1 — Theme row. Not a control: a static row states the theme is fixed
      paper-and-ink. Dark mode does not exist anywhere in the app.
- [~] **SET-19** · P1 — Text size row that changes the app's type scale. Delegated to the
      system font scale instead (the app follows it).
- [x] **SET-20** · P1 — Link to system accessibility settings.
      Verified row `TalkBack · SYSTEM` and `Text size · SYSTEM`, both opening
      `ACTION_ACCESSIBILITY_SETTINGS`.
- [!] **SET-21** · P2 — Layout density. Absent.
- [x] **SET-22** · P1 — No appearance option introduces hue, gradient or elevation. There is
      no hue, gradient or shadow in the codebase at all.

## Data and privacy

- [x] **SET-23** · P1 — Storage overview: conversations, calls, contacts, held texts.
      Verified as a static row with live numbers.
- [x] **SET-24** · P1 — Clear call history / held texts / rules, each confirmed separately.
      Each opens its own sheet naming the count (`Delete 5 call entries · CANNOT BE UNDONE`),
      and each is disabled when there is nothing to clear.
- [!] **SET-25** · P2 — Backup/restore of Trinity's own data. Absent.
- [x] **SET-26** · P1 — A plain statement that rules and held texts never leave the device.
      `Rules, held texts and drafts never leave this device. Trinity has no server and no account.`
- [~] **SET-27** · P1 — Reset-to-defaults behind a confirmation naming what is lost.
      `Clear all rules` covers names, numbers, keywords and allowances only; there is no full
      reset of every stored setting.
- [x] **SET-28** · P1 — Version, build, and what Trinity replaces.
      Verified: `TRINITY 0.1.0`, `Version · 0.1.0 · DEBUG BUILD`, `Replaces · PHONE · MESSAGES · CONTACTS`.
- [!] **SET-29** · P2 — Open-source licences screen. Absent.
- [!] **SET-30** · P2 — Link to source / issue tracker. Absent.

## Behaviour of the screen itself

- [x] **SET-31** · P0 — Values are live, not snapshots.
      Verified in one dump: `8/8` permissions, `NOT SET` roles, `NO SIMS DETECTED`, live rule
      counts. The previous hardcoded `SIM 1` is gone.
- [x] **SET-32** · P0 — Toggles persist across restart. `hideMatchedCalls` is written to
      `trinity.rules` and re-read at startup.
- [x] **SET-33** · P0 — No row exists that does nothing when tapped. Every clickable row
      navigates, requests a role, or opens a system page; facts are rendered as static rows
      with no chevron.
- [x] **SET-34** · P1 — Grouped sections with hairline separators and small-caps labels.
      Verified: `ROLES / BLOCKING / MESSAGING / APPEARANCE · FIXED / DATA / ABOUT`.
- [x] **SET-35** · P1 — Destructive actions confirm and name the consequence.
- [~] **SET-36** · P1 — Back returns to the same scroll position. Back works; the scroll
      position is rebuilt because `rememberScrollState` is local to the screen.
- [~] **SET-37** · P1 — Every row is a 48 dp target with a TalkBack label including its value.
      Rows are 62 dp; the label and value are separate nodes rather than one announcement.
- [!] **SET-38** · P2 — Settings search. Absent.
- [!] **SET-39** · P2 — Deep links ("why is this blocked?") landing on a row. Absent.

## Score

39 items: **20 pass**, **8 partial**, **11 absent**.

## What this pass changed

- Removed four rows that lied: `Send from · SIM 1` (fake, tap did nothing),
  `Paper & ink` and `Row borders` (chevrons with no action) and `Version` (tap did nothing).
- Added: `Default phone app` with a real `ROLE_DIALER` request, `Permissions · n/8` opening
  app settings, `Send from` with a real SIM sheet, `Allowed senders` count, `Notifications`
  opening the system channel page, `Text size`/`TalkBack` opening accessibility, a `Storage`
  overview, and three confirmed clear actions (calls, held texts, rules).
- Facts are now rendered as static rows — no chevron, nothing to tap, nothing pretending.
