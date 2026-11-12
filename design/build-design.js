// Trinity — phone + messages + contacts, one app.
// OpenPencil 0.8.5 document generator.
//
// Palette rule (hard): achromatic only. No purple, neon, deep blue, red,
// green, orange, and no gradients anywhere. Hierarchy comes from weight,
// scale, and hairline rules instead of hue.
const BONE = '#F1EDE4'; // app background
const PAPER = '#FAF8F2'; // raised surface
const INK = '#17160F'; // primary text / filled controls
const MUT = '#6D6A5C'; // secondary text
const FAINT = '#9C9788'; // tertiary text
const LINE = '#C6BEA8'; // visible row border — rows must never blur together
const LINE_SOFT = '#DCD6C8'; // section rules only
const WASH = '#E7E2D5'; // inert fill

const UI = 'Inter';
const MONO = 'Consolas';

const s = (c) => [{ type: 'solid', color: c }];
const hair = { thickness: 1, fill: s(LINE) };
const hairSoft = { thickness: 1, fill: s(LINE_SOFT) };
const topHair = { thickness: { top: 1 }, fill: s(LINE) };

const doc = { version: '0.8.5', id: 'trinity-doc', name: 'Trinity', children: [], editorMeta: { activePageIndex: 0, preserveAuthoredGeometry: false } };
let seq = 0;

function I(parent, node) {
  node.id = node.id || 't' + ++seq;
  if (parent) {
    parent.children = parent.children || [];
    parent.children.push(node);
  }
  return node;
}

function fr(name, props) {
  return Object.assign(
    { type: 'frame', name, layout: 'vertical', width: 'fill_container', height: 'fit_content', fill: [] },
    props,
  );
}
function row(name, props) {
  return Object.assign(fr(name, { layout: 'horizontal', alignItems: 'center' }), props);
}
function txt(name, content, props) {
  return Object.assign(
    { type: 'text', name, content, fontFamily: UI, fontSize: 14, fontWeight: 400, lineHeight: 1.35, fill: s(INK), textGrowth: 'auto' },
    props,
  );
}
function mono(name, content, props) {
  return txt(name, content, Object.assign({ fontFamily: MONO, fontSize: 10, letterSpacing: 0.9, fill: s(MUT) }, props));
}
function rule(name, props) {
  return Object.assign({ type: 'rectangle', name, width: 'fill_container', height: 1, fill: s(LINE) }, props);
}
function softRule(name, props) {
  return Object.assign({ type: 'rectangle', name, width: 'fill_container', height: 1, fill: s(LINE_SOFT) }, props);
}
function slab(name, color, props) {
  return Object.assign({ type: 'rectangle', name, width: 'fill_container', height: 8, fill: s(color) }, props);
}
function dot(name, color, size) {
  return { type: 'ellipse', name, width: size || 6, height: size || 6, fill: s(color) };
}
function ic(name, glyph, props) {
  return Object.assign(
    { type: 'icon_font', name, iconFontName: glyph, iconFontFamily: 'lucide', width: 18, height: 18, fill: s(MUT) },
    props,
  );
}

// Phone shell ---------------------------------------------------------------
function phone(name, x, tabIndex) {
  const f = I(null, {
    type: 'frame', name, x, y: 0, width: 390, height: 844,
    layout: 'vertical', gap: 0, fill: s(BONE),
  });
  doc.children.push(f);

  const bar = I(f, row('status-bar', { height: 56, padding: [0, 26], justifyContent: 'space_between' }));
  I(bar, mono('clock', '9:41', { fontSize: 12, fontWeight: 600, fill: s(INK), letterSpacing: 0.4 }));
  const right = I(bar, row('status-icons', { width: 'fit_content', gap: 5 }));
  I(right, ic('sig', 'signal', { width: 14, height: 14, fill: s(INK) }));
  I(right, ic('wifi', 'wifi', { width: 14, height: 14, fill: s(INK) }));
  I(right, ic('battery', 'battery-medium', { width: 16, height: 16, fill: s(INK) }));

  const body = I(f, { type: 'frame', name: 'body', width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 0, fill: [] });
  const tabs = I(f, row('tab-bar', { height: 74, padding: [0, 4, 10, 4], stroke: topHair, fill: s(PAPER), justifyContent: 'space_between' }));
  const defs = [['calls', 'CALLS', 'phone'], ['messages', 'MESSAGES', 'message-square'], ['contacts', 'CONTACTS', 'users']];
  for (let i = 0; i < defs.length; i++) {
    const active = i === tabIndex;
    const t = I(tabs, fr('tab-' + i, { width: 'fill_container', gap: 5, alignItems: 'center', justifyContent: 'center' }));
    I(t, ic('tab-' + i + '-icon', defs[i][2], { width: 19, height: 19, fill: s(active ? INK : FAINT) }));
    I(t, mono('tab-' + i + '-label', defs[i][1], { fontSize: 8, letterSpacing: 1.4, fill: s(active ? INK : FAINT) }));
    if (active) I(t, slab('tab-' + i + '-mark', INK, { width: 16, height: 2 }));
  }
  return body;
}

function screenHeader(parent, title, meta, rightLabel) {
  const h = I(parent, fr('header', { padding: [18, 24, 14, 24], gap: 2 }));
  const top = I(h, row('header-top', { justifyContent: 'space_between', alignItems: 'end' }));
  I(top, txt('title', title, { fontSize: 34, fontWeight: 700, letterSpacing: -1.4, lineHeight: 1 }));
  const actions = I(top, row('header-actions', { width: 'fit_content', gap: 8, alignItems: 'center' }));
  if (rightLabel) {
    const pill = I(actions, row('header-action', { width: 'fit_content', height: 26, padding: [0, 9], cornerRadius: 3, stroke: hair, gap: 5 }));
    I(pill, ic('header-action-icon', 'shield', { width: 12, height: 12, fill: s(INK) }));
    I(pill, mono('header-action-label', rightLabel, { fontSize: 8, letterSpacing: 1.2, fill: s(INK) }));
  }
  const gear = I(actions, { type: 'frame', name: 'header-settings', width: 26, height: 26, layout: 'none', cornerRadius: 3, stroke: hair });
  I(gear, ic('header-settings-icon', 'settings', { x: 6, y: 6, width: 14, height: 14, fill: s(INK) }));
  if (meta) I(h, mono('header-meta', meta, { fontSize: 9, letterSpacing: 1.6, fill: s(MUT) }));
  I(parent, rule('header-rule'));
  return h;
}

function avatar(parent, name, initials, filled) {
  const a = I(parent, {
    type: 'frame', name, width: 40, height: 40, layout: 'none',
    cornerRadius: 20, fill: s(filled ? INK : WASH),
  });
  I(a, txt(name + '-initials', initials, {
    x: 0, y: 12, width: 40, fontSize: 13, fontWeight: 600, textAlign: 'center',
    fill: s(filled ? PAPER : INK),
  }));
  return a;
}

function unread(n) {
  const b = { type: 'frame', name: 'unread', width: 20, height: 20, layout: 'none', cornerRadius: 10, fill: s(INK) };
  I(b, txt('count', String(n), { x: 0, y: 3, width: 20, fontSize: 11, fontWeight: 700, textAlign: 'center', fill: s(PAPER) }));
  return b;
}

// 01 · MESSAGES ------------------------------------------------------------
const m = phone('01 Messages', 0, 1);
screenHeader(m, 'Messages', '3 CONVERSATIONS · 1 FILTERED', 'BLOCK 4');

const filterBar = I(m, row('filter-summary', { padding: [12, 24], gap: 8, fill: s(WASH) }));
I(filterBar, ic('filter-icon', 'filter', { width: 13, height: 13, fill: s(INK) }));
I(filterBar, txt('filter-text', '1 message hidden by 2 rules', { fontSize: 11, fontWeight: 500, fill: s(INK) }));
I(filterBar, mono('filter-review', 'REVIEW', { fontSize: 9, letterSpacing: 1.4, fill: s(INK), textAlign: 'right', width: 'fill_container' }));

const list = I(m, fr('thread-list', { gap: 0 }));

function thread(name, initials, sender, preview, time, count, filled, flagged) {
  const r = I(list, row(name, { height: 78, padding: [0, 24], gap: 14, fill: [] }));
  avatar(r, name + '-avatar', initials, filled);
  const col = I(r, fr(name + '-stack', { width: 'fill_container', gap: 3 }));
  const nm = I(col, row(name + '-name-row', { width: 'fit_content', gap: 6 }));
  I(nm, txt(name + '-name', sender, { fontSize: 15, fontWeight: 600, letterSpacing: -0.2 }));
  if (flagged) I(nm, mono(name + '-flag', 'KEYWORD', { fontSize: 7, letterSpacing: 1, fill: s(INK), textAlign: 'center' }));
  I(col, txt(name + '-preview', preview, { fontSize: 12, fill: s(MUT), textGrowth: 'fixed-width', width: 'fill_container' }));
  const meta = I(r, fr(name + '-meta', { width: 52, gap: 6, alignItems: 'end' }));
  I(meta, mono(name + '-time', time, { fontSize: 9, letterSpacing: 0.4, fill: s(FAINT) }));
  if (count) I(meta, unread(count));
  I(list, rule(name + '-rule'));
  return r;
}

thread('m-1', 'AR', 'Areeba Raza', 'Sent the lease scans, check page 4', '9:24', 2, false, false);
thread('m-2', 'PC', 'Pakistan Code', 'VERIFY: OTP 884120', '9:02', 0, false, true);
thread('m-3', 'HM', 'Hina Malik', 'Ok, tomorrow at 6 then', 'Yesterday', 0, false, false);
thread('m-4', 'BK', 'Bank Alfalah', 'Win a prize! Claim at bit.ly/x', 'Tue', 0, true, true);
thread('m-5', 'SM', 'Sami + 2', 'photos from the trail', 'Mon', 0, false, false);
thread('m-6', 'ZA', 'Zainab Ali', 'call me when you can', 'Sun', 0, false, false);

// 02 · THREAD --------------------------------------------------------------
const t = phone('02 Thread', 430, 1);
const thead = I(t, row('thread-header', { height: 66, padding: [0, 20], gap: 12, stroke: { thickness: { bottom: 1 }, fill: s(LINE) }, fill: s(PAPER) }));
I(thead, ic('back', 'arrow-left', { width: 20, height: 20, fill: s(INK) }));
avatar(thead, 'thread-avatar', 'AR', false);
const tnames = I(thead, fr('thread-names', { width: 'fill_container', gap: 1 }));
I(tnames, txt('thread-name', 'Areeba Raza', { fontSize: 15, fontWeight: 600 }));
I(tnames, mono('thread-number', '+92 300 4481 902 · MOBILE', { fontSize: 8, letterSpacing: 1, fill: s(FAINT) }));
const theadActs = I(thead, row('thread-actions', { width: 'fit_content', gap: 14 }));
I(theadActs, ic('call', 'phone', { width: 19, height: 19, fill: s(INK) }));
I(theadActs, ic('block', 'shield-off', { width: 19, height: 19, fill: s(INK) }));

const stream = I(t, fr('stream', { padding: [16, 20], gap: 10, height: 'fill_container' }));
I(stream, mono('day', 'TODAY', { fontSize: 8, letterSpacing: 2, fill: s(FAINT), textAlign: 'center', width: 'fill_container' }));

function bubble(name, body, time, mine, props) {
  const wrap = I(stream, row(name, Object.assign({ gap: 8, alignItems: 'end', justifyContent: mine ? 'end' : 'start' }, props || {})));
  const b = I(wrap, fr(name + '-bubble', {
    width: 'fit_content', height: 'fit_content', padding: [10, 13], gap: 2,
    cornerRadius: 12,
    fill: s(mine ? INK : PAPER),
    stroke: mine ? undefined : hair,
  }));
  I(b, txt(name + '-body', body, { fontSize: 13, lineHeight: 1.45, fill: s(mine ? PAPER : INK), textGrowth: 'fixed-width', width: 240 }));
  I(b, mono(name + '-time', time, { fontSize: 8, fill: s(mine ? FAINT : FAINT), textAlign: 'right', width: 'fill_container' }));
  return wrap;
}

bubble('msg-1', 'Hi, I have the signed pages. Do you want the scans or originals?', '9:18', false);
bubble('msg-2', 'Scans first please', '9:19', true);
bubble('msg-3', 'Sent the lease scans, check page 4', '9:24', false);

// hidden-by-rule message
const hidden = I(stream, fr('hidden-message', {
  width: 'fill_container', height: 'fit_content', padding: [11, 13], gap: 6,
  cornerRadius: 10, stroke: { thickness: 1, fill: s(INK) }, fill: s(WASH),
}));
const htop = I(hidden, row('hidden-top', { width: 'fill_container', justifyContent: 'space_between' }));
const hleft = I(htop, row('hidden-label-row', { width: 'fit_content', gap: 6 }));
I(hleft, ic('hidden-shield', 'shield-alert', { width: 13, height: 13, fill: s(INK) }));
I(hleft, mono('hidden-label', 'HIDDEN BY KEYWORD RULE', { fontSize: 8, letterSpacing: 1.2, fill: s(INK) }));
I(htop, mono('hidden-time', '9:26', { fontSize: 8, fill: s(MUT) }));
I(hidden, txt('hidden-matched', 'Matched: "otp" · "verify"', { fontSize: 11, fontFamily: MONO, fill: s(MUT) }));
const hrow = I(hidden, row('hidden-actions', { width: 'fill_container', gap: 8, padding: [3, 0, 0, 0] }));
const showBtn = I(hrow, row('show-once', { width: 'fit_content', height: 28, padding: [0, 12], cornerRadius: 3, stroke: hair, justifyContent: 'center' }));
I(showBtn, mono('show-once-label', 'SHOW ONCE', { fontSize: 8, letterSpacing: 1.2, fill: s(INK) }));
const allowBtn = I(hrow, row('allow-sender', { width: 'fit_content', height: 28, padding: [0, 12], cornerRadius: 3, stroke: hair, justifyContent: 'center' }));
I(allowBtn, mono('allow-sender-label', 'ALLOW SENDER', { fontSize: 8, letterSpacing: 1.2, fill: s(INK) }));

bubble('msg-4', 'Did you get my code? verify now or it expires', '9:26', false, { padding: [2, 0, 0, 0] });

const composer = I(t, row('composer', { height: 70, padding: [0, 20], gap: 10, stroke: topHair, fill: s(PAPER) }));
const field = I(composer, row('composer-field', { width: 'fill_container', height: 42, padding: [0, 14], cornerRadius: 21, stroke: hair, fill: s(BONE) }));
I(field, txt('composer-placeholder', 'Message Areeba', { fontSize: 12, fill: s(FAINT) }));
const send = I(composer, { type: 'frame', name: 'send', width: 42, height: 42, layout: 'none', cornerRadius: 21, fill: s(INK) });
I(send, ic('send-icon', 'arrow-up', { x: 12, y: 12, width: 18, height: 18, fill: s(PAPER) }));

// 03 · CALLS ---------------------------------------------------------------
const c = phone('03 Calls', 860, 0);
screenHeader(c, 'Calls', 'TODAY · 4 CALLS · 1 BLOCKED', 'BLOCK 4');

const callRow = (name, initials, who, detail, time, kind, filled, blocked) => {
  const r = I(c, row(name, { height: 76, padding: [0, 24], gap: 14 }));
  avatar(r, name + '-avatar', initials, filled);
  const col = I(r, fr(name + '-stack', { width: 'fill_container', gap: 3 }));
  const nm = I(col, row(name + '-name-row', { width: 'fit_content', gap: 6 }));
  I(nm, txt(name + '-name', who, { fontSize: 15, fontWeight: 600, letterSpacing: -0.2, fill: s(blocked ? MUT : INK) }));
  if (blocked) I(nm, mono(name + '-blocked-tag', 'BLOCKED', { fontSize: 7, letterSpacing: 1.2, fill: s(INK), textAlign: 'center' }));
  const meta2 = I(col, row(name + '-meta-row', { width: 'fit_content', gap: 5 }));
  I(meta2, ic(name + '-dir', kind === 'out' ? 'arrow-up-right' : kind === 'missed' ? 'phone-missed' : 'arrow-down-left', { width: 11, height: 11, fill: s(FAINT) }));
  I(meta2, txt(name + '-detail', detail, { fontSize: 11, fill: s(MUT) }));
  I(r, mono(name + '-time', time, { fontSize: 9, letterSpacing: 0.4, fill: s(FAINT) }));
  I(c, rule(name + '-rule'));
};
callRow('c-1', 'AR', 'Areeba Raza', 'Incoming · 4m 12s', '9:31', 'in', false, false);
callRow('c-2', 'UM', 'Unknown · 0300 7712 008', 'Missed · no answer', '8:47', 'missed', false, true);
callRow('c-3', 'HM', 'Hina Malik', 'Outgoing · 1m 06s', 'Yesterday', 'out', false, false);
callRow('c-4', 'BK', 'Bank Alfalah', 'Incoming · 0m 21s', 'Tue', 'in', true, true);
callRow('c-5', 'ZA', 'Zainab Ali', 'Missed · no answer', 'Sun', 'missed', false, false);

const dialFab = I(c, row('dial-bar', { height: 76, padding: [0, 24], gap: 10, fill: s(BONE), justifyContent: 'center' }));
const dial = I(dialFab, row('dial-button', { height: 52, cornerRadius: 26, fill: s(INK), justifyContent: 'center', gap: 9, padding: [0, 26], width: 'fill_container' }));
I(dial, ic('dial-icon', 'phone', { width: 17, height: 17, fill: s(PAPER) }));
I(dial, mono('dial-label', 'DIAL PAD', { fontSize: 10, letterSpacing: 1.6, fill: s(PAPER) }));

// 04 · CONTACTS ------------------------------------------------------------
const k = phone('04 Contacts', 1290, 2);
screenHeader(k, 'Contacts', '38 SAVED · 3 BLOCKED', 'BLOCK 4');

const search = I(k, fr('search-wrap', { padding: [14, 24, 12, 24] }));
const sfield = I(search, row('search-field', { height: 44, padding: [0, 14], gap: 9, cornerRadius: 6, stroke: hair, fill: s(PAPER) }));
I(sfield, ic('search-icon', 'search', { width: 15, height: 15, fill: s(FAINT) }));
I(sfield, txt('search-placeholder', 'Search name, number, or rule', { fontSize: 12, fill: s(FAINT) }));

const contact = (name, initials, who, sub, filled, blocked) => {
  const r = I(k, row(name, { height: 66, padding: [0, 24], gap: 13 }));
  avatar(r, name + '-avatar', initials, filled);
  const col = I(r, fr(name + '-stack', { width: 'fill_container', gap: 2 }));
  I(col, txt(name + '-name', who, { fontSize: 14, fontWeight: 600, fill: s(blocked ? MUT : INK) }));
  I(col, mono(name + '-sub', sub, { fontSize: 9, letterSpacing: 0.6, fill: s(FAINT) }));
  if (blocked) I(r, mono(name + '-state', 'MUTED', { fontSize: 8, letterSpacing: 1.2, fill: s(INK) }));
  else I(r, ic(name + '-chevron', 'chevron-right', { width: 16, height: 16, fill: s(FAINT) }));
  I(k, rule(name + '-rule'));
};
contact('k-1', 'AR', 'Areeba Raza', 'ARE · +92 300 4481 902', false, false);
contact('k-2', 'HM', 'Hina Malik', 'HINA · +92 321 8890 114', true, false);
contact('k-3', 'HN', 'Hina Naz', 'HNA · +92 333 2010 771', false, false);
contact('k-4', 'SM', 'Sami Khan', 'SAMI · +92 345 7781 002', false, false);
contact('k-5', 'ZA', 'Zainab Ali', 'ZAIN · +92 302 5567 310', false, false);
contact('k-6', 'BK', 'Bank Alfalah', 'BANK · 2244', false, true);
contact('k-7', 'PC', 'Pakistan Code', 'PCOD · 8555', true, true);

const addRow = I(k, row('add-contact', { height: 60, padding: [0, 24], gap: 9, fill: [] }));
I(addRow, ic('add-icon', 'plus', { width: 16, height: 16, fill: s(INK) }));
I(addRow, mono('add-label', 'NEW CONTACT', { fontSize: 9, letterSpacing: 1.4, fill: s(INK) }));

// 05 · BLOCKLIST -----------------------------------------------------------
const b = phone('05 Blocking', 1720, 1);
// Taller than the other frames: the rules, keyword chips and allowlist need the room.
doc.children[doc.children.length - 1].height = 900;
const bh = I(b, fr('block-header', { padding: [18, 24, 12, 24], gap: 4 }));
I(bh, mono('block-eyebrow', 'RULES', { fontSize: 9, letterSpacing: 2, fill: s(MUT) }));
I(bh, txt('block-title', 'Blocking', { fontSize: 34, fontWeight: 700, letterSpacing: -1.4, lineHeight: 1 }));
I(bh, txt('block-lead', 'Messages and calls are matched against both lists. Names win before keywords.', {
  fontSize: 12, fill: s(MUT), lineHeight: 1.5, textGrowth: 'fixed-width', width: 'fill_container', padding: [4, 0, 0, 0],
}));
I(b, rule('block-rule-0'));

const seg = I(b, row('segments', { height: 48, padding: [0, 24], gap: 8 }));
const segOne = I(seg, row('seg-names', { width: 'fill_container', height: 34, cornerRadius: 4, fill: s(INK), justifyContent: 'center' }));
I(segOne, mono('seg-names-label', 'NAMES · 3', { fontSize: 9, letterSpacing: 1.4, fill: s(PAPER) }));
const segTwo = I(seg, row('seg-keywords', { width: 'fill_container', height: 34, cornerRadius: 4, stroke: hair, justifyContent: 'center' }));
I(segTwo, mono('seg-keywords-label', 'KEYWORDS · 5', { fontSize: 9, letterSpacing: 1.4, fill: s(INK) }));

const addBox = I(b, fr('add-rule', { padding: [14, 24, 10, 24], gap: 7 }));
I(addBox, mono('add-rule-label', 'BLOCK A NAME', { fontSize: 8, letterSpacing: 1.6, fill: s(FAINT) }));
const addField = I(addBox, row('add-rule-field', { height: 46, padding: [0, 14], gap: 8, cornerRadius: 5, stroke: { thickness: 1, fill: s(INK) }, fill: s(PAPER), justifyContent: 'space_between' }));
I(addField, txt('add-rule-placeholder', 'Name, or a word inside a message', { fontSize: 12, fill: s(FAINT), textGrowth: 'fixed-width', width: 'fill_container' }));
const addGo = I(addField, { type: 'frame', name: 'add-go', width: 28, height: 28, layout: 'none', cornerRadius: 14, fill: s(INK) });
I(addGo, ic('add-go-icon', 'plus', { x: 7, y: 7, width: 14, height: 14, fill: s(PAPER) }));

const nameList = I(b, fr('name-rules', { gap: 0 }));
const nameLabel = I(nameList, row('name-rules-wrap', { height: 34, padding: [0, 24] }));
I(nameLabel, mono('name-rules-label', 'BLOCKED NAMES', { fontSize: 8, letterSpacing: 1.8, fill: s(FAINT) }));
const ruleRow = (name, value, sub, isKeyword) => {
  const r = I(nameList, row(name, { height: 60, padding: [0, 24], gap: 12 }));
  I(r, ic(name + '-icon', isKeyword ? 'type' : 'user-x', { width: 15, height: 15, fill: s(FAINT) }));
  const col = I(r, fr(name + '-stack', { width: 'fill_container', gap: 2 }));
  I(col, txt(name + '-value', value, { fontSize: 13, fontWeight: 600 }));
  I(col, mono(name + '-sub', sub, { fontSize: 8, letterSpacing: 0.8, fill: s(FAINT) }));
  I(r, ic(name + '-remove', 'x', { width: 15, height: 15, fill: s(MUT) }));
  I(nameList, rule(name + '-rule'));
};
ruleRow('r-1', 'Bank Alfalah', 'MATCHES 1 THREAD · 2 CALLS', false);
ruleRow('r-2', 'Pakistan Code', 'MATCHES 1 THREAD', false);
ruleRow('r-3', 'Unknown · 0300 7712 008', 'NUMBER RULE · MATCHES 1 CALL', false);

const kwList = I(b, fr('keyword-rules', { gap: 0, padding: [10, 0, 0, 0] }));
const kwLabel = I(kwList, row('kw-label-wrap', { height: 34, padding: [0, 24] }));
I(kwLabel, mono('kw-label', 'KEYWORD RULES', { fontSize: 8, letterSpacing: 1.8, fill: s(FAINT) }));
const kwChip = (parent, name, word, hits) => {
  const chip = I(parent, row(name, { width: 'fit_content', height: 34, padding: [0, 12], gap: 8, cornerRadius: 17, stroke: hair, fill: s(PAPER) }));
  I(chip, mono(name + '-word', word, { fontSize: 9, letterSpacing: 1.1, fill: s(INK) }));
  I(chip, mono(name + '-hits', hits, { fontSize: 8, fill: s(FAINT) }));
  return chip;
};
const chipRow1 = I(kwList, row('kw-row-1', { padding: [0, 24], gap: 7, height: 46 }));
kwChip(chipRow1, 'kw-1', 'otp', '12');
kwChip(chipRow1, 'kw-2', 'verify', '8');
kwChip(chipRow1, 'kw-3', 'prize', '3');
const chipRow2 = I(kwList, row('kw-row-2', { padding: [0, 24], gap: 7, height: 44 }));
kwChip(chipRow2, 'kw-4', 'bit.ly', '2');
kwChip(chipRow2, 'kw-5', 'loan approved', '1');

const allowList = I(b, fr('allowed-senders', { gap: 0, padding: [6, 0, 0, 0] }));
I(allowList, rule('allowed-rule-top'));
const allowLabel = I(allowList, row('allowed-label-wrap', { height: 34, padding: [0, 24] }));
I(allowLabel, mono('allowed-label', 'ALLOWED SENDERS · 1', { fontSize: 8, letterSpacing: 1.8, fill: s(FAINT) }));
const allowRow = I(allowList, row('allowed-row', { height: 52, padding: [0, 24], gap: 12 }));
I(allowRow, ic('allowed-icon', 'hash', { width: 15, height: 15, fill: s(FAINT) }));
const allowStack = I(allowRow, fr('allowed-stack', { width: 'fill_container', gap: 2 }));
I(allowStack, txt('allowed-value', '0300 7712 008', { fontSize: 13, fontWeight: 600 }));
I(allowStack, mono('allowed-sub', 'KEYWORDS SKIP THIS SENDER', { fontSize: 8, letterSpacing: 0.8, fill: s(FAINT) }));
I(allowRow, ic('allowed-remove', 'x', { width: 15, height: 15, fill: s(MUT) }));
I(allowList, rule('allowed-rule-bottom'));

const foot = I(b, fr('block-foot', { padding: [8, 24, 0, 24], gap: 5 }));
I(foot, rule('foot-rule'));
const footRow = I(foot, row('foot-row', { height: 54, justifyContent: 'space_between' }));
const fl = I(footRow, fr('foot-left', { width: 'fill_container', gap: 1 }));
I(fl, txt('foot-title', 'Also hide matched calls', { fontSize: 12, fontWeight: 500 }));
I(fl, mono('foot-sub', 'SILENCES RINGING FOR NAME MATCHES', { fontSize: 8, letterSpacing: 0.8, fill: s(FAINT) }));
const toggle = I(footRow, { type: 'frame', name: 'toggle', width: 44, height: 26, layout: 'none', cornerRadius: 13, fill: s(INK) });
I(toggle, { type: 'ellipse', name: 'toggle-knob', x: 21, y: 3, width: 20, height: 20, fill: s(PAPER) });

// 06 · DIAL PAD ------------------------------------------------------------
function barePhone(name, x, tabIndex) {
  const f = I(null, {
    type: 'frame', name, x, y: 0, width: 390, height: 844,
    layout: 'vertical', gap: 0, fill: s(BONE),
  });
  doc.children.push(f);
  const bar = I(f, row('status-bar', { height: 56, padding: [0, 26], justifyContent: 'space_between' }));
  I(bar, mono('clock', '9:41', { fontSize: 12, fontWeight: 600, fill: s(INK), letterSpacing: 0.4 }));
  const right = I(bar, row('status-icons', { width: 'fit_content', gap: 5 }));
  I(right, ic('sig', 'signal', { width: 14, height: 14, fill: s(INK) }));
  I(right, ic('wifi', 'wifi', { width: 14, height: 14, fill: s(INK) }));
  I(right, ic('battery', 'battery-medium', { width: 16, height: 16, fill: s(INK) }));
  const body = I(f, { type: 'frame', name: 'body', width: 'fill_container', height: 'fill_container', layout: 'vertical', gap: 0, fill: [] });
  if (tabIndex !== undefined) {
    const tabs = I(f, row('tab-bar', { height: 74, padding: [0, 4, 10, 4], stroke: topHair, fill: s(PAPER), justifyContent: 'space_between' }));
    const defs = [['calls', 'CALLS', 'phone'], ['messages', 'MESSAGES', 'message-square'], ['contacts', 'CONTACTS', 'users']];
    for (let i = 0; i < defs.length; i++) {
      const active = i === tabIndex;
      const t = I(tabs, fr('tab-' + i, { width: 'fill_container', gap: 5, alignItems: 'center', justifyContent: 'center' }));
      I(t, ic('tab-' + i + '-icon', defs[i][2], { width: 19, height: 19, fill: s(active ? INK : FAINT) }));
      I(t, mono('tab-' + i + '-label', defs[i][1], { fontSize: 8, letterSpacing: 1.4, fill: s(active ? INK : FAINT) }));
      if (active) I(t, slab('tab-' + i + '-mark', INK, { width: 16, height: 2 }));
    }
  }
  return body;
}

const dp = barePhone('06 Dial pad', 2150);
const dpHead = I(dp, fr('dial-head', { padding: [16, 24, 10, 24], gap: 2 }));
const dpHeadTop = I(dpHead, row('dial-head-top', { justifyContent: 'space_between', alignItems: 'center' }));
I(dpHeadTop, txt('dial-title', 'New call', { fontSize: 34, fontWeight: 700, letterSpacing: -1.4, lineHeight: 1 }));
const dpClose = I(dpHeadTop, { type: 'frame', name: 'dial-close', width: 30, height: 30, layout: 'none', cornerRadius: 3, stroke: hair });
I(dpClose, ic('dial-close-icon', 'x', { x: 8, y: 8, width: 14, height: 14, fill: s(INK) }));
I(dpHead, mono('dial-meta', 'TYPE OR PASTE A NUMBER', { fontSize: 9, letterSpacing: 1.6, fill: s(MUT) }));
I(dp, rule('dial-rule-top'));

const dpNumber = I(dp, row('number-line', { height: 86, padding: [0, 24], justifyContent: 'space_between' }));
I(dpNumber, txt('number-value', '0300 7712 008', { fontFamily: MONO, fontSize: 26, letterSpacing: 1.4, fontWeight: 600, fill: s(INK) }));
I(dpNumber, ic('number-backspace', 'delete', { width: 20, height: 20, fill: s(MUT) }));
I(dp, rule('dial-rule-number'));

const dpWarn = I(dp, row('blocked-warning', { padding: [12, 24], gap: 8, fill: s(WASH) }));
I(dpWarn, ic('blocked-warning-icon', 'shield-alert', { width: 14, height: 14, fill: s(INK) }));
I(dpWarn, txt('blocked-warning-text', 'This number is blocked by a number rule', { fontSize: 11, fontWeight: 500, fill: s(INK), textGrowth: 'fixed-width', width: 'fill_container' }));
I(dpWarn, mono('blocked-warning-action', 'UNBLOCK', { fontSize: 8, letterSpacing: 1.2, fill: s(INK) }));

const keypad = I(dp, fr('keypad', { padding: [10, 24, 0, 24], gap: 4, height: 'fill_container' }));
const keys = [
  ['1', ''], ['2', 'ABC'], ['3', 'DEF'],
  ['4', 'GHI'], ['5', 'JKL'], ['6', 'MNO'],
  ['7', 'PQRS'], ['8', 'TUV'], ['9', 'WXYZ'],
  ['*', ''], ['0', '+'], ['#', ''],
];
for (let r = 0; r < 4; r++) {
  const rowNode = I(keypad, row('key-row-' + r, { height: 70, gap: 4 }));
  for (let c = 0; c < 3; c++) {
    const k = keys[r * 3 + c];
    const cell = I(rowNode, fr('key-' + k[0], { width: 'fill_container', height: 70, gap: 1, alignItems: 'center', justifyContent: 'center', cornerRadius: 8 }));
    I(cell, txt('key-' + k[0] + '-digit', k[0], { fontSize: 26, fontWeight: 500, lineHeight: 1.1, fill: s(INK) }));
    if (k[1]) I(cell, mono('key-' + k[0] + '-letters', k[1], { fontSize: 7, letterSpacing: 1.4, fill: s(FAINT) }));
  }
}

const dpActions = I(dp, fr('dial-actions', { padding: [8, 24, 22, 24], gap: 10 }));
const dpCall = I(dpActions, row('call-button', { height: 56, cornerRadius: 28, fill: s(INK), justifyContent: 'center', gap: 10 }));
I(dpCall, ic('call-icon', 'phone', { width: 18, height: 18, fill: s(PAPER) }));
I(dpCall, mono('call-label', 'CALL', { fontSize: 11, letterSpacing: 2, fill: s(PAPER) }));
const dpSecondary = I(dpActions, row('dial-secondary', { height: 40, gap: 10 }));
const dpAdd = I(dpSecondary, row('add-to-contacts', { width: 'fill_container', height: 40, cornerRadius: 4, stroke: hair, justifyContent: 'center', gap: 7 }));
I(dpAdd, ic('add-to-contacts-icon', 'user-plus', { width: 14, height: 14, fill: s(INK) }));
I(dpAdd, mono('add-to-contacts-label', 'SAVE CONTACT', { fontSize: 8, letterSpacing: 1.2, fill: s(INK) }));
const dpPaste = I(dpSecondary, row('paste-number', { width: 'fill_container', height: 40, cornerRadius: 4, stroke: hair, justifyContent: 'center', gap: 7 }));
I(dpPaste, ic('paste-number-icon', 'clipboard', { width: 14, height: 14, fill: s(INK) }));
I(dpPaste, mono('paste-number-label', 'PASTE', { fontSize: 8, letterSpacing: 1.2, fill: s(INK) }));

// 07 · SETTINGS ------------------------------------------------------------
const st = barePhone('07 Settings', 2580, 2);
st.height = 1010; // settings is the one scrolling-length board; never clip a row
const stHead = I(st, fr('settings-head', { padding: [16, 24, 14, 24], gap: 3 }));
const stTop = I(stHead, row('settings-top', { justifyContent: 'space_between', alignItems: 'center' }));
I(stTop, txt('settings-title', 'Settings', { fontSize: 34, fontWeight: 700, letterSpacing: -1.4, lineHeight: 1 }));
I(stTop, ic('settings-gear', 'settings', { width: 18, height: 18, fill: s(MUT) }));
I(stHead, mono('settings-meta', 'TRINITY 0.1.0', { fontSize: 9, letterSpacing: 1.6, fill: s(MUT) }));
I(st, rule('settings-rule-top'));

function section(parent, name, label) {
  const wrap = I(parent, row(name, { height: 36, padding: [0, 24], fill: s(BONE) }));
  I(wrap, mono(name + '-label', label, { fontSize: 8, letterSpacing: 2, fill: s(FAINT) }));
  return wrap;
}

function settingsRow(parent, name, label, value, opts) {
  const o = opts || {};
  const r = I(parent, row(name, { height: 62, padding: [0, 24], gap: 12 }));
  if (o.icon) I(r, ic(name + '-icon', o.icon, { width: 16, height: 16, fill: s(o.iconColor || MUT) }));
  const col = I(r, fr(name + '-stack', { width: 'fill_container', gap: 2 }));
  I(col, txt(name + '-label', label, { fontSize: 13, fontWeight: 600, fill: s(o.muted ? MUT : INK) }));
  if (o.sub) I(col, mono(name + '-sub', o.sub, { fontSize: 8, letterSpacing: 0.8, fill: s(FAINT) }));
  if (value) I(r, mono(name + '-value', value, { fontSize: 9, letterSpacing: 0.8, fill: s(MUT) }));
  if (o.chevron) I(r, ic(name + '-chevron', 'chevron-right', { width: 15, height: 15, fill: s(FAINT) }));
  if (o.toggle) {
    const t = I(r, { type: 'frame', name: name + '-toggle', width: 44, height: 26, layout: 'none', cornerRadius: 13, fill: s(o.toggleOn ? INK : WASH) });
    I(t, { type: 'ellipse', name: name + '-knob', x: o.toggleOn ? 21 : 3, y: 3, width: 20, height: 20, fill: s(PAPER) });
  }
  I(parent, rule(name + '-rule'));
  return r;
}

section(st, 'sec-messaging', 'MESSAGING');
settingsRow(st, 'st-sms', 'Default SMS app', 'ON', { icon: 'message-square', sub: 'TRINITY RECEIVES AND FILES TEXTS' });
settingsRow(st, 'st-sim', 'Send from', 'SIM 1', { icon: 'smartphone', chevron: true });
settingsRow(st, 'st-quiet', 'Mute unknown senders', null, { icon: 'bell-off', toggle: true, toggleOn: false });

section(st, 'sec-calls', 'CALLS');
settingsRow(st, 'st-dialer', 'Set as default dialer', 'NOT SET', { icon: 'phone', chevron: true, muted: true });
settingsRow(st, 'st-hidecalls', 'Hide matched calls', null, { icon: 'shield', toggle: true, toggleOn: true });

section(st, 'sec-block', 'BLOCKING');
settingsRow(st, 'st-names', 'Name & number rules', '3', { icon: 'user-x', chevron: true });
settingsRow(st, 'st-keywords', 'Keyword rules', '5', { icon: 'type', chevron: true });
settingsRow(st, 'st-held', 'Messages held in quarantine', '12', { icon: 'archive', chevron: true });

section(st, 'sec-look', 'APPEARANCE');
settingsRow(st, 'st-theme', 'Paper & ink', 'FIXED', { icon: 'circle-half', sub: 'NO GRADIENTS · NO ACCENT HUE' });
settingsRow(st, 'st-density', 'Row borders', 'ON', { icon: 'rows-3', sub: 'KEEPS ADJACENT CONTACTS APART' });

section(st, 'sec-about', 'ABOUT');
settingsRow(st, 'st-version', 'Version', '0.1.0', { icon: 'info' });
const stFoot = I(st, fr('settings-foot', { padding: [16, 24], gap: 4 }));
I(stFoot, txt('settings-foot-text', 'Trinity replaces the Phone, Messages and Contacts apps with one surface. Blocking rules never leave this device.', {
  fontSize: 10, fill: s(FAINT), lineHeight: 1.6, textGrowth: 'fixed-width', width: 'fill_container',
}));

// 08 · IN CALL -------------------------------------------------------------
const call = barePhone('08 In call', 3010);
const clHead = I(call, fr('call-head', { padding: [16, 24, 12, 24], gap: 2 }));
const clTop = I(clHead, row('call-head-top', { justifyContent: 'space_between', alignItems: 'center' }));
I(clTop, mono('call-state', 'IN CALL · HANDSET', { fontSize: 9, letterSpacing: 1.6, fill: s(MUT) }));
I(clTop, mono('call-time', '00:42', { fontSize: 10, letterSpacing: 1.4, fill: s(INK) }));
I(call, rule('call-rule-top'));

const clBody = I(call, fr('call-body', { padding: [24, 24, 0, 24], gap: 16, alignItems: 'center', justifyContent: 'center', height: 'fill_container' }));
const clAvatar = I(clBody, { type: 'frame', name: 'call-avatar', width: 88, height: 88, layout: 'none', cornerRadius: 44, fill: s(INK) });
I(clAvatar, txt('call-avatar-initials', 'SI', { x: 0, y: 28, width: 88, fontSize: 30, fontWeight: 600, textAlign: 'center', fill: s(PAPER) }));
const clNames = I(clBody, fr('call-name-stack', { gap: 3, alignItems: 'center' }));
I(clNames, txt('call-name', 'Sana Iqbal', { fontSize: 26, fontWeight: 700, letterSpacing: -1, lineHeight: 1.1 }));
I(clNames, mono('call-number', '0300 7712 008', { fontSize: 10, letterSpacing: 1.4 }));
I(clNames, mono('call-status', 'CONNECTED', { fontSize: 8, letterSpacing: 1.8, fill: s(FAINT) }));

const clFlagRow = I(clBody, row('call-flag-row', { width: 'fit_content', padding: [6, 10], gap: 6, cornerRadius: 4, fill: s(WASH) }));
I(clFlagRow, ic('call-flag-icon', 'shield', { width: 12, height: 12, fill: s(INK) }));
I(clFlagRow, mono('call-flag-label', 'NUMBER RULE · 0300 7712 008', { fontSize: 8, letterSpacing: 1.1, fill: s(INK) }));

const clControls = I(call, fr('call-controls', { padding: [0, 24, 14, 24], gap: 10 }));
I(clControls, rule('call-controls-rule'));
const clCtlRow = I(clControls, row('call-controls-row', { gap: 10, padding: [14, 0, 0, 0] }));
const callTile = (name, label, glyph, active) => {
  const t = I(clCtlRow, fr(name, { width: 'fill_container', height: 64, gap: 6, alignItems: 'center', justifyContent: 'center', cornerRadius: 6, stroke: hair, fill: s(active ? INK : PAPER) }));
  I(t, ic(name + '-icon', glyph, { width: 16, height: 16, fill: s(active ? PAPER : INK) }));
  I(t, mono(name + '-label', label, { fontSize: 7, letterSpacing: 1.2, fill: s(active ? PAPER : MUT) }));
};
callTile('ctl-mute', 'MUTE', 'mic-off', true);
callTile('ctl-speaker', 'SPEAKER', 'volume-2', false);
callTile('ctl-keypad', 'KEYPAD', 'grid-3x3', false);
const clEnd = I(clControls, row('end-call', { height: 56, cornerRadius: 28, fill: s(INK), justifyContent: 'center', gap: 10 }));
I(clEnd, ic('end-call-icon', 'phone-off', { width: 18, height: 18, fill: s(PAPER) }));
I(clEnd, mono('end-call-label', 'END CALL', { fontSize: 11, letterSpacing: 2, fill: s(PAPER) }));

// 09 · NEW CONTACT ---------------------------------------------------------
const nc = barePhone('09 New contact', 3440);
const ncHead = I(nc, fr('new-head', { padding: [16, 24, 14, 24], gap: 3 }));
const ncTop = I(ncHead, row('new-head-top', { justifyContent: 'space_between', alignItems: 'center' }));
I(ncTop, mono('new-eyebrow', 'CONTACTS', { fontSize: 9, letterSpacing: 2, fill: s(MUT) }));
const ncClose = I(ncTop, { type: 'frame', name: 'new-close', width: 30, height: 30, layout: 'none', cornerRadius: 3, stroke: hair });
I(ncClose, ic('new-close-icon', 'x', { x: 8, y: 8, width: 14, height: 14, fill: s(INK) }));
I(ncHead, txt('new-title', 'New contact', { fontSize: 34, fontWeight: 700, letterSpacing: -1.4, lineHeight: 1 }));
const ncLead = I(ncHead, fr('new-lead', { padding: [6, 0, 0, 0], width: 'fill_container' }));
I(ncLead, txt('new-lead-text', 'Saved to the phone address book, so every app sees it.', {
  fontSize: 12, fill: s(MUT), lineHeight: 1.5, textGrowth: 'fixed-width', width: 'fill_container',
}));
I(nc, rule('new-rule-top'));

const ncIdentity = I(nc, row('new-identity', { padding: [20, 24], gap: 14, alignItems: 'center' }));
const ncAvatar = I(ncIdentity, { type: 'frame', name: 'new-avatar', width: 56, height: 56, layout: 'none', cornerRadius: 28, fill: s(INK) });
I(ncAvatar, txt('new-avatar-initials', 'NI', { x: 0, y: 17, width: 56, fontSize: 19, fontWeight: 600, textAlign: 'center', fill: s(PAPER) }));
const ncIdStack = I(ncIdentity, fr('new-id-stack', { width: 'fill_container', gap: 2 }));
I(ncIdStack, txt('new-id-name', 'Nadia Iqbal', { fontSize: 18, fontWeight: 600 }));
I(ncIdStack, mono('new-id-number', '0300 7712 008', { fontSize: 9, letterSpacing: 1.2 }));
I(nc, rule('new-rule-id'));

const newField = (name, label, value, hint) => {
  const f = I(nc, fr(name, { padding: [14, 24], gap: 6 }));
  I(f, mono(name + '-label', label, { fontSize: 8, letterSpacing: 1.8, fill: s(FAINT) }));
  I(f, txt(name + '-value', value || hint, { fontSize: 15, fontWeight: value ? 500 : 400, fill: s(value ? INK : FAINT) }));
  I(nc, rule(name + '-rule'));
};
newField('field-name', 'NAME', 'Nadia Iqbal');
newField('field-phone', 'PHONE', '0300 7712 008');
newField('field-email', 'EMAIL', null, 'Optional');

settingsRow(nc, 'new-block', 'Block this contact too', null, {
  icon: 'shield', toggle: true, toggleOn: false, sub: 'ADDS A NAME RULE AFTER SAVING',
});

I(nc, fr('new-spacer', { height: 'fill_container' }));
const ncActions = I(nc, fr('new-actions', { padding: [0, 24, 24, 24], gap: 10 }));
const ncSave = I(ncActions, row('save-contact', { height: 54, cornerRadius: 5, fill: s(INK), justifyContent: 'center', gap: 9 }));
I(ncSave, ic('save-contact-icon', 'user-plus', { width: 16, height: 16, fill: s(PAPER) }));
I(ncSave, mono('save-contact-label', 'SAVE CONTACT', { fontSize: 10, letterSpacing: 1.8, fill: s(PAPER) }));
const ncCancel = I(ncActions, row('cancel-contact', { height: 44, cornerRadius: 5, stroke: hair, justifyContent: 'center' }));
I(ncCancel, mono('cancel-contact-label', 'CANCEL', { fontSize: 9, letterSpacing: 1.8, fill: s(INK) }));

require('fs').writeFileSync('C:/Users/Sarim/repos/trinity/design/trinity.op', JSON.stringify(doc, null, 2));
console.log('frames:', doc.children.map((f) => f.name).join(' | '));
console.log('nodes:', seq);
