import {classifyOfficialStreak} from './streak-policy.mjs';
export async function readOfficialStreak(im,address,now=new Date()) {
 const unknown={readSucceeded:false,hasExistingStreak:false};
 const response=await im.getConversationInfos([address]);
 if(response.statusCode!==0)return unknown;
 const rows=response.conversations?.filter(x=>x.conversationId===address.threadId);
 if(rows?.length!==1)return unknown;
 const row=rows[0];
 if(row.conversationType!==1||row.isGroup||!/^\d+$/.test(row.conversationShortId)||row.conversationShortId==='0')return unknown;
 if(address.conversationShortId!=='0'&&address.conversationShortId!==row.conversationShortId)return unknown;
 const risk=row.settingExt;if((risk?.['a:sky_eye_dialog']&&risk['a:sky_eye_dialog']!=='{}')||(risk?.['a:sky_eye_dialog_list']&&risk['a:sky_eye_dialog_list']!=='[]'))return unknown;
 const raw=row.coreExt?.['a:consecutive_chat_data'];if(typeof raw!=='string'||raw.length>20000)return unknown;
 const state=classifyOfficialStreak(raw,now.getTime()/1000);let existing=false;
 if(state!=='unknown'){
  const window=JSON.parse(raw).flame_infos.find(x=>x.start<=now.getTime()/1000&&now.getTime()/1000<x.end);
  const days=Number(window?.real_days??window?.days);
  existing=Number.isSafeInteger(days)&&days>0&&days<=100000;
 }
 return {readSucceeded:true,hasExistingStreak:existing,raw,state,address:{...address,conversationShortId:row.conversationShortId}};
}
