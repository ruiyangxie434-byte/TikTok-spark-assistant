package app.local.spark.background;
import android.app.*;import android.content.*;import android.os.*;import org.json.JSONObject;import java.io.*;
/** User-selected Beijing time. Alarms remain subject to the phone's delivery policy. */
public final class DailySchedule {
 static final String TEST="app.local.spark.background.ALARM_TEST",DAILY="app.local.spark.background.DAILY",RETRY="app.local.spark.background.DAILY_RETRY",DEFER="app.local.spark.background.DEFER_DAILY";
 static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("schedule",0);}
 static int hour(Context c){return prefs(c).getInt("hour",22);}static int minute(Context c){return prefs(c).getInt("minute",0);}
 static String timeLabel(Context c){return DailyTime.label(hour(c),minute(c));}
 static PendingIntent pending(Context c,String action,long planned){return PendingIntent.getBroadcast(c,TEST.equals(action)?22:RETRY.equals(action)?24:23,new Intent(c,ScheduleReceiver.class).setAction(action).putExtra("plannedAt",planned),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static boolean exactAllowed(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
 static void schedule(Context c,String action,long trigger,long planned){if(!exactAllowed(c))throw new SecurityException("ExactAlarmPermissionRequired");c.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pending(c,action,planned));}
 static void arm(Context c,long planned,long trigger){schedule(c,DAILY,trigger,planned);prefs(c).edit().putLong("nextAt",planned).putLong("triggerAt",trigger).commit();}
 static void cancelRetry(Context c){c.getSystemService(AlarmManager.class).cancel(pending(c,RETRY,0));prefs(c).edit().remove("retryAt").remove("retryFor").remove("retriedFor").commit();}
 static void cancelTest(Context c){c.getSystemService(AlarmManager.class).cancel(pending(c,TEST,0));record(c,"alarm-cancelled",0,0,null);}
 static void test(Context c){long at=System.currentTimeMillis()+15000;try{schedule(c,TEST,at,at);record(c,"alarm-scheduled",at,0,null);}catch(SecurityException error){record(c,"alarm-permission-required",at,0,null);}}
 static void setTime(Context c,int hour,int minute){DailyTime.validate(hour,minute);long next=DailyTime.next(System.currentTimeMillis(),hour,minute);if(prefs(c).getBoolean("enabled",false))arm(c,next,next);prefs(c).edit().putInt("hour",hour).putInt("minute",minute).commit();cancelRetry(c);}
 static void enable(Context c){try{JSONObject data=DailyRunLog.read(new File(c.getFilesDir(),"state-read-private.json"),2000000);if(data.optString("accountId").isEmpty()||data.getJSONArray("conversations").length()==0)throw new IllegalStateException("NoVerifiedStreakList");long at=DailyTime.next(System.currentTimeMillis(),hour(c),minute(c));arm(c,at,at);prefs(c).edit().putBoolean("enabled",true).commit();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("NoVerifiedStreakList");}}
 static void disable(Context c){prefs(c).edit().putBoolean("enabled",false).remove("nextAt").remove("triggerAt").commit();c.getSystemService(AlarmManager.class).cancel(pending(c,DAILY,0));cancelRetry(c);}
 static void advance(Context c){if(!prefs(c).getBoolean("enabled",false))return;try{long at=DailyTime.next(System.currentTimeMillis(),hour(c),minute(c));arm(c,at,at);}catch(SecurityException e){recordDaily(c,"daily-permission-required",System.currentTimeMillis(),e.getClass().getSimpleName());}}
 static void restore(Context c){if(!prefs(c).getBoolean("enabled",false))return;long now=System.currentTimeMillis(),saved=prefs(c).getLong("nextAt",0);
  try{long planned=DailyTime.restore(now,saved,hour(c),minute(c));if(saved>0&&saved<now&&!DailyTime.day(saved).equals(DailyTime.day(now)))recordDaily(c,"daily-missed",saved,"stale-schedule-recovered");long trigger=DailyTime.trigger(now,planned),savedTrigger=prefs(c).getLong("triggerAt",0);if(saved==planned&&savedTrigger>now)trigger=savedTrigger;arm(c,planned,trigger);long retryAt=prefs(c).getLong("retryAt",0),retryFor=prefs(c).getLong("retryFor",0);if(retryFor>0&&DailyTime.day(retryFor).equals(DailyTime.day(now)))schedule(c,RETRY,Math.max(now+5000,retryAt),retryFor);}
  catch(SecurityException e){recordDaily(c,"daily-permission-required",now,e.getClass().getSimpleName());}
 }
 static void defer(Context c,long planned){long now=System.currentTimeMillis(),at=DailyTime.retry(now,planned);if(!prefs(c).getBoolean("enabled",false))return;if(at==0||prefs(c).getLong("retriedFor",0)==planned){recordDaily(c,"daily-service-busy",planned,"no-more-safe-retries");return;}
  try{schedule(c,RETRY,at,planned);prefs(c).edit().putLong("retryAt",at).putLong("retryFor",planned).putLong("retriedFor",planned).commit();recordDaily(c,"daily-deferred",planned,"engine-busy");}catch(SecurityException e){recordDaily(c,"daily-service-blocked",planned,e.getClass().getSimpleName());}
 }
 static void recordDaily(Context c,String status,long planned,String reason){try{JSONObject r=new JSONObject();r.put("status",status);r.put("plannedAt",planned);r.put("updatedAt",System.currentTimeMillis());r.put("reason",reason);r.put("dryRun",false);DailyRunLog.write(c,r);}catch(Exception ignored){}}
 static void record(Context c,String state,long planned,long actual,String reason){try{JSONObject r=new JSONObject();r.put("status",state);r.put("plannedAt",planned);r.put("firedAt",actual);r.put("reason",reason);r.put("sentMessages",0);r.put("automaticTaskEnabled",false);DailyRunLog.atomic(new File(c.getFilesDir(),"alarm-report.json"),r.toString());}catch(Exception ignored){}}
}
