# Version 0.7: skip if the user already sent today

Verified on 2026-11-01, OnePlus 13. Package app.local.spark.background, versionCode 7, versionName 0.7-self-sent-guard.

Before any pending-streak dispatch, both the single-target worker and nightly worker now read fresh server message history. Authenticated platform/IM sender IDs identify outgoing messages. Beijing midnight defines today. An outgoing user message today skips the conversation even if the official streak is still pending. Official done remains a separate state; this rule never claims the peer has replied or the streak has renewed.

History is read with existing private conversation IDs, at most five pages of 50 messages. Pages can be oldest-first within each older-history page. Pagination must move backwards, and a no-self-send result requires history to reach before midnight or the server to report no more history. Missing, inconsistent, failed or incomplete history skips dispatch. System tips, recalled and deleted records do not prove a user-authored send. Message bodies are not persisted by this guard. Existing attempt-before-dispatch durable deduplication remains in force.

Nightly dry runs and live jobs use the same shared runner and history guard. Dry runs never claim an attempt or call the sender.

## Validation

- The original duplicate-send behavior was reproduced by a failing regression test before the fix.
- 51 offline Node tests passed, including user-only/both-sides/peer-only messages, yesterday versus today, Beijing midnight, pagination order, incomplete history, read failures, dry runs and durable deduplication.
- APK compiled, v3 signature verified, and installed with the existing signing key, preserving the helper session and settings.
- Real-account read-only nightly rehearsal: 42 discovered conversations, 17 official-done skips, 8 already-sent-today skips, 1 other skip, 16 unknown/non-streak reads; no history-check unknowns, no would-send candidates, zero dispatch attempts and zero sent messages. The first rehearsal exposed ascending within-page ordering; the corrected reader was rebuilt and verified in a subsequent rehearsal.
- Daily enabled state and queued DAILY alarm retained. Next planned run: 2026-11-01 22:00 Beijing. The user's accepted OEM delay window and no-alarm-clock-icon preference remain unchanged.
- The helper activity was relaunched without test extras after validation. This change did not perform a live send.

## Limits

Discovery remains bounded and incomplete. Session expiration, platform verification or unavailable history stops/skips the relevant work. Native Douyin was not reauthenticated or manipulated during this change. Qwen replies, strangers and comments remain outside this streak-only change.

The local signing key, account session, private snapshots, device tooling and screenshots are not public source artifacts. Earlier 0.6 observations remain in repository history.
