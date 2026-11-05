# Version 0.8: configurable daily time and schedule recovery

Verified 2026-11-05 on OnePlus 13. Android versionCode 8, versionName 0.8-configurable-time. Native account restoration and the existing today-already-sent guard remain in use.

## Reproduced problem

The installed 0.7 helper showed enabled=true with nextAt still at 2026-11-01 22:00 Beijing. No current pending DAILY alarm was present, and opening its main activity did not rearm it. RUN_ANY_IN_BACKGROUND was allowed. Earlier aggregate results lacked execution dates and could be overwritten by rehearsals, so the exact cause and timeline of the reported November 2 miss cannot be reconstructed conclusively.

## Changes

- Native 24-hour time picker persists Beijing hour/minute; default remains 22:00. Saving while enabled replaces the existing alarm. Turning automation off preserves the selected time.
- Opening the helper now rearms the saved schedule. Boot, package replacement and clock-change restoration use the same selected time. A same-day overdue plan can be checked after reopening; yesterday is never backfilled. Stale or premature alarm deliveries are rejected.
- When another engine check occupies the process, an automatic run can request one delayed attempt within the original Beijing day. Existing per-conversation attempt markers still prevent duplicate sends. No new continuous service or alarm-clock indicator is used.
- Real automatic-run aggregate reports retain planned/start/update/finish times and seven dated history entries, independently of dry runs. Host timeouts preserve recorded dispatch counters. The UI reports the latest actual automatic-run outcome separately from the current diagnostic check.
- Only the helper's own private snapshots are read. Account/session material and message bodies are not exported into diagnostics or source artifacts.

## Validation

54 Node tests and 55 Java configurable-time, restore and timezone/boundary checks passed. Signed APK built, signature verified, installed preserving account/settings, and installed APK hash matched the provided file.

Device tests: 08:17 saved and persisted across relaunch, then restored to 22:00; only one DAILY alarm remained. Force-stop removed the alarm; relaunch rearmed it. The 15-second fixture alarm fired and completed one mock dispatch with zero real sends. Real-account dry run completed: 42 conversations discovered, 23 would-send, 3 other skips, 16 unknown/non-streak reads, zero history-check unknowns, zero dispatch attempts and zero real sends. Its output did not overwrite the actual automatic-run report.

Final state at verification: automation enabled, selected time 22:00 Beijing, one queued DAILY alarm for 2026-11-05 22:00, no engine process active. The normal scheduled live batch has not yet been observed for this build.

## Limits

Android/OEM delivery can be delayed. Force-stopping cancels app alarms; reopening the helper restores the schedule. Work delivered after its planned Beijing day is skipped. Recent-conversation discovery is bounded, and unknown/recovering/inactive states remain excluded from ordinary emoji dispatch. Qwen, stranger and comment replies remain outside this workflow. No past-day spark state is rewritten by the new schedule recovery.

Private evidence, signing data, device tooling and screenshots are excluded from distributable source.
