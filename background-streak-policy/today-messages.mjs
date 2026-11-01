import {beijingDateKey} from './streak-policy.mjs';

const positiveId=value=>typeof value==='string'&&/^[1-9]\d*$/.test(value);
// The SDK retains wire timestamps. Accept unambiguous Unix seconds or milliseconds only.
function timestamp(value) {
 if(!Number.isSafeInteger(value))return NaN;
 if(value>=946684800&&value<4102444800)return value*1000;
 if(value>=946684800000&&value<4102444800000)return value;
 return NaN;
}

/** Read server history only; never mark read, create conversations or store message bodies. */
export async function readTodayMessages(im,address,selfUids,now=new Date()) {
 const dateKey=beijingDateKey(now),unknown={state:'unknown',dateKey};
 const fail=reason=>({...unknown,reason});
 const self=new Set((selfUids??[]).filter(positiveId));
 if(!self.size||address?.conversationType!==1||!address.threadId||!positiveId(address.conversationShortId))return fail('identity-or-address');
 const midnight=Date.parse(dateKey+'T00:00:00+08:00'),time=now.getTime();
 // Older pages can contain messages in ascending order; validate the boundary between pages.
 let cursor,previousTime=Infinity;const seen=new Set();
 try {
  for(let page=0;page<5;page++) {
   const response=await im.getMessages({...address,cursor,count:50,direction:'older'});
   if(response.statusCode!==0)return fail('server-status-'+String(Number.isSafeInteger(response.statusCode)?response.statusCode:'missing'));
   if(response.direction!=='older'||typeof response.hasMore!=='boolean'||!Array.isArray(response.messages))return fail('response-shape');
   let crossedMidnight=false,oldestTime=Infinity;
   for(const message of response.messages) {
    const at=timestamp(message.createTime);
    if(message.threadId!==address.threadId||(message.conversationShortId&&message.conversationShortId!==address.conversationShortId)||
       (message.conversationType!==undefined&&message.conversationType!==1))return fail('message-address');
    if(!Number.isFinite(at))return fail('message-time');
    if(at>time)return fail('future-message');
    if(at>previousTime)return fail('message-order');
    oldestTime=Math.min(oldestTime,at);
    if(at<midnight){crossedMidnight=true;continue;}
    if(!Number.isSafeInteger(message.msgType)||message.msgType<=0)return fail('message-type');
    // SDK read-state excludes these tips, recalls and deleted messages from last-sent messages.
    if([1,1001,1002,1010,40001].includes(message.msgType)||message.msgType>=50000||message.deleted||message.ext?.['s:is_recalled']==='true')continue;
    if(!positiveId(message.senderUid)||!positiveId(message.msgId)||message.status!==0)return fail('message-identity-or-status');
    if(self.has(message.senderUid))return {state:'self-sent',dateKey};
   }
   previousTime=oldestTime;
   if(crossedMidnight||response.hasMore===false)return {state:'not-sent',dateKey};
   const next=response.cursor;
   if(!response.messages.length||!positiveId(next)||next===cursor||seen.has(next)||(cursor&&BigInt(next)>=BigInt(cursor)))return fail('history-cursor');
   seen.add(next);cursor=next;
  }
 }catch{return fail('history-read-error');}
 return fail('history-page-limit');
}
