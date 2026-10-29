import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
const script = readFileSync(new URL('./build/probe.js', import.meta.url), 'utf8');
const now = Date.parse('2026-10-28T13:00:00Z');
class Clock extends Date { static now() { return now; } }
const item = (type, state) => ({type, id:'not-exported', message:'private text must never leave the page',
 coreInfo:{ext:{'a:consecutive_chat_data':JSON.stringify({flame_infos:[{start:now/1000-10,end:now/1000+10,state}]})}}});
const read = (map, origin='https://www.douyin.com') => JSON.parse(JSON.stringify(runInNewContext(script,
 {Date:Clock,location:{origin},conversationStore:{conversationMap:map}})));
test('requires the official page and a loaded store', () => {
 assert.equal(read(new Map(), 'https://example.com').ready,false);
 assert.equal(read(null).ready,false);
});
test('counts private streak states while ignoring groups and exporting no messages or IDs', () => {
 const result=read(new Map([['a',item(1,1)],['b',item(1,2)],['g',item(2,2)],['bad',{type:1,coreInfo:{ext:{'a:consecutive_chat_data':'bad'}}}]]));
 assert.equal(result.privateCount,3);assert.equal(result.candidateCount,3);
 assert.equal(result.done,1);assert.equal(result.pending,1);assert.equal(result.unknown,1);
 assert.equal(result.serverRefreshConfirmed,false);
 assert.equal(JSON.stringify(result).includes('not-exported'),false);
 assert.equal(JSON.stringify(result).includes('private text'),false);
});
test('bounded reads disclose that the snapshot is partial', () => {
 const result=read(new Map(Array.from({length:2001},(_,i)=>[String(i),item(1,1)])));
 assert.equal(result.truncated,true);assert.equal(result.privateCount,2000);
});


test('empty or group-only stores are not treated as usable private-chat data', () => {
 assert.equal(read(new Map()).ready,false);
 assert.equal(read(new Map([['group',item(2,2)]])).ready,false);
});
