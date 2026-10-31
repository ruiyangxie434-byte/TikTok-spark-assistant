package app.local.spark.background;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
/** Daily background alarm request; the OEM may batch delivery. No alarm-clock indicator. */
public final class DailySchedule {
    static final String TEST="app.local.spark.background.ALARM_TEST";
    static final String DAILY="app.local.spark.background.DAILY";
    static PendingIntent pending(Context c,String action,long at){return PendingIntent.getBroadcast(c,TEST.equals(action)?22:23,new Intent(c,ScheduleReceiver.class).setAction(action).putExtra("plannedAt",at),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static boolean exactAllowed(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static void schedule(Context c,String action,long at){if(!exactAllowed(c))throw new SecurityException("ExactAlarmPermissionRequired");c.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(c,action,at));}
    static void cancelTest(Context c){c.getSystemService(AlarmManager.class).cancel(pending(c,TEST,0));record(c,"alarm-cancelled",0,0,null);}
    static void test(Context c){long at=System.currentTimeMillis()+15000;try{schedule(c,TEST,at);record(c,"alarm-scheduled",at,0,null);}catch(SecurityException error){record(c,"alarm-permission-required",at,0,null);}}
    static void enable(Context c){try{JSONObject data=new JSONObject(new String(Files.readAllBytes(new File(c.getFilesDir(),"state-read-private.json").toPath()),StandardCharsets.UTF_8));if(data.optString("accountId").isEmpty()||data.getJSONArray("conversations").length()==0)throw new IllegalStateException("NoVerifiedStreakList");long at=DailyTime.next22(System.currentTimeMillis());schedule(c,DAILY,at);c.getSharedPreferences("schedule",0).edit().putBoolean("enabled",true).putLong("nextAt",at).apply();}catch(RuntimeException error){throw error;}catch(Exception error){throw new IllegalStateException("NoVerifiedStreakList");}}
    static void disable(Context c){c.getSharedPreferences("schedule",0).edit().putBoolean("enabled",false).remove("nextAt").apply();c.getSystemService(AlarmManager.class).cancel(pending(c,DAILY,0));}
    static void restore(Context c){if(c.getSharedPreferences("schedule",0).getBoolean("enabled",false))try{{long at=DailyTime.next22(System.currentTimeMillis());schedule(c,DAILY,at);c.getSharedPreferences("schedule",0).edit().putLong("nextAt",at).apply();}}catch(SecurityException error){c.getSharedPreferences("schedule",0).edit().putBoolean("enabled",false).apply();record(c,"alarm-permission-required",0,0,null);}}
    static void record(Context c,String state,long planned,long actual,String reason){try{JSONObject r=new JSONObject();r.put("status",state);r.put("plannedAt",planned);r.put("firedAt",actual);r.put("reason",reason);r.put("sentMessages",0);r.put("automaticTaskEnabled",false);File temp=new File(c.getFilesDir(),"alarm-report.tmp");Files.write(temp.toPath(),r.toString().getBytes(StandardCharsets.UTF_8));Files.move(temp.toPath(),new File(c.getFilesDir(),"alarm-report.json").toPath(),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ignored){}}
}
