import {decideStreakSend,streakAttemptKey,classifyOfficialStreak,beijingDateKey} from './streak-policy.mjs';

/** Adapter contract: readConversation performs an official network read, never a UI cache read. */
export async function runStreakBatch({accountId,conversationIds,readConversation,sendEmoji,ledger,readTodayActivity,dryRun=false,now=()=>new Date()}) {
  const counts={done:0,skipped:0,attempted:0,acknowledged:0,verifiedDone:0,unknown:0,alreadySentToday:0,historyUnknown:0,wouldSend:0};
  for(const conversationId of new Set(conversationIds)) {
    let current;
    try{current=await readConversation(conversationId);}catch{counts.unknown++;continue;}
    const at=now();
    try {
      const key=streakAttemptKey(accountId,conversationId,at);
      const prior=ledger.has(key);
      const decision=decideStreakSend({accountId,conversationId,raw:current?.raw,readSucceeded:current?.readSucceeded,
        hasExistingStreak:current?.hasExistingStreak,attemptedKeys:new Set(prior?[key]:[]),now:at});
      if(!decision.send){if(decision.state==='done')counts.done++;else if(decision.state==='unknown')counts.unknown++;else counts.skipped++;continue;}
      let activity;
      try{activity=await readTodayActivity(conversationId,at);}catch{}
      const checkedAt=now();
      if(beijingDateKey(checkedAt)!==beijingDateKey(at)||activity?.dateKey!==beijingDateKey(checkedAt)||!['self-sent','not-sent'].includes(activity?.state)){
        counts.historyUnknown++;counts.unknown++;continue;
      }
      if(activity.state==='self-sent'){counts.alreadySentToday++;counts.skipped++;continue;}
      if(dryRun){counts.wouldSend++;continue;}
      if(!ledger.claim(key)){counts.skipped++;continue;}
      counts.attempted++;
      const result=await sendEmoji(conversationId,'😀');
      if(result?.acknowledged===true)counts.acknowledged++;
      const verification=await readConversation(conversationId);
      if(verification?.readSucceeded===true&&classifyOfficialStreak(verification.raw,now().getTime()/1000)==='done')counts.verifiedDone++;
    }catch{counts.unknown++;}
  }
  return counts;
}
