package app.local.spark.background;
import android.content.*;
public final class ScheduleReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent intent){String action=intent.getAction();long planned=intent.getLongExtra("plannedAt",0),now=System.currentTimeMillis();
  if(DailySchedule.TEST.equals(action)){DailySchedule.record(c,"alarm-fired",planned,now,null);try{c.startForegroundService(new Intent(c,EngineService.class).putExtra("mode","fixture"));}catch(RuntimeException e){DailySchedule.record(c,"alarm-service-blocked",planned,now,e.getClass().getSimpleName());}return;}
  if(DailySchedule.DEFER.equals(action)){DailySchedule.defer(c,planned);return;}
  if(DailySchedule.DAILY.equals(action)||DailySchedule.RETRY.equals(action)){
   if(!DailySchedule.prefs(c).getBoolean("enabled",false))return;
   boolean retry=DailySchedule.RETRY.equals(action);
   if(planned<=0||planned>now||planned!=DailySchedule.prefs(c).getLong(retry?"retryFor":"nextAt",0))return;
   if(retry)DailySchedule.prefs(c).edit().remove("retryFor").remove("retryAt").commit();else DailySchedule.advance(c);
   if(!DailyTime.day(planned).equals(DailyTime.day(now))){DailySchedule.recordDaily(c,"daily-missed",planned,"delivered-after-planned-day");return;}
   DailySchedule.recordDaily(c,"daily-fired",planned,null);
   try{c.startForegroundService(new Intent(c,EngineService.class).putExtra("mode","nightly").putExtra("plannedAt",planned));}catch(RuntimeException e){DailySchedule.recordDaily(c,"daily-service-blocked",planned,e.getClass().getSimpleName());}return;
  }
  DailySchedule.restore(c);
 }
}
