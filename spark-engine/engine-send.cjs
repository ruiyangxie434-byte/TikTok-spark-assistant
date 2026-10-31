"use strict";
const fs=require('node:fs'),path=require('node:path');if(!globalThis.crypto)globalThis.crypto=require('node:crypto').webcrypto;
const {pathToFileURL}=require('node:url'),base=path.join(__dirname,'..');
const config=JSON.parse(fs.readFileSync(path.join(base,'engine-config.json'),'utf8'));
let client,ending=false,started=false;
const result={status:'send-restoring',authenticationConfirmed:false,sentMessages:0,dispatchAttempts:0,verifiedDone:0,automaticTaskEnabled:false};
function write(){const temp=path.join(base,'send-report.tmp');fs.writeFileSync(temp,JSON.stringify(result));fs.renameSync(temp,path.join(base,'engine-report.json'));}
async function finish(status){if(ending)return;ending=true;result.status=status;write();try{await client?.logout();}catch{}process.exit(0);}
function failure(error){if(ending)return;result.reason=typeof error?.code==='string'?error.code:error?.name??'Error';void finish('send-failed');}
process.on('uncaughtException',failure);process.on('unhandledRejection',failure);setTimeout(()=>void finish('send-timeout'),85000).unref();
async function execute(account){
 if(ending||started)return;started=true;result.authenticationConfirmed=true;
 const snapshot=JSON.parse(fs.readFileSync(path.join(base,'state-read-private.json'),'utf8'));
 if(snapshot.accountId!==account.uid){await finish('send-account-mismatch');return;}
 const selected=snapshot.conversations.filter(x=>x.conversationId===config.conversationId);
 if(selected.length!==1){await finish('send-target-unknown');return;}
 const target=selected[0];if(!target.displayName||snapshot.conversations.filter(x=>x.displayName.toLowerCase()===target.displayName.toLowerCase()).length!==1){await finish('send-target-ambiguous');return;}let address={threadId:target.conversationId,conversationShortId:target.conversationShortId,conversationType:1,inboxType:target.inboxType};
 const {runStreakBatch}=await import(pathToFileURL(path.join(__dirname,'policy/streak-runner.mjs')).href);
 const {createAttemptLedger}=await import(pathToFileURL(path.join(__dirname,'policy/attempt-ledger.mjs')).href);
 const {readOfficialStreak}=await import(pathToFileURL(path.join(__dirname,'policy/official-streak-adapter.mjs')).href);
 const {isMessageDelivered}=await import(pathToFileURL(path.join(__dirname,'protocol/lib/services/im/content.js')).href);
 let dispatched=false;result.status='send-checking';write();
 const counts=await runStreakBatch({accountId:account.uid,conversationIds:[target.conversationId],ledger:createAttemptLedger(path.join(base,'attempt-ledger')),
  readConversation:async()=>{let fresh;for(let i=0;i<(dispatched?5:1);i++){if(i)await new Promise(r=>setTimeout(r,1200));fresh=await readOfficialStreak(account.im,address);if(fresh.address)address=fresh.address;if(fresh.readSucceeded){Object.assign(target,{raw:fresh.raw,state:fresh.state,readAt:Date.now()});try{const temp=path.join(base,'state-read-private.tmp');fs.writeFileSync(temp,JSON.stringify(snapshot),{mode:0o600});fs.renameSync(temp,path.join(base,'state-read-private.json'));}catch{}}if(fresh.state==='done')break;}return fresh;},
  sendEmoji:async(id,emoji)=>{result.dispatchAttempts=1;result.status='send-dispatching';write();dispatched=true;const reply=await account.outbound.sendText({...address,text:emoji});return {acknowledged:isMessageDelivered(reply)};}});
 Object.assign(result,{sentMessages:counts.acknowledged,dispatchAttempts:counts.attempted,verifiedDone:counts.verifiedDone,done:counts.done,skipped:counts.skipped,unknown:counts.unknown});
 await finish(counts.verifiedDone?'send-renewal-confirmed':counts.acknowledged?'send-acknowledged-pending':counts.attempted?'send-result-uncertain':counts.done?'send-skipped-already-done':'send-skipped');
}
async function main(){write();const sdk=await import(pathToFileURL(path.join(__dirname,'protocol/lib/index.js')).href);sdk.configureLogger({level:'silent'});client=sdk.createClient({autoLoad:true,localState:false,dataDir:path.join(base,'protocol-account')});if(client.accounts.length!==1){await finish('send-no-single-saved-account');return;}
 client.on('system.login.qrcode',()=>void finish('send-authorization-required'));client.on('system.login.verification',({verification})=>{verification.cancel('Sending requires user authorization');void finish('send-authorization-required');});
 client.on('system.online',({account})=>void execute(account).catch(failure));client.on('system.login.error',({error})=>failure(error));client.on('system.handler.error',({error})=>failure(error));await client.login();}
void main().catch(failure);
