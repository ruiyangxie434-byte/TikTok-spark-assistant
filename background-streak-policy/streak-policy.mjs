const DAY_MS = 86_400_000;
const BEIJING_OFFSET_MS = 8 * 3_600_000;
const STATES = new Map([[1, 'done'], [2, 'pending'], [3, 'recover'], [4, 'inactive']]);

/** Interpret only one current server window; malformed or ambiguous data stays unknown. */
export function classifyOfficialStreak(raw, nowSeconds) {
  if (typeof raw !== 'string' || !Number.isFinite(nowSeconds)) return 'unknown';
  let data;
  try { data = JSON.parse(raw); } catch { return 'unknown'; }
  if (!data || !Array.isArray(data.flame_infos)) return 'unknown';
  let current;
  for (const window of data.flame_infos) {
    if (!window || !Number.isSafeInteger(window.start) || !Number.isSafeInteger(window.end)
        || window.end <= window.start) return 'unknown';
    if (window.start <= nowSeconds && nowSeconds < window.end) {
      if (current) return 'unknown';
      current = window;
    }
  }
  return STATES.get(current?.state) ?? 'unknown';
}

function milliseconds(now) {
  if (!(now instanceof Date) || !Number.isFinite(now.getTime())) {
    throw new RangeError('A valid Date is required');
  }
  return now.getTime();
}

export function beijingDateKey(now = new Date()) {
  return new Date(milliseconds(now) + BEIJING_OFFSET_MS).toISOString().slice(0, 10);
}

/** First Beijing 22:00 strictly after now; exact 22:00 advances to the next day. */
export function nextBeijing22(now = new Date()) {
  const time = milliseconds(now);
  const dayStart = Math.floor((time + BEIJING_OFFSET_MS) / DAY_MS) * DAY_MS - BEIJING_OFFSET_MS;
  const todayAt22 = dayStart + 22 * 3_600_000;
  return new Date(todayAt22 > time ? todayAt22 : todayAt22 + DAY_MS);
}

export function streakAttemptKey(accountId, conversationId, now = new Date()) {
  for (const id of [accountId, conversationId]) {
    if (typeof id !== 'string' || !id.trim() || id !== id.trim()) {
      throw new TypeError('Stable account and conversation IDs are required');
    }
  }
  return JSON.stringify([accountId, conversationId, beijingDateKey(now)]);
}

/** Pure policy: the caller records an attempt before dispatching any message. */
export function decideStreakSend({ accountId, conversationId, raw, readSucceeded,
  hasExistingStreak, attemptedKeys, now = new Date() }) {
  const key = streakAttemptKey(accountId, conversationId, now);
  const state = readSucceeded === true ? classifyOfficialStreak(raw, now.getTime() / 1000) : 'unknown';
  let reason;
  if (readSucceeded !== true) reason = 'read-not-confirmed';
  else if (hasExistingStreak !== true) reason = 'relationship-not-confirmed';
  else if (state !== 'pending') reason = state;
  else if (!(attemptedKeys instanceof Set)) reason = 'attempt-history-unavailable';
  else if (attemptedKeys.has(key)) reason = 'already-attempted';
  else reason = 'pending';
  return { send: reason === 'pending', state, reason, key };
}
