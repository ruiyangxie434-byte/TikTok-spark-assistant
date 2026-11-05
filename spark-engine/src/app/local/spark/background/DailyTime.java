package app.local.spark.background;
import java.time.*;
import java.util.Locale;
/** Fixed Beijing civil time, independent of the phone timezone. */
public final class DailyTime {
    private static final ZoneOffset BEIJING=ZoneOffset.ofHours(8);
    public static void validate(int hour,int minute){if(hour<0||hour>23||minute<0||minute>59)throw new IllegalArgumentException("InvalidDailyTime");}
    public static String label(int hour,int minute){validate(hour,minute);return String.format(Locale.ROOT,"%02d:%02d",hour,minute);}
    public static String day(long at){return Instant.ofEpochMilli(at).atOffset(BEIJING).toLocalDate().toString();}
    public static long today(long now,int hour,int minute){validate(hour,minute);return Instant.ofEpochMilli(now).atOffset(BEIJING).toLocalDate().atTime(hour,minute).atOffset(BEIJING).toInstant().toEpochMilli();}
    public static long next(long now,int hour,int minute){long at=today(now,hour,minute);return at>now?at:at+86400000L;}
    /** Keep overdue work only within its original Beijing day; never backfill yesterday. */
    public static long restore(long now,long saved,int hour,int minute){
        if(saved==today(now,hour,minute))return saved;
        if(saved==next(now,hour,minute))return saved;
        return next(now,hour,minute);
    }
    public static long trigger(long now,long planned){return planned>now?planned:now+5000;}
    public static long retry(long now,long planned){long at=now+600000;return planned<=now&&day(planned).equals(day(at))?at:0;}
}
