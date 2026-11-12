# PHONE / CALLER — feature checklist

Scope: everything the Caller tab and the call surfaces must do. A stock dialer, nothing
less: dial pad, live suggestions, recent calls, call detail, in-call screen, incoming
call, blocking integration, and the states around all of it.

Priority: **P0** = the app is broken without it. **P1** = every real dialer has it.
**P2** = polish that still counts as a feature.

Status: `[x]` pass · `[~]` partial · `[!]` fail · `[ ]` not verified (reason given).
Pass = re-checked on the device after the fix pass, with the dump/screenshot quoted.

## Dial pad

- [x] **PH-01** · P0 — 0–9, `*`, `#`, 3×4, keys ≥64 dp.
      Rows at y 1198/1358/1518/1678, each 148 px @320 dpi = **74 dp**; columns 257 px = 128 dp.
- [x] **PH-02** · P0 — Digits appear in the field in the order typed. `0300771200…` typed key by key.
- [x] **PH-03** · P0 — Backspace deletes one; long-press clears.
      Single press removed the last digit; a 900 ms press wiped the field to `—`.
- [x] **PH-04** · P0 — Long-press `0` types `+`. Field went from `—` to `+`.
- [x] **PH-05** · P0 — Feedback on every key: press state + haptic/tone.
      `combinedClickable` + `LocalHapticFeedback.performHapticFeedback(TextHandleMove)` on each key,
      plus the default achromatic ripple. (Haptic can't be felt through adb.)
- [~] **PH-06** · P1 — Letters under digits, and T9 digits search names.
      Letters present. T9 still absent: typing `555` matches nothing, because suggestions
      search numbers, not name letters.
- [x] **PH-07** · P0 — CALL places the call; empty field = inert and visibly disabled.
      Empty: outline + faint label, tap does nothing. With digits: ink pill, `sims.placeCall`.
- [x] **PH-08** · P0 — Typing a known number shows the name live.
      `0300` → … → header flips to `KNOWN NUMBER` and shows **Nadia Iqbal** above the digits.
- [x] **PH-09** · P0 — Typing digits shows matching recent calls.
      Typing `0300` listed `Nadia Iqbal · Outgoing · 20:40`, `0300 123 4001 · MISSED · 16:40`,
      `Nadia Iqbal · MISSED · Sun`, `Nadia Iqbal · Outgoing · Sun` under a RECENT CALLS label.
- [x] **PH-10** · P1 — Contact suggestions for numbers containing the typed digits.
      `CONTACTS · Nadia Iqbal · 0300 771 2008` under the recents block.
- [~] **PH-11** · P1 — Paste lands in the field, cleaned.
      PASTE reads the clipboard and strips everything but `0-9 + * #`, and says `PASTED · CLEANED TO
      DIGITS`. Not fully proven: `cmd clipboard set-text` is unavailable on WSA, so a known
      clipboard value could not be planted for a clean test.
- [x] **PH-12** · P1 — Short-code warning under 5 digits.
      Strips `SHORT CODE · MAY BE PREMIUM RATE` below the keypad (deliberately below, so the keys
      never move while typing).
- [x] **PH-13** · P1 — Digits survive leaving and returning.
      Field kept `003001234001` after Close → Calls → DIAL PAD. The digits live in `TrinityState`.
- [x] **PH-14** · P1 — Blocked numbers flagged while typing, one-tap UNBLOCK.
      `This number is blocked by a number rule` + `UNBLOCK`.
- [x] **PH-15** · P0 — SAVE CONTACT opens the new-contact screen pre-filled.
      Typed `03001234001` → New contact with `PHONE = 03001234001`.

## Recent calls

- [x] **PH-16** · P0 — Newest first, with name or formatted number.
      `Nadia Iqbal` resolved through the address book when the log has no cached name;
      numbers render as `0300 123 4001` / `0321 987 6543`.
- [x] **PH-17** · P0 — Distinct non-colour markers per type. Glyph + `Outgoing` / `Missed` / `Incoming`.
- [x] **PH-18** · P0 — Timestamp; today shows time, older shows date. `20:40`, `Yesterday`, `Sun`.
- [x] **PH-19** · P1 — Grouped by day. Headers `TODAY · 2 CALLS`, `YESTERDAY · 1 CALLS`, `8 Nov · 2 CALLS · 1 NUMBER`.
- [x] **PH-20** · P0 — Tapping a row calls back. Row tap → in-call surface for that number.
- [x] **PH-21** · P0 — Second affordance opens call detail. `INFO` chip on every row → detail screen.
- [x] **PH-22** · P1 — Repeated calls collapse with a count. Three `03007712008` entries → one row, `Missed · 2 calls`.
- [!] **PH-23** · P1 — Long-press gives call/message/add-to-contacts/block/delete.
      Long-press now **copies the number** (`COPIED · 0300 771 2008`) instead of opening a menu.
      The other actions exist inside the detail screen, not in a long-press sheet.
- [!] **PH-24** · P1 — Swipe to delete one entry with undo/confirm. Not implemented.
- [x] **PH-25** · P1 — Clear history behind a confirmation.
      `CLEAR HISTORY` → inline `Delete all 5 calls?  CANCEL / DELETE`.
- [x] **PH-26** · P0 — Header tells the truth. `5 CALLS · 2 MISSED · 1 MATCHED RULES`.
- [x] **PH-27** · P0 — Empty state explains what will appear.
      `No call history yet.` + a paragraph on ordering, day grouping, the BLOCKED tag and where rules live.
- [x] **PH-28** · P1 — Missed countable at a glance. `2 MISSED` in the header.
- [x] **PH-29** · P1 — Search field filters by name or number.
      Mono `SEARCH · name or number`; clearing it shows `No calls match "…"`.
- [x] **PH-30** · P1 — Auto-refresh on returning to the tab. `LaunchedEffect(Unit) { state.refresh() }` in `CallsScreen`.
- [x] **PH-31** · P0 — A call that just ended is at the top on return.
      The tab re-reads the log every time it is shown; nothing is cached across visits.

## Call detail

- [x] **PH-32** · P1 — Name, number, type, exact time, duration.
      `TYPE Outgoing · WHEN 11 Nov, 20:40 · DURATION 0m 42s · NUMBER 0300 771 2008` (+ `SIM` when dual).
- [x] **PH-33** · P1 — Actions: call, message, block, delete, save contact. All five buttons on the screen.
- [x] **PH-34** · P1 — Delete removes the row and it does not come back.
      DELETE → provider query dropped from 5 rows to 4 and the list re-read as `4 CALLS`.

## In-call screen

- [x] **PH-35** · P0 — Shows who you are calling. `Nadia Iqbal` + `0300 771 2008`; unknown numbers show `#`.
- [x] **PH-36** · P0 — Timer runs while connected, stops on END.
      `CALLING` for 1.2 s, then `CONNECTED` and `00:00` starts counting; gone with the surface.
- [x] **PH-37** · P0 — MUTE and SPEAKER toggle with visible state.
      SPEAKER flips the header to `IN CALL · SPEAKER` and inverts its tile; MUTE inverts too.
- [x] **PH-38** · P0 — END CALL returns to where the call started. → CALLS list.
- [x] **PH-39** · P0 — KEYPAD opens a DTMF pad and shows digits sent.
      12 tappable keys, pressing `1` shows `SENT · 1`, each press gives a haptic tick.
- [x] **PH-40** · P1 — HOLD with visible held state. `HOLD` tile inverts and the header reads `IN CALL · ON HOLD`.
- [x] **PH-41** · P1 — Calling → connected transition. See PH-36.
- [x] **PH-42** · P1 — Back cannot strand the user. System back ends the call and returns to CALLS
      (`BackHandler { endCall(...) }` on the call surface).
- [x] **PH-43** · P1 — Blocked caller flagged on the call screen. `BLOCKED · NUMBER RULE` chip.
- [x] **PH-44** · P1 — Screen stays awake. `view.keepScreenOn = true` for the call surface, cleared on dispose.

## Incoming calls / notifications

- [!] **PH-45** · P0 — Incoming call surface with caller ID, accept and decline.
      Still missing: no `InCallService`/`ConnectionService`, no incoming-call Activity or
      notification in the manifest. This is the one P0 the phone tab does not yet have.
- [!] **PH-46** · P1 — Missed-call notification, cleared by opening the log. No notification code exists.
- [!] **PH-47** · P1 — Blocked calls silenced. Nothing intercepts calls; blocking is display-side only.
- [ ] **PH-48** · P1 — Call notification does not leak blocked content. Nothing to test: no notification.
- [ ] **PH-49** · P1 — Declined calls logged. Trinity never handles an incoming call.

## States, entry points, edge cases

- [x] **PH-50** · P0 — Missing permissions explained, with a way to grant.
      Gate lists Contacts / SMS / Call log with reasons + ALLOW ACCESS.
- [x] **PH-51** · P0 — No SIM / no telephony says why.
      `NO TELEPHONY ON THIS DEVICE · A CALL CANNOT CONNECT` on the pad, and
      `CANNOT CONNECT · NO TELEPHONY ON THIS DEVICE` inside the call.
- [x] **PH-52** · P1 — Emergency numbers stay dialable.
      `BlockMatcher.match` returns null for 112/911/999/110/118/102/100/108 before any rule runs;
      the pad shows `EMERGENCY NUMBER · RULES DO NOT APPLY`.
- [x] **PH-53** · P1 — `tel:` / `ACTION_DIAL` opens the pad pre-filled.
      `am start -n …/.MainActivity -a android.intent.action.DIAL -d tel:03219876543` →
      dial pad pre-filled with `0321 987 6543` and its recent-call suggestion.
      (WSA has no handler for the bare DIAL action, so the component was addressed directly.)
- [~] **PH-54** · P1 — Offered as the default dialer.
      Settings now carries `Default phone app`, which requests `ROLE_DIALER` and reports the real
      state. WSA offers no dialer role, so it still reads `NOT SET` here.
- [!] **PH-55** · P2 — Favourites / speed dial. Not implemented.
- [!] **PH-56** · P2 — Voicemail entry with a count. Not implemented.
- [~] **PH-57** · P2 — Dual SIM: choose SIM per call; the log shows the SIM used.
      Implemented: `Sims` reads active subscriptions, the pad shows one chip per SIM and dials
      through that subscription's `PhoneAccountHandle` (reflection, since Android has no public
      API for it), the call header and the log/detail rows carry `SIM 1`/`SIM 2`.
      **Not proven**: this device has no SIMs, so the chips never render here.
- [!] **PH-58** · P2 — Search highlights the match. Search exists, highlighting does not.

## Micro-detail, motion, accessibility

- [x] **PH-59** · P1 — Grouped digits everywhere. Field, log, detail and call header all use `formatNumber`.
- [x] **PH-60** · P1 — Long-press a number to copy.
      Long-press a log row → `COPIED · 0300 771 2008`; long-press the typed number copies it too.
- [x] **PH-61** · P1 — Quick, quiet transitions; no coloured ripple, no bounce.
      No animation code at all; the only motion is the achromatic ripple and ink tile flips.
- [~] **PH-62** · P1 — Descriptions and row announcements.
      Tab icons, Delete, Settings, Close, call-type icons and the call tiles all have descriptions.
      Keypad keys expose their digit but not their letters.
- [x] **PH-63** · P1 — Touch targets ≥48 dp. Backspace 48 dp, settings gear 48 dp, keys 128×74 dp.
- [x] **PH-64** · P1 — Font scaling without clipping. At `font_scale 1.3` the list renders complete.
- [ ] **PH-65** · P2 — Landscape / small-height windows. Unverified: WSA ignores rotation and the
      emulator cannot boot on this host (see below).

## Score

65 items: **49 pass**, **5 partial**, **8 fail**, **3 unverified**.
P0 (26 items): **25 pass**, 1 fail — the missing incoming-call surface (PH-45).

## What the fix pass changed

- Dial pad rebuilt: digits hoisted into `TrinityState`; live known-name line; RECENT CALLS and
  CONTACTS suggestion lists; fixed 74 dp key rows anchored above CALL so the pad no longer
  resizes (or jumps) when suggestions appear; advisories moved below the keypad; haptics;
  long-press clear / `+`; disabled CALL state; emergency and short-code notices.
- Calls tab: contact-name resolution, grouped digits, day headers with per-day counts,
  collapsed repeats, `MISSED` count, search, INFO → detail, long-press copy, clear history
  with confirm, refresh on entry.
- New `CallDetailScreen` with five actions and working delete (`WRITE_CALL_LOG`).
- In-call: DTMF pad that reports digits sent, HOLD, CALLING→CONNECTED transition, back ends
  the call, screen kept awake, no-telephony copy.
- `Sims` + dual-SIM chips, per-SIM dialling and SIM tags in the log/detail/call header.

## Verification environment

- Device: Windows Subsystem for Android, Android 13 (SDK 33), rooted, phone-shaped window.
- Call log seeded through `content insert` on `content://call_log/calls` (5 rows across
  today / yesterday / 8 Nov, one number twice, one under a number rule).
- The Android emulator **is installed** — `emulator` 37.2.13, `system-images;android-34;google_apis;x86_64`,
  WHPX reported operational, AVD `trinity34` created — but every launch dies before the guest
  kernel prints. Both crashpad reports carry
  `ERROR | detected a hanging thread 'QEMU2 CPU0 thread'. No response for 15001 ms`, then the
  process segfaults. Reproduced with WHPX, with `-accel off`, headless and windowed, on a freshly
  recreated AVD. That is why live telephony and incoming calls still cannot be exercised here.
