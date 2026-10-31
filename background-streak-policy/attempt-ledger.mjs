import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';

/** Atomic durable claim: a crash or unknown send outcome never permits a retry. */
export function createAttemptLedger(directory) {
  fs.mkdirSync(directory,{recursive:true,mode:0o700});
  const file=key=>path.join(directory,createHash('sha256').update(key).digest('hex')+'.json');
  return {
    has(key){return fs.existsSync(file(key));},
    claim(key){
      let fd;
      try{fd=fs.openSync(file(key),'wx',0o600);}
      catch(error){if(error.code==='EEXIST')return false;throw error;}
      try{fs.writeFileSync(fd,JSON.stringify({key,status:'attempted'}));fs.fsyncSync(fd);}
      finally{fs.closeSync(fd);}
      // Persist the directory entry before callers may dispatch a message.
      const dir=fs.openSync(directory,'r');try{if(process.platform!=='win32')fs.fsyncSync(dir);}finally{fs.closeSync(dir);}
      return true;
    }
  };
}
