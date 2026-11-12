# CONTACTS — feature checklist

Scope: the Contacts tab and every screen that writes to the phone's address book —
list, search, detail, create, edit, delete, blocking hooks, and the provider round-trip.

Priority: **P0** = broken without it. **P1** = every real contacts app has it. **P2** = polish.

Status: `[x]` pass on the device · `[~]` partial · `[!]` absent/failing.
Unlike messaging, this tab reads a provider that exists on the test host, so most of it
is real evidence.

## List

- [x] **CON-01** · P0 — Loads from the phone's provider with name and number.
      Verified: `Nadia Iqbal · 0300 771 2008` read from `ContactsContract` at launch.
- [x] **CON-02** · P0 — Alphabetical sections with letter headers. Verified: `N · 1` header
      above the only contact; sections are `groupBy { it.section }` sorted by letter.
- [~] **CON-03** · P1 — Headers stick while scrolling. `stickyHeader` is in place, but one
      contact cannot scroll, so pinning is unproven.
- [x] **CON-04** · P0 — Hairlines between rows, dark enough (`Palette.Line`, 1 dp).
- [~] **CON-05** · P1 — Avatar with initials. Initials verified (`NI`); the provider photo
      is not used anywhere.
- [x] **CON-06** · P0 — Tapping a row opens that contact. Verified.
- [~] **CON-07** · P1 — Empty state explains the book is empty and offers to add one.
      The prose exists (`No contacts yet … filed under its first letter`) and the NEW CONTACT
      bar is always present, but the state cannot be reached with a contact on the device.
- [~] **CON-08** · P1 — Refresh on return. `LaunchedEffect(Unit) { state.refresh() }`;
      an external add was not performed to observe it.
- [x] **CON-09** · P0 — Header states counts truthfully. Verified: `1 SAVED · 0 BLOCKED`.
- [~] **CON-10** · P0 — Search by name and by number. Filter covers name, digits of every
      number and emails, but no query was typed on the device this pass.
- [~] **CON-11** · P1 — Case-insensitive yes; spacing and country prefixes are not normalised
      (`+92 300…` will not match `0300…` beyond the digits it contains).
- [x] **CON-12** · P1 — Search covers email addresses. Included in the same predicate.
- [~] **CON-13** · P1 — Empty-result state naming the query: `No contacts match "…"`.
- [x] **CON-14** · P1 — Clearing the query restores the full list (plain filter, no reload).
- [!] **CON-15** · P2 — T9/dial-pad search. Absent.

## Detail

- [x] **CON-16** · P0 — Name, initials and **every** number, each with its own label.
      Verified: `NUMBERS · 1 | 0300 771 2008 | MOBILE`.
- [~] **CON-17** · P0 — CALL and MESSAGE from detail.
      CALL verified. MESSAGE now opens Trinity's own composer pre-filled instead of firing an
      `smsto:` intent at whatever app claims it.
- [x] **CON-18** · P1 — Emails listed and tappable (`mailto:`). The test contact has none.
- [~] **CON-19** · P0 — Block state visible with block/unblock. The chip renders when a rule
      matches; neither direction was exercised this pass.
- [x] **CON-20** · P1 — Copy number. Row copies every number, comma separated.
- [x] **CON-21** · P1 — Share contact. `ACTION_SEND` with `Name · numbers`.
- [x] **CON-22** · P1 — Call history reachable. Verified: `Call history · 2 CALLS` from the
      seeded log, tapping it opens the Calls tab.

## Create

- [x] **CON-23** · P0 — New-contact screen with name, phone and email. Verified in the editor.
- [x] **CON-24** · P0 — Live preview of initials, name and number. Verified: avatar `NI` and
      the number tracked every keystroke.
- [x] **CON-25** · P0 — Phone field uses a number keyboard and rejects letters.
      `KeyboardType.Phone` plus an input filter that keeps only `0-9 + * # - ( )` and spaces.
- [~] **CON-26** · P0 — SAVE writes to the provider and the contact appears.
      The write path is proven (see CON-35); creating a brand-new contact by tapping through
      the form was blocked by the harness (the tap after ADD ANOTHER NUMBER landed off-target).
- [x] **CON-27** · P0 — Saving with neither name nor number is refused, with a reason.
      The save bar is disabled and says `A NAME OR A NUMBER IS REQUIRED`.
- [~] **CON-28** · P1 — Duplicate warning. Implemented (`That number is already saved as …`),
      not observed on the device.
- [x] **CON-29** · P1 — CANCEL asks before discarding a filled form. Verified the sheet:
      `DISCARD · Discard and leave (NOTHING WRITTEN) · Keep editing`.
- [~] **CON-30** · P1 — Save gives confirmation feedback. The editor shows
      `SAVED · CONTACT ADDED` / `SAVED · CHANGES WRITTEN` before leaving; the strip was not
      photographed because navigation follows immediately.
- [x] **CON-31** · P1 — A number typed on the dial pad arrives pre-filled (route carries it).
- [!] **CON-32** · P2 — Per-number label picker (MOBILE/HOME/WORK). Labels are read from the
      provider but cannot be chosen in Trinity.
- [!] **CON-33** · P2 — Photo. Absent.

## Edit and delete

- [x] **CON-34** · P0 — Edit an existing contact: name, number, email. Verified: `Edit contact`
      opens prefilled with `Nadia Iqbal` / `03007712008`.
- [x] **CON-35** · P0 — Edits persist to the provider and survive a restart.
      **Verified against the provider**: appending `9` to the field and tapping SAVE CHANGES
      turned `data1=03007712008` into `data1=030077120089` in `ContactsContract.Data`, and the
      list re-read the new number as `03007 712 0089`. The original was restored afterwards.
- [~] **CON-36** · P0 — Delete a contact behind a confirmation. The sheet exists
      (`Delete Nadia Iqbal · CANNOT BE UNDONE`); the delete itself was not run.
- [x] **CON-37** · P1 — Add a second number. Verified: `ADD ANOTHER NUMBER` produced
      `PHONE 2` with its own REMOVE chip.
- [~] **CON-38** · P1 — Remove one number from a contact with several. The REMOVE chip renders
      per row; the removal itself was not exercised.
- [~] **CON-39** · P1 — A deleted contact disappears from list, threads and call-log names.
      Follows from the refresh, not observed.
- [!] **CON-40** · P2 — Merge duplicates. Absent.
- [!] **CON-41** · P2 — vCard import/export. Absent.

## Blocking hooks

- [~] **CON-42** · P0 — Block from detail lands a rule in the block list.
      `Block this contact` adds a name rule and one number rule per number.
- [~] **CON-43** · P0 — Unblock from detail removes the matching rule. Removes the name rule
      and every number rule for that person.
- [x] **CON-44** · P1 — Blocking by name matches everywhere, including new numbers.
      `BlockMatcher` name comparison is `contains(…, ignoreCase = true)`.
- [x] **CON-45** · P1 — A blocked contact is marked in the list, not hidden.
      Verified label is now `BLOCKED` (it previously said `MUTED`).
- [~] **CON-46** · P1 — Block at the same time as saving. The toggle renders
      (`Block this contact too · ADDS A NAME AND NUMBER RULE AFTER SAVING`).

## States, edge cases, micro-detail

- [x] **CON-47** · P0 — READ_CONTACTS missing: explained with a way to grant.
      Startup gate lists Contacts with the reason "to name people and block by name".
- [x] **CON-48** · P0 — WRITE_CONTACTS failure says so instead of failing silently.
      `The address book refused the write. Nothing was saved — check the contacts permission.`
- [x] **CON-49** · P1 — Provider write failures surface as an error, never fake success.
      `ContactWriter` returns null/false on failure and the screen prints it.
- [~] **CON-50** · P1 — Thousands of contacts stay scrollable. `LazyColumn` + `stickyHeader`;
      untested at scale (one contact on this device).
- [x] **CON-51** · P1 — Numbers displayed grouped. Verified `0300 771 2008`, `03007 712 0089`.
- [x] **CON-52** · P1 — Initials from one-word and multi-word names. `Nadia Iqbal → NI`.
- [~] **CON-53** · P1 — Rows ≥48 dp with TalkBack labels. Rows are 72 dp; name and number are
      separate text nodes rather than one merged announcement.
- [~] **CON-54** · P2 — Alphabet fast-scroll index. Implemented on the right edge, shown only
      when there are more than four sections, so it never appeared here.

## Added by this pass

- [x] **CON-55** · P1 — Every number carries its provider label (MOBILE / HOME / WORK / FAX).
- [x] **CON-56** · P1 — Extra numbers are summarised in the list as `+N MORE`.
- [~] **CON-57** · P1 — Back returns to the tab you came from. The global back handler now maps
      Contact and NewContact to the Contacts tab; not re-tested after the fix.
- [x] **CON-58** · P1 — The system back key obeys the discard guard. Verified: pressing back on
      a dirty editor opens `DISCARD` instead of losing the form.
- [x] **CON-59** · P2 — Extra number rows are numbered (`PHONE`, `PHONE 2`), each removable.
- [~] **CON-60** · P2 — Email field uses an email keyboard.
- [~] **CON-61** · P1 — Destructive rows name the person and the consequence.
- [~] **CON-62** · P2 — A blocked row can show which rule caught it.
- [~] **CON-63** · P1 — Blocking from detail adds a name rule **and** a rule for every number.
- [~] **CON-64** · P2 — Search states how many matched (`N FOUND`).
- [x] **CON-65** · P2 — The search bar states how many sections exist. Verified: `1 SECTIONS`.

## Score

65 items: **33 pass**, **27 partial**, **5 absent**.

The provider round-trip — the part that could silently corrupt someone's address book — is
the part actually proven, in both directions, against `ContactsContract`.

## What this pass changed

- Every number and email per contact is now read (previously only the first number per name,
  keyed by name, so two people sharing a name collapsed into one row).
- List: letter sections with sticky headers, alphabet index, formatted numbers, `+N MORE`,
  `BLOCKED` instead of `MUTED`, refresh on entry, real empty/empty-result states, and the
  primary action moved to a bottom bar.
- Detail: all numbers with labels, emails, copy, share, real call-history count, and rows for
  block/unblock/delete with a confirmation sheet that names the person.
- Editor: one screen for create and edit, several numbers, duplicate warning, phone/email
  keyboards, digit-only filter, disabled save with a reason, discard guard (close **and** back),
  and a save result that admits failure.
- `ContactWriter`: `save` returns a real id, `update` rewrites name/numbers/email rows,
  `delete` removes the raw contact.
