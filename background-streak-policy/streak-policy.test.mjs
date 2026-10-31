import test from 'node:test';
import assert from 'node:assert/strict';
import { classifyOfficialStreak, beijingDateKey, nextBeijing22, streakAttemptKey,
  decideStreakSend } from './streak-policy.mjs';

const NOW = new Date('2026-10-27T14:00:00Z');
const SEC = NOW.getTime() / 1000;
const win = (state, start = SEC - 100, end = SEC + 100) => ({ start, end, state });
const raw = (...windows) => JSON.stringify({ flame_infos: windows });
const options = (extra = {}) => ({ accountId: 'account-a', conversationId: '0:1:10:20',
  raw: raw(win(2)), readSucceeded: true, hasExistingStreak: true,
  attemptedKeys: new Set(), now: NOW, ...extra });

test('classifies known states; unknown state codes are not coerced', () => {
  for (const [code, expected] of [[1, 'done'], [2, 'pending'], [3, 'recover'], [4, 'inactive']]) {
    assert.equal(classifyOfficialStreak(raw(win(code)), SEC), expected);
  }
  for (const code of [0, 5, -1, '2', null]) assert.equal(classifyOfficialStreak(raw(win(code)), SEC), 'unknown');
});

test('missing, malformed, stale and future data remain unknown', () => {
  for (const input of [undefined, null, {}, '', '{', 'null', '[]', '{}', '{"flame_infos":{}}', raw(),
    raw(win(2, SEC - 200, SEC - 100)), raw(win(2, SEC + 100, SEC + 200))]) {
    assert.equal(classifyOfficialStreak(input, SEC), 'unknown');
  }
  for (const time of [NaN, Infinity, '1793109600', undefined]) assert.equal(classifyOfficialStreak(raw(win(2)), time), 'unknown');
});

test('malformed interval bounds fail closed even beside a valid window', () => {
  for (const entry of [null, {}, { ...win(2), start: String(SEC - 100) }, win(2, SEC, SEC),
    win(2, SEC + 1, SEC), win(2, SEC - 0.5, SEC + 1), win(2, SEC, Number.MAX_SAFE_INTEGER + 1)]) {
    assert.equal(classifyOfficialStreak(raw(entry), SEC), 'unknown');
    assert.equal(classifyOfficialStreak(raw(win(2), entry), SEC), 'unknown');
  }
});

test('server intervals include start, exclude end and handle adjacent windows', () => {
  const input = raw(win(2, SEC, SEC + 10));
  assert.equal(classifyOfficialStreak(input, SEC - 0.001), 'unknown');
  assert.equal(classifyOfficialStreak(input, SEC), 'pending');
  assert.equal(classifyOfficialStreak(input, SEC + 9.999), 'pending');
  assert.equal(classifyOfficialStreak(input, SEC + 10), 'unknown');
  assert.equal(classifyOfficialStreak(raw(win(1, SEC - 10, SEC), win(2, SEC, SEC + 10)), SEC), 'pending');
});

test('multiple current windows are unknown, including identical duplicate windows', () => {
  assert.equal(classifyOfficialStreak(raw(win(1), win(2)), SEC), 'unknown');
  assert.equal(classifyOfficialStreak(raw(win(2), win(2)), SEC), 'unknown');
});

test('requires explicit fresh-read, relationship and attempt-history confirmation', () => {
  const input = options();
  assert.deepEqual(decideStreakSend(input), { send: true, state: 'pending', reason: 'pending',
    key: streakAttemptKey(input.accountId, input.conversationId, NOW) });
  assert.equal(input.attemptedKeys.size, 0);
  for (const value of [false, undefined, 'true', 1]) {
    assert.equal(decideStreakSend(options({ readSucceeded: value })).send, false);
    assert.equal(decideStreakSend(options({ hasExistingStreak: value })).send, false);
  }
  for (const value of [undefined, null, []]) {
    assert.equal(decideStreakSend(options({ attemptedKeys: value })).reason, 'attempt-history-unavailable');
  }
});

test('already done, recovery, inactive and unknown never send', () => {
  for (const state of [1, 3, 4, 9]) assert.equal(decideStreakSend(options({ raw: raw(win(state)) })).send, false);
  assert.equal(decideStreakSend(options({ raw: '{' })).send, false);
  assert.equal(decideStreakSend(options({ readSucceeded: false, raw: raw(win(1)) })).state, 'unknown');
});

test('an attempt blocks only the same account, conversation and Beijing day', () => {
  const attemptedKeys = new Set([streakAttemptKey('account-a', '0:1:10:20', NOW)]);
  assert.equal(decideStreakSend(options({ attemptedKeys })).reason, 'already-attempted');
  assert.equal(decideStreakSend(options({ attemptedKeys, accountId: 'account-b' })).send, true);
  assert.equal(decideStreakSend(options({ attemptedKeys, conversationId: '0:1:10:30' })).send, true);
  const yesterday = new Date('2026-10-26T14:00:00Z');
  assert.equal(decideStreakSend(options({ attemptedKeys: new Set([
    streakAttemptKey('account-a', '0:1:10:20', yesterday)]) })).send, true);
});

test('structured keys prevent delimiter collisions and require stable IDs', () => {
  assert.notEqual(streakAttemptKey('a:b', 'c', NOW), streakAttemptKey('a', 'b:c', NOW));
  for (const id of ['', ' ', ' a', 12, undefined]) {
    assert.throws(() => streakAttemptKey(id, 'conversation', NOW), TypeError);
    assert.throws(() => streakAttemptKey('account', id, NOW), TypeError);
  }
});

test('Beijing dates and attempt keys switch at UTC 16:00', () => {
  const before = new Date('2026-10-27T15:59:59.999Z'), after = new Date('2026-10-27T16:00:00Z');
  assert.equal(beijingDateKey(before), '2026-10-27');
  assert.equal(beijingDateKey(after), '2026-10-28');
  assert.notEqual(streakAttemptKey('a', 'c', before), streakAttemptKey('a', 'c', after));
});

test('next 22:00 is strictly future across exact time, midnight and year boundaries', () => {
  for (const [input, expected] of [
    ['2026-10-27T13:59:59.999Z', '2026-10-27T14:00:00.000Z'],
    ['2026-10-27T14:00:00Z', '2026-10-28T14:00:00.000Z'],
    ['2026-10-27T14:00:00.001Z', '2026-10-28T14:00:00.000Z'],
    ['2026-10-27T16:00:00Z', '2026-10-28T14:00:00.000Z'],
    ['2026-12-31T14:00:00Z', '2027-01-01T14:00:00.000Z'],
  ]) assert.equal(nextBeijing22(new Date(input)).toISOString(), expected);
  assert.throws(() => beijingDateKey(new Date(NaN)), RangeError);
  assert.throws(() => nextBeijing22(new Date(NaN)), RangeError);
});

test('changing host system timezone does not affect dates, scheduling or keys', () => {
  const previous = process.env.TZ;
  try {
    const results = ['UTC', 'America/Los_Angeles', 'Pacific/Auckland'].map(zone => {
      process.env.TZ = zone;
      const now = new Date('2026-10-27T16:00:00Z');
      return { offset: now.getTimezoneOffset(), date: beijingDateKey(now),
        next: nextBeijing22(now).toISOString(), key: streakAttemptKey('a', 'c', now) };
    });
    assert.equal(new Set(results.map(result => result.offset)).size, 3);
    const projected = results.map(({ offset, ...result }) => result);
    assert.deepEqual(projected[0], projected[1]);
    assert.deepEqual(projected[1], projected[2]);
    assert.equal(projected[0].date, '2026-10-28');
    assert.equal(projected[0].next, '2026-10-28T14:00:00.000Z');
  } finally {
    if (previous === undefined) delete process.env.TZ;
    else process.env.TZ = previous;
  }
});
