const SELF = 'https://www.douyin.com/aweme/v1/web/user/profile/self/';
export function signSessionCheck({userAgent, values = {}}) {
  if (typeof userAgent !== 'string' || !userAgent.startsWith('Mozilla/5.0 ') || userAgent.length > 1000) throw new Error('invalid_user_agent');
  const url = new URL(SELF);
  for (const [key, value] of Object.entries({device_platform:'webapp',aid:'6383',channel:'channel_pc_web',
      browser_platform:'Linux x86_64',screen_width:'1024',screen_height:'768'})) url.searchParams.set(key,value);
  for (const key of ['msToken','verifyFp','fp','uifid']) {
    const value = values[key];
    if (typeof value === 'string' && value.length <= 2048 && !/[\r\n]/u.test(value)) url.searchParams.set(key,value);
  }
  return nativeSignedUrl(url,{userAgent});
}
export function classifySessionCheck({httpStatus, body}) {
  const unknown = {status:'session-unknown',authenticationConfirmed:false,accountMatchConfirmed:false,responseKind:'unconfirmed',profileShape:'missing'};
  if (httpStatus !== 200 || typeof body !== 'string' || body.length > 262144) return unknown;
  try {
    const value = JSON.parse(body);
    unknown.responseKind='json';
    const code = value?.status_code;
    if ((typeof code==='number' && Number.isSafeInteger(code) && Math.abs(code)<=2147483647) || (typeof code==='string' && /^-?\d{1,9}$/u.test(code))) unknown.platformStatus=String(code);
    if (value && Object.hasOwn(value,'user')) unknown.profileShape=value.user===null?'null':typeof value.user==='object'?'object-unconfirmed':'other';
    if (!value || ![0,'0'].includes(value.status_code) || !value.user || Array.isArray(value.user)) return unknown;
    const uid = value.user.uid;
    // Numeric JSON IDs may already have lost precision. Do not export any identifier.
    if (typeof uid !== 'string' || !/^[1-9]\d{0,29}$/u.test(uid)) return unknown;
    return {...unknown,status:'session-profile-readable',authenticationConfirmed:true,profileShape:'readable'};
  } catch { return {...unknown,responseKind:'non-json'}; }
}
