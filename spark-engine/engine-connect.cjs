"use strict";
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
if(!globalThis.crypto)globalThis.crypto=require('node:crypto').webcrypto;
const {pathToFileURL}=require('node:url');
const base=path.join(__dirname,'..'),config=JSON.parse(fs.readFileSync(path.join(base,'engine-config.json'),'utf8'));
let state={status:'connecting'},client,bridge,ending=false,lastRequest;
const originalFetch=globalThis.fetch;globalThis.fetch=async function(input,init){const u=new URL(input instanceof Request?input.url:input);lastRequest={host:u.hostname,path:u.pathname};return originalFetch(input,init);};
function report(){const safe={status:'connect-'+state.status,fixtureOnly:false,authenticationConfirmed:state.status==='online',sentMessages:0,automaticTaskEnabled:false};if(bridge?.listening)safe.bridgePort=bridge.address().port;if(state.reason)safe.reason=state.reason;if(state.frames)safe.frames=state.frames;if(lastRequest)safe.request=lastRequest;const temp=path.join(base,'engine-report.connect-tmp');fs.writeFileSync(temp,JSON.stringify(safe));fs.renameSync(temp,path.join(base,'engine-report.json'));}
function update(next){state=next;report();}
function failure(error){if(ending||state.status==='failed')return;while(error?.errors?.length)error=error.errors[0];const cause=error?.cause;const code=cause?.code??error?.code;const frames=String(error?.stack??'').split('\n').slice(1,5).map(x=>x.match(/([\w.-]+\.(?:js|cjs)):(\d+):(\d+)/)?.[0]).filter(Boolean);update({status:'failed',reason:(typeof code==='string'?code:error?.name??'Error'),frames});if(config.probeOnly)void stop();}
async function stop(){if(ending)return;ending=true;try{await client?.logout();}catch{}bridge?.close();setTimeout(()=>process.exit(0),100).unref();}
process.on('uncaughtException',failure);process.on('unhandledRejection',failure);
setTimeout(()=>{update({status:'timed-out'});void stop();},540000).unref();
async function main(){
 if(!/^[a-f0-9-]{36}$/.test(config.token))throw new Error('Invalid bridge token');
 bridge=http.createServer((req,res)=>{
  res.setHeader('Cache-Control','no-store');res.setHeader('Content-Type','application/json');res.setHeader('Connection','close');
  if(req.method!=='GET'||req.url!=='/status'||req.headers['x-spark-token']!==config.token){res.writeHead(403);res.end('{}');return;}
  res.end(JSON.stringify(state));
 });
 await new Promise((resolve,reject)=>{bridge.once('error',reject);bridge.listen(0,'127.0.0.1',resolve);});report();
 const sdk=await import(pathToFileURL(path.join(__dirname,'protocol/lib/index.js')).href);sdk.configureLogger({level:'silent'});
 client=sdk.createClient({autoLoad:true,localState:false,dataDir:path.join(base,'protocol-account')});
 client.on('system.login.qrcode',async({account,qrcodeBase64})=>{if(config.probeOnly){update({status:'qr-ready'});void stop();return;}update({status:'qr',qr:qrcodeBase64.replace(/^data:image\/[^;]+;base64,/, '')});await account.continueLogin();});
 client.on('system.login.qrcode.status',({status})=>{if(status==='expired')update({status:'expired'});else if(status==='scanned'||status==='confirmed')update({status});});
 client.on('system.login.verification',async({verification})=>{
  try{const verifyUrl=await verification.openUrl({openBrowser:false,timeoutMs:300000});const u=new URL(verifyUrl);if(u.protocol!=='http:'||u.hostname!=='127.0.0.1'||!/^\d+$/.test(u.port))throw new Error('UnexpectedVerificationHost');
   update({status:'verification',verifyUrl});await verification.open({openBrowser:false,timeoutMs:300000});update({status:'connecting'});
  }catch(error){failure(error);}
 });
 client.on('system.online',()=>update({status:'online'}));
 client.on('system.login.error',({error})=>failure(error));client.on('system.handler.error',({error})=>failure(error));
 await client.login();
}
void main().catch(failure);
