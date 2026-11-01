import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {createAttemptLedger} from './attempt-ledger.mjs';
import {runStreakBatch} from './streak-runner.mjs';
import {streakAttemptKey} from './streak-policy.mjs';
const fixed=new Date('2026-10-31T14:00:00Z');const now=()=>fixed;
const raw=state=>JSON.stringify({flame_infos:[{start:1793376000,end:1793462400,state}]});
const read=state=>({readSucceeded:true,hasExistingStreak:true,raw:raw(state)});
function fixture(t){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'spark-ledger-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));return dir;}
test('persist before dispatch and suppress duplicates across a new ledger instance',async t=>{
 const dir=fixture(t),key=streakAttemptKey('a','c',fixed);let sends=0;
 const execute=ledger=>runStreakBatch({accountId:'a',readTodayActivity:async()=>({state:'not-sent',dateKey:'2026-10-31'}),conversationIds:['c','c'],ledger,now,readConversation:async()=>read(2),sendEmoji:async()=>{assert(createAttemptLedger(dir).has(key));sends++;return {acknowledged:true};}});
 const first=await execute(createAttemptLedger(dir));const second=await execute(createAttemptLedger(dir));
 assert.equal(sends,1);assert.equal(first.attempted,1);assert.equal(first.verifiedDone,0);assert.equal(second.attempted,0);
});
test('manual renewal and unknown reads skip; only server done verifies renewal',async t=>{
 let sends=0,pendingReads=0;
 const counts=await runStreakBatch({accountId:'a',readTodayActivity:async()=>({state:'not-sent',dateKey:'2026-10-31'}),conversationIds:['done','bad','pending'],ledger:createAttemptLedger(fixture(t)),now,
  readConversation:async id=>id==='done'?read(1):id==='bad'?{readSucceeded:false}:read(++pendingReads===1?2:1),
  sendEmoji:async()=>{sends++;return {acknowledged:true};}});
 assert.equal(sends,1);assert.equal(counts.done,1);assert.equal(counts.unknown,1);assert.equal(counts.verifiedDone,1);
});
test('ambiguous send result or exception remains claimed after restart',async t=>{
 const dir=fixture(t);let calls=0;
 const input={accountId:'a',readTodayActivity:async()=>({state:'not-sent',dateKey:'2026-10-31'}),conversationIds:['c'],now,readConversation:async()=>read(2),sendEmoji:async()=>{calls++;throw new Error('uncertain');}};
 await runStreakBatch({...input,ledger:createAttemptLedger(dir)});await runStreakBatch({...input,ledger:createAttemptLedger(dir)});assert.equal(calls,1);
});
test('unavailable persistence blocks dispatch; independent conversations still progress',async()=>{
 let sends=0;
 const counts=await runStreakBatch({accountId:'a',readTodayActivity:async()=>({state:'not-sent',dateKey:'2026-10-31'}),conversationIds:['a','b'],now,readConversation:async()=>read(2),ledger:{has(){return false;},claim(){throw new Error('disk-full');}},sendEmoji:async()=>{sends++;}});
 assert.equal(sends,0);assert.equal(counts.unknown,2);
});
test('claim race and non-streak relationships block dispatch',async()=>{
 let sends=0;
 const counts=await runStreakBatch({accountId:'a',readTodayActivity:async()=>({state:'not-sent',dateKey:'2026-10-31'}),conversationIds:['race','new'],now,
  readConversation:async id=>({...read(2),hasExistingStreak:id!=='new'}),ledger:{has(){return false;},claim(){return false;}},sendEmoji:async()=>{sends++;}});
 assert.equal(sends,0);assert.equal(counts.skipped,2);
});

test('today self-sent messages skip without claiming or sending, even while official state is pending',async()=>{
 let sends=0,claims=0;
 const counts=await runStreakBatch({accountId:'a',conversationIds:['c'],now,readConversation:async()=>read(2),
  readTodayActivity:async()=>({state:'self-sent',dateKey:'2026-10-31'}),
  ledger:{has(){return false;},claim(){claims++;return true;}},sendEmoji:async()=>{sends++;}});
 assert.equal(sends,0);assert.equal(claims,0);assert.equal(counts.alreadySentToday,1);
});

test('missing, failed, stale or unknown history blocks dispatch without consuming a ledger claim',async()=>{
 for(const activity of [undefined,{state:'unknown',dateKey:'2026-10-31'},{state:'not-sent',dateKey:'2026-10-30'}]){
  let calls=0;
  const counts=await runStreakBatch({accountId:'a',conversationIds:['c'],now,readConversation:async()=>read(2),readTodayActivity:async()=>activity,
   ledger:{has(){return false;},claim(){calls++;return true;}},sendEmoji:async()=>{calls++;}});
  assert.equal(calls,0);assert.equal(counts.historyUnknown,1);
 }
 const result=await runStreakBatch({accountId:'a',conversationIds:['c'],now,readConversation:async()=>read(2),ledger:{has(){return false;}},sendEmoji:async()=>assert.fail('sent')});
 assert.equal(result.historyUnknown,1);
});
test('dry run uses the same history guard, without claiming or dispatching',async()=>{
 const counts=await runStreakBatch({accountId:'a',conversationIds:['self','peer'],now,dryRun:true,readConversation:async()=>read(2),
  readTodayActivity:async id=>({state:id==='self'?'self-sent':'not-sent',dateKey:'2026-10-31'}),ledger:{has(){return false;},claim(){assert.fail('claimed');}},sendEmoji:async()=>assert.fail('sent')});
 assert.equal(counts.wouldSend,1);assert.equal(counts.alreadySentToday,1);assert.equal(counts.attempted,0);
});
test('crossing Beijing midnight during history reading blocks dispatch',async()=>{
 let at=fixed;
 const counts=await runStreakBatch({accountId:'a',conversationIds:['c'],now:()=>at,readConversation:async()=>read(2),
  readTodayActivity:async()=>{at=new Date('2026-10-31T16:00:00Z');return {state:'not-sent',dateKey:'2026-10-31'};},
  ledger:{has(){return false;},claim(){assert.fail('claimed');}},sendEmoji:async()=>assert.fail('sent')});
 assert.equal(counts.historyUnknown,1);
});
