package app.local.spark.background;
import android.content.*;
public final class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){String action=intent.getAction();
        if(DailySchedule.TEST.equals(action)){DailySchedule.record(c,"alarm-fired",intent.getLongExtra("plannedAt",0),System.currentTimeMillis(),null);try{c.startForegroundService(new Intent(c,EngineService.class).putExtra("mode","fixture"));}catch(RuntimeException error){DailySchedule.record(c,"alarm-service-blocked",intent.getLongExtra("plannedAt",0),System.currentTimeMillis(),error.getClass().getSimpleName());}return;}
        if(DailySchedule.DAILY.equals(action)){if(c.getSharedPreferences("schedule",0).getBoolean("enabled",false)){DailySchedule.restore(c);try{c.startForegroundService(new Intent(c,EngineService.class).putExtra("mode","nightly").putExtra("plannedAt",intent.getLongExtra("plannedAt",0)));}catch(RuntimeException error){DailySchedule.record(c,"nightly-service-blocked",intent.getLongExtra("plannedAt",0),System.currentTimeMillis(),error.getClass().getSimpleName());}}return;}
        DailySchedule.restore(c);
    }
}
