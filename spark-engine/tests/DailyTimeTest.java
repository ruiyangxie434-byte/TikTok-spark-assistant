package app.local.spark.background;
import java.time.Instant;import java.util.TimeZone;
public final class DailyTimeTest {
 static int checks;
 static long at(String value){return Instant.parse(value).toEpochMilli();}
 static void equal(long value,String expected){checks++;if(value!=at(expected))throw new AssertionError(value+" != "+expected);}
 static void check(boolean value){checks++;if(!value)throw new AssertionError();}
 public static void main(String[] args){for(String zone:new String[]{"UTC","America/New_York","Asia/Shanghai"}){
  TimeZone.setDefault(TimeZone.getTimeZone(zone));
  equal(DailyTime.next(at("2026-11-05T13:59:59.999Z"),22,0),"2026-11-05T14:00:00Z");
  equal(DailyTime.next(at("2026-11-05T14:00:00Z"),22,0),"2026-11-06T14:00:00Z");
  equal(DailyTime.next(at("2026-11-05T01:00:00Z"),9,30),"2026-11-05T01:30:00Z");
  equal(DailyTime.next(at("2026-11-05T01:30:00Z"),9,30),"2026-11-06T01:30:00Z");
  equal(DailyTime.next(at("2026-11-05T15:59:59Z"),0,0),"2026-11-05T16:00:00Z");
  equal(DailyTime.next(at("2026-12-31T15:00:00Z"),0,5),"2026-12-31T16:05:00Z");
  equal(DailyTime.next(at("2028-02-28T15:00:00Z"),9,0),"2028-02-29T01:00:00Z");
  equal(DailyTime.restore(at("2026-11-05T15:00:00Z"),at("2026-11-05T14:00:00Z"),22,0),"2026-11-05T14:00:00Z");
  equal(DailyTime.restore(at("2026-11-05T13:00:00Z"),at("2026-11-01T14:00:00Z"),22,0),"2026-11-05T14:00:00Z");
  equal(DailyTime.restore(at("2026-11-05T16:00:00Z"),at("2026-11-05T14:00:00Z"),22,0),"2026-11-06T14:00:00Z");
  equal(DailyTime.restore(at("2026-11-05T13:00:00Z"),at("2026-11-05T14:00:00Z"),21,30),"2026-11-05T13:30:00Z");
  equal(DailyTime.trigger(at("2026-11-05T15:00:00Z"),at("2026-11-05T14:00:00Z")),"2026-11-05T15:00:05Z");
  equal(DailyTime.retry(at("2026-11-05T15:00:00Z"),at("2026-11-05T14:00:00Z")),"2026-11-05T15:10:00Z");
  check(DailyTime.retry(at("2026-11-05T15:55:00Z"),at("2026-11-05T14:00:00Z"))==0);
  check(DailyTime.retry(at("2026-11-05T13:55:00Z"),at("2026-11-05T14:00:00Z"))==0);
  check(DailyTime.label(0,5).equals("00:05"));check(DailyTime.label(23,59).equals("23:59"));
 }
 for(int[] value:new int[][]{{-1,0},{24,0},{0,-1},{0,60}}){boolean rejected=false;try{DailyTime.next(0,value[0],value[1]);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
 System.out.println("DailyTime: "+checks+" configurable-time, recovery and boundary checks passed");}
}
