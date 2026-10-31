package app.local.spark.background;
import java.time.Instant;
import java.util.TimeZone;
public final class DailyTimeTest {
    private static void check(String now,String expected){long actual=DailyTime.next22(Instant.parse(now).toEpochMilli());if(actual!=Instant.parse(expected).toEpochMilli())throw new AssertionError(now);}
    public static void main(String[] args){for(String zone:new String[]{"UTC","America/New_York","Asia/Shanghai"}){TimeZone.setDefault(TimeZone.getTimeZone(zone));check("2026-10-31T13:59:59.999Z","2026-10-31T14:00:00Z");check("2026-10-31T14:00:00Z","2026-11-01T14:00:00Z");check("2026-10-31T16:00:00Z","2026-11-01T14:00:00Z");check("2026-12-31T15:00:00Z","2027-01-01T14:00:00Z");check("2028-02-28T15:00:00Z","2028-02-29T14:00:00Z");}System.out.println("DailyTime: 15 timezone/boundary checks passed");}
}
