"use strict";
const fs=require('node:fs'),path=require('node:path');
if(!globalThis.crypto)globalThis.crypto=require('node:crypto').webcrypto;
const {pathToFileURL}=require('node:url'),base=path.join(__dirname,'..');
let client,ending=false,started=false;
const result={status:'state-restoring',authenticationConfirmed:false,privateCount:0,candidateCount:0,done:0,pending:0,recover:0,inactive:0,unknown:0,readErrors:0,pages:0,complete:false,sentMessages:0,automaticTaskEnabled:false};
function write(){const temp=path.join(base,'state-report.tmp');fs.writeFileSync(temp,JSON.stringify(result));fs.renameSync(temp,path.join(base,'engine-report.json'));}
async function finish(status){if(ending)return;ending=true;result.status=status;write();try{await client?.logout();}catch{}process.exit(0);}
function failure(error){if(ending)return;while(error?.errors?.length)error=error.errors[0];result.reason=typeof error?.code==='string'?error.code:error?.name??'Error';void finish('state-failed');}
process.on('uncaughtException',failure);process.on('unhandledRejection',failure);
setTimeout(()=>void finish('state-timeout'),150000).unref();
async function scan(account){
 if(started||ending)return;started=true;result.authenticationConfirmed=true;result.status='state-reading';write();
 const {classifyOfficialStreak}=await import(pathToFileURL(path.join(__dirname,'policy/streak-policy.mjs')).href);
 const all=new Map();let complete=true;
 const friends=await account.getFriendList();
 for(const friend of friends){const a=friend.address;if(a.conversationType===1&&a.threadId)all.set(a.threadId,{conversationId:a.threadId,conversationShortId:a.conversationShortId||'0',inboxType:a.inboxType,displayName:friend.remark||friend.nickname||''});}
 result.friendCount=friends.length;
 result.discoveredConversations=0;result.discoveredThreads=0;result.inboxes=[];
 for(const inboxType of [0,1]){
  let cursor='0';const seen=new Set();
  for(let page=0;page<5;page++){
   const response=await account.im.listThreads({inboxType,cursor});result.pages++;
   result.inboxes.push({inbox:inboxType,page,status:response.statusCode,conversations:response.conversations?.length??0,threads:response.threads?.length??0,hasMore:response.hasMore});
   if(response.statusCode!==0){result.readErrors++;complete=false;break;}
   result.discoveredConversations+=response.conversations.length;result.discoveredThreads+=response.threads.length;
   for(const row of response.conversations){if(row.conversationType===1&&!row.isGroup&&row.conversationId)all.set(row.conversationId,{...row,inboxType:row.inboxType??inboxType});}
   for(const row of response.threads){if(row.conversationType===1&&row.threadId)all.set(row.threadId,{...all.get(row.threadId),conversationId:row.threadId,conversationShortId:row.conversationShortId||'0',inboxType:row.inboxType??inboxType,displayName:row.peer?.nickname||'',peer:row.peer});}
   result.privateCount=all.size;write();if(!response.hasMore)break;
   if(!response.cursor||response.cursor===cursor||seen.has(response.cursor)||page===4){complete=false;break;}
   seen.add(cursor);cursor=response.cursor;
  }
 }
 result.scope='friends-and-recent-inbox-pages';result.privateCount=all.size;const snapshots=[];write();
 const entries=[...all];
 for(let offset=0;offset<entries.length;offset+=4){
  await Promise.all(entries.slice(offset,offset+4).map(async([id,row])=>{
   try{
    const fresh=await account.im.getConversationInfos([{threadId:id,conversationShortId:row.conversationShortId,conversationType:1,inboxType:row.inboxType}]);
    const matched=fresh.conversations?.filter(x=>x.conversationId===id);
    if(fresh.statusCode!==0||matched?.length!==1){result.readErrors++;result.unknown++;complete=false;return;}
    const raw=matched[0].coreExt?.['a:consecutive_chat_data'];if(typeof raw!=='string')return;
    result.candidateCount++;const state=raw.length<=20000?classifyOfficialStreak(raw,Date.now()/1000):'unknown';result[state]++;
    const peer=matched[0].members?.find(x=>x.uid&&x.uid!==account.imUid&&x.uid!==account.uid);
    snapshots.push({conversationId:id,conversationShortId:matched[0].conversationShortId,inboxType:row.inboxType,displayName:row.displayName||peer?.alias||peer?.nickname||matched[0].name||'',peerUid:row.peer?.uid||peer?.uid,peerSecUid:row.peer?.secUid||peer?.secUid,raw,state,readAt:Date.now()});
   }catch{result.readErrors++;result.unknown++;complete=false;}
  }));result.pages++;write();
 }
 const profiles=await account.im.resolveUsers(snapshots.filter(x=>!x.displayName&&x.peerSecUid).map(x=>x.peerSecUid));
 for(const row of snapshots){if(!row.displayName){const profile=profiles.find(x=>x.secUid===row.peerSecUid&&x.uid===row.peerUid);if(profile)row.displayName=profile.nickname;}}
 result.namedCount=snapshots.filter(x=>x.displayName).length;const keys=new Set();const counters={};for(const row of snapshots){try{for(const [key,value] of Object.entries(JSON.parse(row.raw))){if(/^[a-z_]{1,60}$/i.test(key))keys.add(key);if(/day|consecutive/i.test(key)&&Number.isSafeInteger(value)&&value>0&&value<100000)counters[key]=(counters[key]??0)+1;}}catch{}}result.streakFieldNames=[...keys];result.positiveCounterFields=counters;
 fs.writeFileSync(path.join(base,'state-read-private.json'),JSON.stringify({accountId:account.uid,readAt:Date.now(),conversations:snapshots}),{mode:0o600});
 result.complete=complete&&all.size>0;result.freshReadConfirmed=snapshots.length>0;await finish(all.size>0?'state-read-complete':'state-no-conversations');
}
async function main(){write();const sdk=await import(pathToFileURL(path.join(__dirname,'protocol/lib/index.js')).href);sdk.configureLogger({level:'silent'});client=sdk.createClient({autoLoad:true,localState:false,dataDir:path.join(base,'protocol-account')});
 if(client.accounts.length!==1){await finish('state-no-single-saved-account');return;}
 client.on('system.login.qrcode',()=>void finish('state-authorization-required'));
 client.on('system.login.verification',({verification})=>{verification.cancel('Read-only check requires user authorization');void finish('state-authorization-required');});
 client.on('system.online',({account})=>void scan(account).catch(failure));
 client.on('system.login.error',({error})=>failure(error));client.on('system.handler.error',({error})=>failure(error));await client.login();
}
void main().catch(failure);
