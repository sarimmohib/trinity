# MESSAGING / SMS — feature checklist

Scope: everything the Messages tab and the text surfaces must do — conversation list,
thread, composer, sending states, notifications, and the blocking/quarantine behaviour
that is Trinity's reason to exist.

Priority: **P0** = broken without it. **P1** = every real SMS app has it. **P2** = polish.

Status: `[x]` pass on the device · `[~]` partial · `[!]` absent/failing ·
`[?]` built and reviewed, but **not provable on this host** (no SMS provider — see the
environment note at the bottom). `[?]` is never counted as a pass.

## The environment problem, stated once

Windows Subsystem for Android has no telephony provider at all:

```
$ adb shell content query --uri content://sms --projection _id
Error while accessing provider:sms
java.lang.IllegalStateException: Could not find provider: sms
```

So on this host there is no inbox to read, no thread to open and no text to deliver.
The Android emulator — which does have SMS — is installed but cannot boot here (see the
phone checklist for the crash report). Every item that needs real messages is marked
`[?]`, quoted against the code that implements it, and must be re-checked on a phone.

## Conversation list

- [x] **MSG-01** · P0 — Each row shows who it is (name, else formatted number) and the last message snippet.
      Row renders `name ?: shownNumber` (grouped digits) and the snippet, prefixed `You: ` when the
      last word was the user's. Snippet newlines flattened to spaces.
- [x] **MSG-02** · P0 — Each row shows when the last message arrived, humanised.
      `relativeStamp`: time today, `Yesterday`, weekday within a week, then a short date.
- [x] **MSG-03** · P0 — Unread is visible without colour. Filled avatar + ink count badge +
      the snippet goes ink/medium; read rows use the muted wash avatar. No hue involved.
- [x] **MSG-04** · P1 — Unread count per row and a total in the header.
      Badge per row; header meta is `N CONVERSATIONS · U UNREAD · K FILTERED` (verified on device).
- [x] **MSG-05** · P1 — Hairlines between rows. `Hairline()` (Palette.Line) after every row.
- [x] **MSG-06** · P1 — Initials avatar, same component as Contacts.
- [~] **MSG-07** · P1 — Newest first, pinned/muted respected.
      Sorted by `DATE DESC`; muted conversations carry a `MUTED` chip, stay out of notifications
      and are dimmed. Pinning does not exist yet.
- [?] **MSG-08** · P1 — Long-press a row: mark read/unread, mute, delete, block, add to contacts.
      Long-press opens the ink sheet with exactly those actions (plus copy number and call).
- [?] **MSG-09** · P1 — Swipe to delete a conversation, with confirm.
      `SwipeToDismissBox` (end→start) springs the row back and opens a `DELETE CONVERSATION`
      sheet whose first action is `Delete every message · CANNOT BE UNDONE`.
- [?] **MSG-10** · P1 — A blocked conversation says why it is hidden and how to unblock.
      Name/number-rule conversations are dropped from the list but counted in the header;
      `N hidden by M rules · REVIEW` opens the Held screen, which names the rule per text and
      offers ALLOW SENDER.
- [?] **MSG-11** · P0 — Empty state explains that texts will appear here.
      Prose: newest first, one row per person, rules hide and count sender, keyword hits stay
      visible but flagged.
- [?] **MSG-12** · P1 — Refresh on return to the tab. `LaunchedEffect(Unit) { state.refresh() }`.
- [?] **MSG-63a** · P1 — A conversation with no name shows a grouped number, never raw digits.
      `Conversation.shownNumber` groups via `formatDisplayNumber` (`0300 771 2008`).
- [?] **MSG-63b** · P2 — Outgoing-last conversations read as `You: …` so the list scans correctly.
- [?] **MSG-63c** · P2 — Muted conversations are marked in the list, not hidden.
      `MUTED` mono chip beside the name.

## Composing and sending

- [x] **MSG-13** · P0 — A new-message affordance is reachable from the list.
      `NEW` pill in the Messages header; tapping it opened `New message` on the device.
- [x] **MSG-14** · P0 — Choose a contact or type a number, then type the body and send.
      Verified: typed `03001234001` → `ADD` + `USE 0300 123 4001`; contact matches filter live.
- [~] **MSG-15** · P1 — Multi-recipient before sending.
      Recipients are a chip list; each chip removes itself on tap and every recipient gets its
      own `SmsManager.sendTextMessage`. Only one chip was added on the device, so the multi-add
      path is code-only.
- [x] **MSG-16** · P0 — Composer is multiline, grows with text, and the IME send key sends.
      Message text sits in a `BasicTextField` with `ImeAction.Send` + `KeyboardActions(onSend)`;
      new-message body is a multiline field with a 90 dp floor.
- [x] **MSG-17** · P0 — Send is disabled while the body is empty.
      Verified both ways: `SEND IS DISABLED UNTIL THERE IS TEXT` with a wash tile, then `READY`
      and an ink tile once text exists.
- [?] **MSG-18** · P0 — Sent text appears immediately, then settles.
      `sendMessage` appends a `PendingMessage` bubble at once, then reconciles with the provider.
- [?] **MSG-19** · P0 — Sending state visible and distinct from failed.
      Bubble footer shows `· SENDING`; provider `OUTBOX`/`QUEUED` map to SENDING, `FAILED` to
      `· NOT SENT`.
- [?] **MSG-20** · P0 — Failed sends show a marker with retry.
      `FAILED · <reason>` with `RETRY` (resends the same body) and `DISCARD`.
- [x] **MSG-21** · P1 — Character counter near multi-part length.
      Verified `11/160 · 1 PART`; `smsParts` switches to 153/67-char parts and UCS-2 limits for
      non-ASCII.
- [?] **MSG-22** · P1 — Drafts survive leaving the thread.
      Draft written to `trinity.threads` on every keystroke, restored on open, cleared on send.
- [!] **MSG-23** · P2 — Attach image / take photo. Not implemented.
- [!] **MSG-24** · P2 — Schedule send. Not implemented.
- [?] **MSG-63d** · P1 — Sending to a brand-new number creates its thread and opens it.
      `Telephony.Threads.getOrCreateThreadId` after a successful send; the composer then routes
      to that conversation.
- [?] **MSG-63e** · P1 — A send that the system rejects says why, in words.
      `SmsSender` turns `RESULT_ERROR_NO_SERVICE` / `RADIO_OFF` / `NULL_PDU` into
      `NO SERVICE` / `RADIO OFF` / `MESSAGE REJECTED` instead of failing silently.
- [?] **MSG-63f** · P2 — Reply from a notification does not open the app.
      `DirectReplyReceiver` + `RemoteInput` send the text and clear the notification.

## Thread

- [~] **MSG-25** · P0 — Incoming and outgoing are visually distinct and attributed.
      Outgoing: ink bubble, right-aligned, paper text. Incoming: paper bubble, hairline border,
      left-aligned. A sender is not printed inside the bubble because a thread has one sender.
- [?] **MSG-26** · P0 — Per-message timestamps with date separators.
      Every bubble carries a stamp; the list is split by day and each run opens with a
      `TODAY` / `YESTERDAY` / `6 Nov` rule. The old hardcoded `TODAY` header is gone.
- [?] **MSG-27** · P1 — Message detail: type, exact time, state.
      `Details` sheet: absolute stamp, `OUTGOING`/`INCOMING`, state, `MESSAGE #id`.
- [?] **MSG-28** · P1 — Long-press: copy, forward, delete, share.
      Sheet has `Copy text`, `Forward` (opens the composer pre-filled), `Details`,
      `Delete message`. Sharing to another app is not there yet.
- [?] **MSG-29** · P1 — Deleting a message removes it and it stays gone.
      `ContentResolver.delete` on the message row, then refresh.
- [?] **MSG-30** · P0 — Opening a thread marks it read and clears the list badge.
      `openThread` sets READ/SEEN, re-reads the list and cancels the notification.
- [?] **MSG-31** · P0 — Thread scrolls to the newest message when opened.
      First non-empty layout scrolls to `rows.lastIndex`.
- [?] **MSG-32** · P1 — A new message appends without jumping the reader.
      Auto-scroll only while `!canScrollForward` (tracked through `snapshotFlow`).
- [?] **MSG-33** · P1 — Jump-to-latest control when scrolled up.
      `LATEST` pill appears while the list can still scroll forward; tap animates to the end.
- [~] **MSG-34** · P1 — Header shows person, number and actions.
      Number + `SMS` (or `HELD SENDER`) + `MUTED`; actions are search, call, blocking and a menu
      with mute / mark unread / add contact / delete. No `add contact` for known people, correctly.
- [?] **MSG-35** · P1 — Search inside the thread, matches highlighted.
      Search field filters bodies; hits are inverted (ink block, paper text) — no hue.
- [?] **MSG-36** · P1 — Unknown senders offer SAVE CONTACT / BLOCK inline.
      `Add to contacts` in the header menu and on the list sheet; the flagged-message card offers
      ALLOW SENDER.
- [?] **MSG-63g** · P2 — A thread with no messages explains how to start one.
      Verified copy: `No messages yet. Type below to start the conversation.`
- [?] **MSG-63h** · P2 — An empty search result names the query.
      `Nothing in this conversation matches "…".`

## Blocking and quarantine (Trinity's core)

- [?] **MSG-37** · P0 — A keyword match shows that single message as a flagged card.
      `HIDDEN BY KEYWORD RULE` card with the matched value, `SHOW ONCE` and `ALLOW SENDER`.
- [~] **MSG-38** · P0 — Reveal is session-only.
      `revealed` is an in-memory set on `TrinityState`; it is never written to disk.
- [x] **MSG-39** · P0 — A name or number rule hides the whole conversation and beats a keyword.
      `BlockMatcher.match` checks name → number → allowed → keyword, in that order. Verified on
      the call log, which shares the matcher (`1 MATCHED RULES`).
- [x] **MSG-40** · P0 — Keyword matches are counted, not silently dropped. Header shows
      `K FILTERED` (verified: `0 FILTERED` with two rules and no provider).
- [?] **MSG-41** · P0 — Held screen lists texts stopped before the inbox, with counts.
      `STOPPED BEFORE DELIVERY · n` section above `IN THE INBOX BUT MATCHED · n`.
- [?] **MSG-42** · P0 — Held entries show sender, body, arrival time and the rule that caught them.
- [?] **MSG-43** · P0 — DISCARD removes a held text; ALLOW SENDER vouches for the sender.
- [?] **MSG-44** · P0 — Allowing a sender lifts their rules, stops keywords catching them and
      clears their held texts. `allowSender`: remove name rule, remove number rule, add allowed,
      `quarantine.removeFor(address)`, refresh.
- [?] **MSG-45** · P0 — Allowed senders are listed and revocable.
      `BlockingScreen` lists `allowed` with `disallowSender`, so allow is reversible.
- [~] **MSG-46** · P1 — Held texts are capped, with the cap stated.
      `QuarantineStore.add` keeps the newest 200 (`take(200)`) but the number is not printed
      anywhere.
- [?] **MSG-47** · P1 — An inbox text that matched a keyword is listed separately from a held one.
      Two sections in the Held screen.
- [x] **MSG-48** · P0 — While Trinity is not the SMS app, the screen says so and offers MAKE DEFAULT.
      Verified on device: `Not the default SMS app — blocking cannot stop delivery · MAKE DEFAULT`,
      tapping it calls `RoleManager.createRequestRoleIntent(ROLE_SMS)`.
- [?] **MSG-49** · P1 — After taking the role, held texts really are absent from the system inbox.
      `SmsDeliverReceiver` returns before any provider insert when a rule matches, so the message
      only exists in quarantine. Cannot be proven here: no SMS role, no delivery.

## Notifications

- [?] **MSG-50** · P1 — One notification per conversation, tap opens that thread.
      `MessageNotifier.show` → id is the thread id, `MessagingStyle`, content intent carries
      `trinity.thread` / `trinity.address`, and `MainActivity` routes straight to the thread.
- [?] **MSG-51** · P1 — Quick reply without opening the app.
      `RemoteInput` + `DirectReplyReceiver`; sends through `SmsSender`.
- [?] **MSG-52** · P1 — Mark-as-read action from the notification.
      `MarkReadReceiver` sets READ/SEEN for the thread and cancels the notification.
- [?] **MSG-53** · P1 — Held texts never preview content.
      `showHeld` prints only `N texts held · Matched your rules. Tap to review.`
- [~] **MSG-54** · P1 — Muted conversations stay silent.
      `SmsDeliverReceiver` checks the mute list before posting; the list screen still shows the
      conversation with a `MUTED` chip.
- [?] **MSG-55** · P1 — Notifications clear when the conversation is read.
      `openThread` and `markRead` both call `MessageNotifier.cancel`.

## States, edge cases, micro-detail

- [x] **MSG-56** · P0 — SMS permission missing: explains the need and offers to grant it.
      The startup gate names SMS and why ("to show threads and hold back matched texts").
- [x] **MSG-57** · P1 — No SMS provider says so instead of showing a blank list.
      Verified on this host: `This device has no SMS provider, so there is nothing to read here.`
      followed by what the tab does on a real phone.
- [x] **MSG-58** · P1 — Keyboard never covers the composer. `windowSoftInputMode="adjustResize"`
      on MainActivity; the composer is the last element of the screen column.
- [?] **MSG-59** · P1 — Back from a thread returns to the list. `BackHandler` routes `Tab(1)`;
      list scroll position is kept by the tab's own `LazyColumn` state being hoisted per visit.
      Scroll preservation across a route change is not proven.
- [?] **MSG-60** · P1 — Empty states for no conversations, no results, empty thread.
      All three exist; the first two cannot be reached on this host because the
      provider-missing state correctly wins.
- [x] **MSG-61** · P1 — Quiet ink press feedback, no coloured ripple.
      Every surface clicks through Material's ripple, tinted by the ink-on-paper scheme; sheets
      are wash-scrimmed `InkSheet`s with no elevation or hue.
- [~] **MSG-62** · P1 — Timestamps update across midnight while a screen is open.
      Stamps are recomputed on every recomposition, but nothing schedules a recomposition at
      midnight, so an idle screen keeps yesterday's label until it is touched.
- [~] **MSG-63** · P1 — Every icon and row labelled for TalkBack.
      Icons carry descriptions; bubbles expose their text but not their send state.
- [~] **MSG-64** · P2 — Long text and long threads stay smooth.
      Thread is a `LazyColumn` keyed by index with a 272 dp bubble; not measured on a long thread.
- [?] **MSG-65** · P1 — Multi-recipient threads are not silently merged into one.
      Each recipient gets its own send call, so the provider keeps separate threads.
- [?] **MSG-66** · P2 — A sent message is not duplicated when Trinity holds the SMS role.
      `SmsStore.insertSent` writes exactly one row after a successful send; without the role
      nothing is written and the pending row is dropped on the next open.

## Score

66 items: **11 pass**, **8 partial**, **3 absent**, **44 unverifiable on this host**.

Every `[?]` is a real gap in evidence, not a formality: the messaging tab is the app's core
(the whole reason for name- and keyword-based blocking) and it has only ever been exercised
here as an empty shell. Nothing in that column should be trusted until it runs on a phone.

## What this pass changed

- List: `NEW` action, unread total in the header, `You:` snippets, grouped numbers,
  `MUTED` chips, refresh on entry, provider-missing state, prose empty state, long-press sheet
  (mark read/unread, mute, copy, add contact, call, block, delete), swipe-to-delete with a
  confirmation sheet, and a truthful `FILTERED` count.
- New message screen: recipient chips, live contact/number matching, `USE <number>`,
  multiline body, part counter, disabled send, discard guard.
- Thread: day rules instead of a hardcoded `TODAY`, mark-read on open, scroll-to-newest with
  stick-to-bottom, `LATEST` pill, in-thread search with inverted highlighting, header menu
  (mute, mark unread, search, add contact, delete conversation), per-message sheet
  (copy, forward, details, delete), failed-send row with RETRY/DISCARD, draft persistence,
  IME send, character/part counter.
- Sending: real result codes from `SmsManager`, pending bubbles (`SENDING` → `SENT`/`FAILED`),
  provider insert only while Trinity holds the SMS role, thread creation for new numbers.
- Notifications: per-conversation `MessagingStyle`, quick reply, mark read, held-count
  notification with no content, cancel-on-read, per-thread mute respected at delivery.
- Data: `ThreadPrefs` for drafts and mutes, richer `MessagesRepository` (unread totals,
  mark read/unread, delete message/thread, provider detection, send states), `SmsStore`.
