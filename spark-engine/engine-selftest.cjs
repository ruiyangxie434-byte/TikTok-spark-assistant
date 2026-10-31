'use strict';
const fs=require('node:fs');const path=require('node:path');const crypto=require('node:crypto');const https=require('node:https');
if(!globalThis.crypto)globalThis.crypto=require('node:crypto').webcrypto;
const {pathToFileURL}=require('node:url');const reportFile=path.join(__dirname,'..','engine-report.json');const started=Date.now();let stage='startup';
function write(value){const temp=reportFile+'.node-tmp';fs.writeFileSync(temp,JSON.stringify(value));fs.renameSync(temp,reportFile);}
function visible(){try{return JSON.parse(fs.readFileSync(path.join(__dirname,'..','engine-ui.json'),'utf8')).visible===true;}catch{return null;}}
function failure(error){write({status:'engine-not-passed',stage,reason:typeof error?.code==='string'?error.code:error?.name??'Error',elapsedMs:Date.now()-started,finishedWhileUiVisible:visible(),fixtureOnly:true,sentMessages:0,automaticTaskEnabled:false});}
process.on('uncaughtException',failure);process.on('unhandledRejection',failure);
function connection(){return new Promise(resolve=>{
 let done=false;let timer;const end=value=>{if(done)return;done=true;clearTimeout(timer);resolve(value);};
 const request=https.get('https://imdesktop.douyin.com/',{timeout:10000},response=>{response.resume();end(response.statusCode>=100 && response.statusCode<600);});
 timer=setTimeout(()=>{request.destroy();end(false);},12000);
 request.on('timeout',()=>{request.destroy();end(false);});request.on('error',()=>end(false));
});}
write({status:'engine-runtime-started',runtime:process.versions.node,platform:process.platform,fixtureOnly:true,sentMessages:0,automaticTaskEnabled:false});
setTimeout(()=>{void check().catch(failure);},12000);
async function check(){
 const elapsed=Date.now()-started;const timerWhileUiVisible=visible();stage='crypto';
 const cryptoReady=crypto.createHash('sha256').update('spark-engine').digest('hex').length===64;
 stage='sdk-import';const sdk=await import(pathToFileURL(path.join(__dirname,'protocol','lib','index.js')).href);
 sdk.configureLogger({level:'silent'});const client=sdk.createClient({autoLoad:false,localState:false,dataDir:path.join(__dirname,'smoke-data')});await client.logout();const sdkReady=true;
 stage='network';const networkReady=await connection();
 write({status:cryptoReady && sdkReady && networkReady?'engine-passed':'engine-not-passed',runtime:process.versions.node,platform:process.platform,
 elapsedMs:elapsed,totalMs:Date.now()-started,cryptoReady,networkReady,sdkReady,timerWhileUiVisible,finishedWhileUiVisible:visible(),
 fixtureOnly:true,authenticationConfirmed:false,sentMessages:0,automaticTaskEnabled:false});
}
