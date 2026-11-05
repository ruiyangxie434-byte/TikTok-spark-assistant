import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import os from 'node:os';import path from 'node:path';import {createRequire} from 'node:module';
const {writeDailyReport}=createRequire(import.meta.url)('./daily-report.cjs');
function fixture(t){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'spark-reports-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));return dir;}
test('read-only rehearsals never overwrite the real automatic-run report',t=>{
 const dir=fixture(t),real={status:'nightly-complete',dryRun:false,plannedAt:Date.parse('2026-11-02T14:00:00Z'),dispatchAttempts:1,sentMessages:1};
 writeDailyReport(dir,real,real.plannedAt+2000,true);const saved=fs.readFileSync(path.join(dir,'last-daily-report.json'),'utf8');
 writeDailyReport(dir,{status:'nightly-dry-complete',dryRun:true,plannedAt:Date.parse('2026-11-05T13:00:00Z'),sentMessages:0},Date.now(),true);
 assert.equal(fs.readFileSync(path.join(dir,'last-daily-report.json'),'utf8'),saved);assert.equal(fs.readFileSync(path.join(dir,'last-nightly-report.json'),'utf8'),saved);
 assert.equal(JSON.parse(fs.readFileSync(path.join(dir,'engine-report.json'),'utf8')).dryRun,true);assert.deepEqual(fs.readdirSync(path.join(dir,'daily-history')),['2026-11-02.json']);
});
test('daily history uses Beijing dates, retains seven days, and preserves other local files',t=>{
 const dir=fixture(t);fs.mkdirSync(path.join(dir,'daily-history'));fs.writeFileSync(path.join(dir,'daily-history','keep.txt'),'keep');
 for(let day=1;day<=9;day++){const at=Date.parse('2026-11-'+String(day).padStart(2,'0')+'T16:00:00Z');writeDailyReport(dir,{status:'nightly-complete',dryRun:false,plannedAt:at},at+1,true);}
 const files=fs.readdirSync(path.join(dir,'daily-history')).filter(x=>x.endsWith('.json')).sort();assert.equal(files.length,7);assert.equal(files[0],'2026-11-04.json');assert.equal(files.at(-1),'2026-11-10.json');assert.equal(fs.readFileSync(path.join(dir,'daily-history','keep.txt'),'utf8'),'keep');
});
test('progress and terminal reports retain timestamps and ambiguous dispatch counters',t=>{
 const dir=fixture(t),result={plannedAt:Date.parse('2026-11-05T14:00:00Z'),startedAt:10,dryRun:false,status:'nightly-dispatching',dispatchAttempts:2,sentMessages:1};
 writeDailyReport(dir,result,20);assert.equal(result.finishedAt,undefined);result.status='nightly-timeout';writeDailyReport(dir,result,30,true);
 const saved=JSON.parse(fs.readFileSync(path.join(dir,'last-daily-report.json'),'utf8'));assert.equal(saved.dispatchAttempts,2);assert.equal(saved.sentMessages,1);assert.equal(saved.startedAt,10);assert.equal(saved.updatedAt,30);assert.equal(saved.finishedAt,30);
});
