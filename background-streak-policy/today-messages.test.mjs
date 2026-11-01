import test from 'node:test';
import assert from 'node:assert/strict';
import {readTodayMessages} from './today-messages.mjs';
const now=new Date('2026-11-01T14:00:00Z'),dateKey='2026-11-01';
const address={threadId:'private-thread',conversationShortId:'900',conversationType:1,inboxType:1};
const msg=(senderUid='11',createTime=Date.parse('2026-11-01T04:00:00Z'),more={})=>({threadId:address.threadId,conversationShortId:'900',conversationType:1,msgId:'91',senderUid,createTime,msgType:7,status:0,...more});
const page=(messages=[],hasMore=false,cursor='0')=>({statusCode:0,messages,hasMore,cursor,direction:'older'});
async function read(pages,self=['11','12']) {
 let calls=0;const value=await readTodayMessages({getMessages:async options=>{assert.equal(options.threadId,address.threadId);assert.equal(options.count,50);assert.equal(options.direction,'older');return pages[calls++];}},address,self,now);
 return {...value,calls};
}
test('own text, sticker, picture and video suppress sending under either authenticated account ID',async()=>{
 for(const type of [7,5,27,30])for(const uid of ['11','12'])assert.equal((await read([page([msg(uid,undefined,{msgType:type})])])).state,'self-sent');
});
test('peer-only and empty complete history confirm no self send; content is never needed',async()=>{
 assert.equal((await read([page([msg('22')])])).state,'not-sent');assert.equal((await read([page()])).state,'not-sent');
});
test('Beijing midnight includes its exact instant, excluding the preceding millisecond',async()=>{
 const midnight=Date.parse('2026-11-01T00:00:00+08:00');
 assert.equal((await read([page([msg('11',midnight)])])).state,'self-sent');
 assert.equal((await read([page([msg('11',midnight-1)],true,'50')])).state,'not-sent');
});
test('raw Unix seconds and milliseconds yield the same date',async()=>{
 assert.equal((await read([page([msg('11',Date.parse('2026-11-01T04:00:00Z')/1000)])])).state,'self-sent');
});
test('look beyond first page of peer messages and preserve the server cursor',async()=>{
 let calls=0;const value=await readTodayMessages({getMessages:async options=>{
  calls++;assert.equal(options.cursor,calls===1?undefined:'100');return calls===1?page([msg('22')],true,'100'):page([msg('11',Date.parse('2026-11-01T03:00:00Z'))],true,'50');
 }},address,['11'],now);
 assert.deepEqual(value,{state:'self-sent',dateKey});assert.equal(calls,2);
});
test('paging crosses midnight without relying on cached last-message metadata',async()=>{
 const result=await read([page([msg('22')],true,'100'),page([msg('11',Date.parse('2026-10-31T15:59:00Z'))],true,'50')]);
 assert.equal(result.state,'not-sent');assert.equal(result.calls,2);
});
test('tips and recalls do not count as user-authored messages',async()=>{
 const messages=[1,1001,1002,1010,40001,50000].map(msgType=>msg('11',undefined,{msgType}));
 messages.push(msg('11',undefined,{ext:{'s:is_recalled':'true'}}));messages.push(msg('11',undefined,{deleted:true}));
 assert.equal((await read([page(messages)])).state,'not-sent');
});
test('missing identity, invalid timestamps, cross-conversation and ambiguous delivery stay unknown',async()=>{
 for(const more of [{senderUid:''},{msgId:'0'},{status:1},{createTime:0},{createTime:NaN},{createTime:now.getTime()+1},{threadId:'other'},{conversationShortId:'901'},{conversationType:2},{msgType:0}])
  assert.equal((await read([page([msg('11',undefined,more)])])).state,'unknown',JSON.stringify(more));
 assert.equal((await read([page()],[])).state,'unknown');
 assert.equal((await readTodayMessages({}, {...address,conversationType:2},['11'],now)).state,'unknown');
});
test('failed or malformed responses and thrown reads never authorize a send',async()=>{
 for(const response of [{...page(),statusCode:1},{...page(),direction:'newer'},{...page(),hasMore:undefined},{...page(),messages:null}])assert.equal((await read([response])).state,'unknown');
 const result=await readTodayMessages({getMessages:async()=>{throw new Error('offline');}},address,['11'],now);assert.equal(result.state,'unknown');
});
test('incomplete, repeated, backwards-time and nonadvancing pages stay unknown',async()=>{
 for(const pages of [[page([],true,'100')],[page([msg('22')],true,'100'),page([msg('22')],true,'100')],
  [page([msg('22')],true,'100'),page([msg('22')],true,'101')],
  [page([msg('22')],true,'100'),page([msg('22',Date.parse('2026-11-01T05:00:00Z'))])],
  [1,2,3,4,5].map((_,i)=>page([msg('22')],true,String(100-i)))]){
  assert.equal((await read(pages)).state,'unknown');
 }
});

test('full dry-run path distinguishes own messages from peer messages while official streak is pending',async()=>{
 const {runStreakBatch}=await import('./streak-runner.mjs');
 const histories={own:[msg()],both:[msg('22'),msg()],peer:[msg('22')],none:[],yesterday:[msg('11',Date.parse('2026-10-31T04:00:00Z'))]};
 const counts=await runStreakBatch({accountId:'11',conversationIds:Object.keys(histories),now:()=>now,dryRun:true,
  ledger:{has(){return false;},claim(){assert.fail('dry run claimed');}},sendEmoji:async()=>assert.fail('dry run sent'),
  readConversation:async()=>({readSucceeded:true,hasExistingStreak:true,raw:JSON.stringify({flame_infos:[{start:Date.parse('2026-11-01T00:00:00+08:00')/1000,end:Date.parse('2026-11-02T00:00:00+08:00')/1000,state:2}]})}),
  readTodayActivity:async id=>readTodayMessages({getMessages:async()=>page(histories[id])},address,['11'],now)});
 assert.equal(counts.alreadySentToday,2);assert.equal(counts.wouldSend,3);assert.equal(counts.historyUnknown,0);assert.equal(counts.attempted,0);
});

test('older-history pages may list messages oldest first, and own sends later in that page still skip',async()=>{
 const older=Date.parse('2026-10-31T14:00:00Z'),today=Date.parse('2026-11-01T04:00:00Z');
 assert.equal((await read([page([msg('22',older),msg('11',today)],true,'100')])).state,'self-sent');
 assert.equal((await read([page([msg('22',today),msg('22',today+1000)],false)])).state,'not-sent');
});
