package app.local.spark.background;
import java.time.*;
/** Fixed Beijing civil time; independent of phone timezone and DST. */
public final class DailyTime {
    private static final ZoneOffset BEIJING=ZoneOffset.ofHours(8);
    public static long next22(long now){
        OffsetDateTime current=Instant.ofEpochMilli(now).atOffset(BEIJING);
        OffsetDateTime target=current.toLocalDate().atTime(22,0).atOffset(BEIJING);
        if(!target.isAfter(current))target=target.plusDays(1);
        return target.toInstant().toEpochMilli();
    }
}
