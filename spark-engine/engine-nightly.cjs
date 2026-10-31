"use strict";
const fs=require('node:fs'),path=require('node:path');if(!globalThis.crypto)globalThis.crypto=require('node:crypto').webcrypto;
const {pathToFileURL}=require('node:url'),base=path.join(__dirname,'..');
const config=JSON.parse(fs.readFileSync(path.join(base,'engine-config.json'),'utf8'));
let client,ending=false,started=false;
const result={status:'nightly-restoring',dryRun:config.dryRun===true,authenticationConfirmed:false,sentMessages:0,dispatchAttempts:0,verifiedDone:0,done:0,skipped:0,unknown:0,wouldSend:0,automaticTaskEnabled:config.enabled===true};
function write(){const data=JSON.stringify(result),temp=path.join(base,'nightly-report.tmp');fs.writeFileSync(temp,data);fs.renameSync(temp,path.join(base,'engine-report.json'));fs.writeFileSync(path.join(base,'last-nightly-report.json'),data);}
async function finish(status){if(ending)return;ending=true;result.status=status;write();try{await client?.logout();}catch{}process.exit(0);}
function failure(error){if(ending)return;result.reason=typeof error?.code==='string'?error.code:error?.name??'Error';void finish('nightly-failed');}
process.on('uncaughtException',failure);process.on('unhandledRejection',failure);setTimeout(()=>void finish('nightly-timeout'),295000).unref();
async function execute(account){
 if(ending||started)return;started=true;result.authenticationConfirmed=true;
 const snapshot=JSON.parse(fs.readFileSync(path.join(base,'state-read-private.json'),'utf8'));if(snapshot.accountId!==account.uid){await finish('nightly-account-mismatch');return;}
 const load=name=>import(pathToFileURL(path.join(__dirname,'policy',name)).href);
 const {beijingDateKey,decideStreakSend,streakAttemptKey}=await load('streak-policy.mjs');const {readOfficialStreak}=await load('official-streak-adapter.mjs');
 const {createAttemptLedger}=await load('attempt-ledger.mjs');const {runStreakBatch}=await load('streak-runner.mjs');const {discoverPrivate}=await load('discover-private.mjs');
 const planned=new Date(config.plannedAt);if(!Number.isFinite(planned.getTime())||beijingDateKey()!==beijingDateKey(planned)||Date.now()<config.plannedAt){await finish('nightly-outside-planned-day');return;}
 if(!result.dryRun&&config.enabled!==true){await finish('nightly-disabled');return;}
 result.status='nightly-discovering';write();const found=await discoverPrivate(account,snapshot.conversations);result.discoveryComplete=found.complete;result.discoveredCount=found.conversations.length;result.discoveryErrors=found.readErrors;
 const rows=new Map(found.conversations.map(x=>[x.conversationId,x]));const ledger=createAttemptLedger(path.join(base,'attempt-ledger'));const freshRows=new Map(snapshot.conversations.map(x=>[x.conversationId,x]));let address;
 const inDay=()=>beijingDateKey()===beijingDateKey(planned);
 async function read(id){if(!inDay())return {readSucceeded:false,hasExistingStreak:false};const row=rows.get(id);address={threadId:id,conversationShortId:row.conversationShortId||'0',conversationType:1,inboxType:row.inboxType};const fresh=await readOfficialStreak(account.im,address);if(fresh.address)address=fresh.address;
  if(fresh.readSucceeded){freshRows.set(id,{...row,conversationShortId:address.conversationShortId,raw:fresh.raw,state:fresh.state,readAt:Date.now()});}return fresh;}
 result.status=result.dryRun?'nightly-dry-checking':'nightly-checking';write();
 if(result.dryRun){for(const id of rows.keys()){try{const current=await read(id);const key=streakAttemptKey(account.uid,id);const d=decideStreakSend({accountId:account.uid,conversationId:id,raw:current.raw,readSucceeded:current.readSucceeded,hasExistingStreak:current.hasExistingStreak,attemptedKeys:new Set(ledger.has(key)?[key]:[])});if(d.send)result.wouldSend++;else if(d.state==='done')result.done++;else if(d.state==='unknown')result.unknown++;else result.skipped++;}catch{result.unknown++;}write();}}
 else {
  const {isMessageDelivered}=await import(pathToFileURL(path.join(__dirname,'protocol/lib/services/im/content.js')).href);
  const counts=await runStreakBatch({accountId:account.uid,conversationIds:[...rows.keys()],ledger,readConversation:read,sendEmoji:async(id,emoji)=>{
   if(!inDay())throw new Error('PlannedDayEnded');result.dispatchAttempts++;result.status='nightly-dispatching';write();const reply=await account.outbound.sendText({...address,text:emoji});if(isMessageDelivered(reply))result.sentMessages++;write();return {acknowledged:isMessageDelivered(reply)};
  }});Object.assign(result,{sentMessages:counts.acknowledged,dispatchAttempts:counts.attempted,verifiedDone:counts.verifiedDone,done:counts.done,skipped:counts.skipped,unknown:counts.unknown});
 }
 const updated={accountId:account.uid,readAt:snapshot.readAt,conversations:[...freshRows.values()].filter(x=>typeof x.raw==='string')};const temp=path.join(base,'state-read-private.tmp');fs.writeFileSync(temp,JSON.stringify(updated),{mode:0o600});fs.renameSync(temp,path.join(base,'state-read-private.json'));
 await finish(result.dryRun?'nightly-dry-complete':'nightly-complete');
}
async function main(){write();const sdk=await import(pathToFileURL(path.join(__dirname,'protocol/lib/index.js')).href);sdk.configureLogger({level:'silent'});client=sdk.createClient({autoLoad:true,localState:false,dataDir:path.join(base,'protocol-account')});if(client.accounts.length!==1){await finish('nightly-no-single-saved-account');return;}
 client.on('system.login.qrcode',()=>void finish('nightly-authorization-required'));const block=({verification})=>{verification.cancel('Scheduled job requires user authorization');void finish('nightly-authorization-required');};client.on('system.login.verification',block);client.on('system.action.verification',block);
 client.on('system.online',({account})=>void execute(account).catch(failure));client.on('system.login.error',({error})=>failure(error));client.on('system.handler.error',({error})=>failure(error));await client.login();}
void main().catch(failure);
