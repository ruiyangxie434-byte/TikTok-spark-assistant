"use strict";
const fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const base=path.join(__dirname,'..');
function write(value){const temp=path.join(base,'fixture-report.tmp');fs.writeFileSync(temp,JSON.stringify(value));fs.renameSync(temp,path.join(base,'engine-report.json'));}
async function main(){
 const {createAttemptLedger}=await import(pathToFileURL(path.join(__dirname,'policy/attempt-ledger.mjs')).href);
 const {runStreakBatch}=await import(pathToFileURL(path.join(__dirname,'policy/streak-runner.mjs')).href);
 const {streakAttemptKey}=await import(pathToFileURL(path.join(__dirname,'policy/streak-policy.mjs')).href);
 const dir=path.join(base,'fixture-ledger');fs.rmSync(dir,{recursive:true,force:true});
 const fixed=new Date('2026-10-31T14:00:00Z');
 const raw=state=>JSON.stringify({flame_infos:[{start:1793376000,end:1793462400,state}]});
 let mockDispatches=0,reads=0;
 const input={accountId:'fixture-account',conversationIds:['done','pending','unknown','pending'],now:()=>fixed,
  readConversation:async id=>id==='unknown'?{readSucceeded:false}:{raw:raw(id==='done'?1:++reads===1?2:1),readSucceeded:true,hasExistingStreak:true},
  sendEmoji:async id=>{if(!createAttemptLedger(dir).has(streakAttemptKey('fixture-account',id,fixed)))throw new Error('ClaimMissing');mockDispatches++;return {acknowledged:true};}};
 const first=await runStreakBatch({...input,ledger:createAttemptLedger(dir)});
 const second=await runStreakBatch({...input,ledger:createAttemptLedger(dir),readConversation:async()=>({raw:raw(2),readSucceeded:true,hasExistingStreak:true}),conversationIds:['pending']});
 if(mockDispatches!==1||first.done!==1||first.verifiedDone!==1||first.unknown!==1||second.attempted!==0)throw new Error('FixtureMismatch');
 const ui=JSON.parse(fs.readFileSync(path.join(base,'engine-ui.json'),'utf8'));
 write({status:'policy-fixture-passed',runtime:process.versions.node,finishedWhileUiVisible:ui.visible===true,fixtureOnly:true,mockDispatches,sentMessages:0,automaticTaskEnabled:false});
}
void main().catch(error=>write({status:'policy-fixture-failed',reason:error?.code??error?.name??'Error',fixtureOnly:true,sentMessages:0,automaticTaskEnabled:false}));
