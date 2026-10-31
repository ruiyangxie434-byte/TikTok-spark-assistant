package app.local.spark.background;
import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** One native Node instance per separate process, with a bounded foreground job. */
public final class EngineService extends Service {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private volatile boolean started,finished;
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(started) return START_NOT_STICKY; started=true;
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("engine","本地引擎检查",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        boolean isConnection="connect".equals(intent.getStringExtra("mode"));
        boolean isSend="send".equals(intent.getStringExtra("mode")),isRead="read".equals(intent.getStringExtra("mode")),isNightly="nightly".equals(intent.getStringExtra("mode"));
        Notification n=new Notification.Builder(this,"engine").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle(isConnection?"火花助手：账号连接":isSend?"火花助手：单人续火花测试":isNightly?"火花助手：每日续火花":isRead?"火花助手：只读状态检查":"火花助手：本地后台检查").setContentText(isConnection?"等待首次授权；不发送消息":isSend?"先刷新状态，仅尝试发送一次表情":isNightly?"按最新状态检查，跳过今天已续的好友":isRead?"恢复助手会话；仅读取火花状态":"不登录、不读取消息、不发送").setContentIntent(open).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34) startForeground(4,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC); else startForeground(4,n);
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SparkBackground:native-check"); wake.acquire("nightly".equals(intent.getStringExtra("mode"))?310000:"connect".equals(intent.getStringExtra("mode"))?550000:"read".equals(intent.getStringExtra("mode"))?160000:"send".equals(intent.getStringExtra("mode"))?95000:65000);
        report("engine-running",null);
        handler.postDelayed(() -> { report("engine-timeout",null); finishJob(); },"nightly".equals(intent.getStringExtra("mode"))?305000:"connect".equals(intent.getStringExtra("mode"))?545000:"read".equals(intent.getStringExtra("mode"))?155000:"send".equals(intent.getStringExtra("mode"))?90000:60000);
        new Thread(() -> {
            try {
                File dir=new File(getFilesDir(),"engine"); if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException();
                boolean connect="connect".equals(intent.getStringExtra("mode"));
                if(connect||"send".equals(intent.getStringExtra("mode"))||"nightly".equals(intent.getStringExtra("mode"))){JSONObject config=new JSONObject();config.put("token",intent.getStringExtra("token"));config.put("conversationId",intent.getStringExtra("conversationId"));config.put("enabled",getSharedPreferences("schedule",0).getBoolean("enabled",false));config.put("dryRun",intent.getBooleanExtra("dryRun",false));config.put("plannedAt",intent.getLongExtra("plannedAt",0));config.put("probeOnly",intent.getBooleanExtra("probeOnly",false));Files.write(new File(getFilesDir(),"engine-config.json").toPath(),config.toString().getBytes(StandardCharsets.UTF_8));}
                File script=new File(dir,"main.cjs");
                try(InputStream in=getAssets().open(connect?"engine-connect.cjs":"read".equals(intent.getStringExtra("mode"))?"engine-read.cjs":"send".equals(intent.getStringExtra("mode"))?"engine-send.cjs":"nightly".equals(intent.getStringExtra("mode"))?"engine-nightly.cjs":"fixture".equals(intent.getStringExtra("mode"))?"engine-fixture.cjs":"engine-selftest.cjs"); OutputStream out=new FileOutputStream(script)) {
                    byte[] block=new byte[4096];int count;while((count=in.read(block))!=-1)out.write(block,0,count);
                }
                File policy=new File(dir,"policy");policy.mkdirs();for(String name:new String[]{"streak-policy.mjs","attempt-ledger.mjs","streak-runner.mjs","official-streak-adapter.mjs","discover-private.mjs"}){try(InputStream in=getAssets().open(name);OutputStream out=new FileOutputStream(new File(policy,name))){byte[] block=new byte[4096];int count;while((count=in.read(block))!=-1)out.write(block,0,count);}}
                File sdk=new File(dir,"protocol");sdk.mkdirs();
                try(java.util.zip.ZipInputStream archive=new java.util.zip.ZipInputStream(getAssets().open("protocol.zip"))) {
                    java.util.zip.ZipEntry entry;
                    while((entry=archive.getNextEntry())!=null) {
                        File target=new File(sdk,entry.getName());
                        if(!target.getCanonicalPath().startsWith(sdk.getCanonicalPath()+File.separator))throw new IOException("Invalid asset path");
                        if(entry.isDirectory()) {target.mkdirs();continue;}
                        target.getParentFile().mkdirs();
                        try(OutputStream out=new FileOutputStream(target)){byte[] b=new byte[8192];int zipCount;while((zipCount=archive.read(b))!=-1)out.write(b,0,zipCount);}
                    }
                }
                int code=NodeEngine.start(script.getAbsolutePath());
                if(code!=0) report("engine-error","native-exit");
                else {
                    JSONObject result=new JSONObject(new String(Files.readAllBytes(new File(getFilesDir(),"engine-report.json").toPath()),StandardCharsets.UTF_8));
                    Log.i("SparkEngine",result.toString());
                }
            } catch(Throwable error) { report("engine-error",error.getClass().getSimpleName()); }
            handler.post(() -> finishJob());
        },"native-node-engine").start();
        return START_NOT_STICKY;
    }
    private synchronized void report(String state,String reason) {
        if(finished)return;
        try {
            JSONObject r=new JSONObject();r.put("status",state);r.put("reason",reason);r.put("sentMessages",0);r.put("automaticTaskEnabled",false);
            File temp=new File(getFilesDir(),"engine-report.tmp");Files.write(temp.toPath(),r.toString().getBytes(StandardCharsets.UTF_8));
            java.nio.file.Files.move(temp.toPath(),new File(getFilesDir(),"engine-report.json").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Log.i("SparkEngine",r.toString());
        }catch(Exception ignored){}
    }
    private synchronized void finishJob() {
        if(finished)return;finished=true;handler.removeCallbacksAndMessages(null);
        if(wake!=null && wake.isHeld())wake.release();
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        if(Application.getProcessName().endsWith(":engine"))android.os.Process.killProcess(android.os.Process.myPid());
    }
    @Override public void onTimeout(int id){ report("engine-timeout",null);finishJob(); }
    @Override public void onTimeout(int id,int type){ report("engine-timeout",null);finishJob(); }
    @Override public void onDestroy(){finishJob();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}

