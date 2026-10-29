import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {sm3Digest} from './vendor/sm3.mjs';
import {nativeABogus} from './vendor/nativeSignature.mjs';
const script=readFileSync(new URL('./build/session-check.js',import.meta.url),'utf8');
const context={TextEncoder,URL}; runInNewContext(script,context);
const api=context.SparkSession;
const ua='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36';

test('portable SM3 matches an independent implementation across UTF-8 and block boundaries',()=>{
 assert.equal(Buffer.from(sm3Digest('abc')).toString('hex'),'66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0');
 for(const input of ['', '抖音😀', 'x'.repeat(55),'x'.repeat(56),'x'.repeat(64),'x'.repeat(1000)])
  assert.equal(Buffer.from(sm3Digest(input)).toString('hex'),createHash('sm3').update(input).digest('hex'));
});
test('signature still matches the fixed upstream Python vector after the browser port',()=>{
 assert.equal(nativeABogus('device_platform=webapp&aid=6383&keyword=%E7%8C%AB',{body:'count=10&cursor=0',userAgent:ua,now:1791684000000,rng:()=>.5}),
 'O7UfhzU7dNmfCdMbuKJsSHPUG99MrPSybTToWuHTSPzJL1tT1SNXTb33JFxgsTo0fbB0we3HzxupbDVPqsWfsK9pKmpDuOhWVGVnIgXL0qwXTts/LqmiehhFwwsC0mJN-5C-E1gRIs0xIjVR9qVplB/aH5Fq5cDgRrpVp/byCEA6DA8kkx-zO9hkLg5D');
});
test('bundled check binds the URL to the official read endpoint and accepts only selected session metadata',()=>{
 const url=new URL(api.sign({userAgent:ua,values:{msToken:'a+b/c=',uifid:'test',endpoint:'https://evil.invalid/',sessionid:'never-in-query'}}));
 assert.equal(url.origin,'https://www.douyin.com');assert.equal(url.pathname,'/aweme/v1/web/user/profile/self/');
 assert.equal(url.searchParams.get('msToken'),'a+b/c=');assert.equal(url.searchParams.has('sessionid'),false);
 assert.match(url.searchParams.get('a_bogus'),/^[A-Za-z0-9/=-]{64,512}$/);
 assert.throws(()=>api.sign({userAgent:'bad'}));
});
test('HTML, verification errors, anonymous IDs and redirects never imply expired or valid sessions',()=>{
 const cases=[{httpStatus:302,body:'{"status_code":0,"user":{"uid":"123"}}'},
 {httpStatus:200,body:'<html>challenge</html>'},{httpStatus:200,body:'{"status_code":8}'},
 {httpStatus:200,body:'{"status_code":0,"user_uid":"123","user_uid_type":0}'},
 {httpStatus:200,body:'{"status_code":0,"user":null}'},{httpStatus:200,body:'{"status_code":0,"user":{"uid":9007199254740993}}'},
 {httpStatus:200,body:'{"status_code":0,"user":{"uid":"0"}}'}, {httpStatus:200,body:'{"status_code":0,"user":{"uid":"123"},"extra":"'+ 'x'.repeat(262144)+'"}'}];
 for(const input of cases) {const result=api.classify(input);assert.equal(result.status,'session-unknown');assert.equal(result.authenticationConfirmed,false);}
});
test('a self-profile result confirms only readable authentication and exports no account data',()=>{
 const result=api.classify({httpStatus:200,body:JSON.stringify({status_code:0,user:{uid:'12345678901234567890',nickname:'private-name'},sessionid:'private-secret'})});
 assert.equal(result.status,'session-profile-readable');assert.equal(result.authenticationConfirmed,true);assert.equal(result.accountMatchConfirmed,false);
 assert.equal(JSON.stringify(result).includes('1234567890'),false);assert.equal(JSON.stringify(result).includes('private-'),false);
});

test('diagnostic metadata distinguishes explicit server response from non-JSON without exporting private fields',()=>{
 const rejected=api.classify({httpStatus:200,body:'{"status_code":8,"status_msg":"private-phone","user":null}'});
 assert.equal(rejected.responseKind,'json');assert.equal(rejected.platformStatus,'8');assert.equal(rejected.profileShape,'null');
 assert.equal(JSON.stringify(rejected).includes('private-phone'),false);assert.equal(rejected.status,'session-unknown');
 assert.equal(api.classify({httpStatus:200,body:'<html>verify</html>'}).responseKind,'non-json');
});
