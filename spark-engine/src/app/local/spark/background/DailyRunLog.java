package app.local.spark.background;
import android.content.Context;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
/** Seven days of local aggregate automatic-run reports; rehearsals never overwrite them. */
public final class DailyRunLog {
 static JSONObject read(File file){return read(file,20000);}
 static JSONObject read(File file,long limit){try{if(file.length()>limit)return new JSONObject();return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
 static void atomic(File file,String data)throws IOException{file.getParentFile().mkdirs();File temp=new File(file.getParentFile(),file.getName()+".java.tmp");Files.write(temp.toPath(),data.getBytes(StandardCharsets.UTF_8));Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
 static void write(Context c,JSONObject report){try{
  long planned=report.optLong("plannedAt");if(report.optBoolean("dryRun")||planned<=0)return;
  String data=report.toString();atomic(new File(c.getFilesDir(),"last-daily-report.json"),data);
  File folder=new File(c.getFilesDir(),"daily-history");atomic(new File(folder,DailyTime.day(planned)+".json"),data);
  File[] files=folder.listFiles((dir,name)->name.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}\\.json"));
  if(files!=null){Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));for(int i=7;i<files.length;i++)Files.deleteIfExists(files[i].toPath());}
 }catch(Exception ignored){}}
 static JSONObject latest(Context c){JSONObject report=read(new File(c.getFilesDir(),"last-daily-report.json"));if(report.length()==0){JSONObject old=read(new File(c.getFilesDir(),"last-nightly-report.json"));if(!old.optBoolean("dryRun",true))report=old;}return report;}
 static JSONArray history(Context c){JSONArray list=new JSONArray();File[] files=new File(c.getFilesDir(),"daily-history").listFiles((dir,name)->name.endsWith(".json"));if(files!=null){Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));for(int i=0;i<Math.min(7,files.length);i++)list.put(read(files[i]));}return list;}
}
