/** Bounded discovery plus previously verified stable conversation IDs. No message bodies retained. */
export async function discoverPrivate(account,known=[],pageLimit=5){
 const all=new Map(known.map(x=>[x.conversationId,{...x}]));let complete=true,readErrors=0,pages=0;
 try{for(const friend of await account.getFriendList()){const a=friend.address;if(a.conversationType===1&&a.threadId)all.set(a.threadId,{...all.get(a.threadId),conversationId:a.threadId,conversationShortId:a.conversationShortId||'0',inboxType:a.inboxType,displayName:friend.remark||friend.nickname||''});}}catch{readErrors++;complete=false;}
 for(const inboxType of [0,1]){let cursor='0';const seen=new Set();const limit=inboxType===0?1:pageLimit;
  for(let page=0;page<limit;page++){
   let response;try{response=await account.im.listThreads({inboxType,cursor});}catch{readErrors++;complete=false;break;}pages++;
   if(response.statusCode!==0){readErrors++;complete=false;break;}
   for(const row of response.conversations??[]){if(row.conversationType===1&&!row.isGroup&&row.conversationId){const prev=all.get(row.conversationId);all.set(row.conversationId,{...prev,conversationId:row.conversationId,conversationShortId:row.conversationShortId||prev?.conversationShortId||'0',inboxType:row.inboxType??inboxType,displayName:row.name||prev?.displayName||''});}}
   for(const row of response.threads??[]){if(row.conversationType===1&&row.threadId){const prev=all.get(row.threadId);all.set(row.threadId,{...prev,conversationId:row.threadId,conversationShortId:row.conversationShortId||prev?.conversationShortId||'0',inboxType:row.inboxType??inboxType,displayName:row.peer?.nickname||prev?.displayName||'',peerUid:row.peer?.uid||prev?.peerUid});}}
   if(!response.hasMore)break;if(!response.cursor||response.cursor===cursor||seen.has(response.cursor)||page===limit-1){complete=false;break;}seen.add(cursor);cursor=response.cursor;
  }
 }
 return {conversations:[...all.values()],complete,readErrors,pages};
}
