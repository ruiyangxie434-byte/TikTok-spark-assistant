import test from 'node:test';import assert from 'node:assert/strict';import {readOfficialStreak} from './official-streak-adapter.mjs';
const now=new Date('2026-10-31T14:00:00Z'),address={threadId:'chat',conversationShortId:'12',conversationType:1,inboxType:1};
const raw=days=>JSON.stringify({flame_infos:[{start:1793376000,end:1793462400,state:2,real_days:days}]});
const row={conversationId:'chat',conversationShortId:'12',conversationType:1,isGroup:false,coreExt:{'a:consecutive_chat_data':raw(100)}};
const im=value=>({getConversationInfos:async x=>{assert.deepEqual(x,[address]);return value;}});
test('fresh SDK response binds exact private conversation and positive official streak days',async()=>{const r=await readOfficialStreak(im({statusCode:0,conversations:[row]}),address,now);assert.equal(r.readSucceeded,true);assert.equal(r.hasExistingStreak,true);assert.equal(r.state,'pending');});
test('different, duplicated, group or missing-short-id responses remain unknown',async()=>{for(const rows of [[{...row,conversationId:'other'}],[row,row],[{...row,isGroup:true}],[{...row,conversationShortId:'99'}],[{...row,conversationShortId:'0'}]])assert.equal((await readOfficialStreak(im({statusCode:0,conversations:rows}),address,now)).readSucceeded,false);});
test('zero or absent official days never confirm an existing streak relationship',async()=>{for(const days of [0,undefined,-1,'invalid']){const r=await readOfficialStreak(im({statusCode:0,conversations:[{...row,coreExt:{'a:consecutive_chat_data':raw(days)}}]}),address,now);assert.equal(r.hasExistingStreak,false);}});

test('current conversation risk flags fail closed before dispatch',async()=>{for(const settingExt of [{'a:sky_eye_dialog':'{\"risk\":1}'},{'a:sky_eye_dialog_list':'[{}]'},{'a:sky_eye_dialog':'malformed'}])assert.equal((await readOfficialStreak(im({statusCode:0,conversations:[{...row,settingExt}]}),address,now)).readSucceeded,false);});
