"use strict";
const fs=require('node:fs'),path=require('node:path');
function atomic(file,data){const temp=file+'.node.tmp';fs.writeFileSync(temp,data,{mode:0o600});fs.renameSync(temp,file);}
function writeDailyReport(base,result,now=Date.now(),finished=false){
 result.updatedAt=now;if(finished)result.finishedAt=now;
 const data=JSON.stringify(result);atomic(path.join(base,'engine-report.json'),data);
 if(result.dryRun===true){atomic(path.join(base,'last-nightly-dry-report.json'),data);return;}
 if(!Number.isSafeInteger(result.plannedAt)||result.plannedAt<=0)return;
 atomic(path.join(base,'last-nightly-report.json'),data);atomic(path.join(base,'last-daily-report.json'),data);
 const folder=path.join(base,'daily-history');fs.mkdirSync(folder,{recursive:true});
 const day=new Date(result.plannedAt+8*3600000).toISOString().slice(0,10);atomic(path.join(folder,day+'.json'),data);
 const files=fs.readdirSync(folder).filter(name=>/^\d{4}-\d{2}-\d{2}\.json$/.test(name)).sort().reverse();
 for(const name of files.slice(7))fs.unlinkSync(path.join(folder,name));
}
module.exports={writeDailyReport};
