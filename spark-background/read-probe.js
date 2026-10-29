if (globalThis.location?.origin !== 'https://www.douyin.com') return { ready: false };
const map = globalThis.conversationStore?.conversationMap;
if (!map || typeof map.values !== 'function') return { ready: false };
const result = { ready: true, source: 'page-memory', serverRefreshConfirmed: false,
  privateCount: 0, candidateCount: 0, done: 0, pending: 0, recover: 0, inactive: 0, unknown: 0, truncated: false };
let seen = 0;
for (const conversation of map.values()) {
  if (++seen > 2000) { result.truncated = true; break; }
  if (conversation?.type !== 1) continue;
  result.privateCount += 1;
  const raw = conversation.coreInfo?.ext?.['a:consecutive_chat_data'];
  if (typeof raw !== 'string') continue;
  result.candidateCount += 1;
  const state = raw.length < 20000 ? classifyOfficialStreak(raw, Date.now() / 1000) : 'unknown';
  result[state] += 1;
}
result.ready = result.privateCount > 0;
return result;


