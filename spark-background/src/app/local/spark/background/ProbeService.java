package app.local.spark.background;
import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.util.Log;
import android.webkit.*;
import org.json.JSONObject;
public final class ProbeService extends Service {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private OffscreenPage offscreen; private PowerManager.WakeLock wake; private WebView web; private String script; private boolean finished,fixture,evaluating;
    private final Runnable check=() -> inspect();
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(web!=null || script!=null) return START_NOT_STICKY;
        fixture=intent!=null && intent.getBooleanExtra("fixture",false);
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("checks","连接检查",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"checks").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("火花助手正在检查连接").setContentText("本次不发送任何消息").setContentIntent(open).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34) startForeground(2,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE); else startForeground(2,n);
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SparkBackground:check"); wake.acquire(115000); save("starting");
        try {script=WebSession.script(this);} catch(Exception e) {finish("engine-error",null);return START_NOT_STICKY;}
        handler.postDelayed(() -> begin(),intent!=null && intent.getBooleanExtra("delayed",false)?10000:300);
        handler.postDelayed(() -> finish("timeout",null),105000);
        return START_NOT_STICKY;
    }
    private void begin() {
        if(finished) return;
        try {
            offscreen=new OffscreenPage(this,handler); web=offscreen.web;
            web.setWebViewClient(new WebViewClient() { @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) {return !WebSession.https(r.getUrl().toString());} });
            save("loading");
            if(fixture) {
                web.getSettings().setBlockNetworkLoads(true);
                String html="<!doctype html><script>const t=Math.floor(Date.now()/1000); const item=(type,state)=>({type,coreInfo:{ext:{'a:consecutive_chat_data':JSON.stringify({flame_infos:[{start:t-60,end:t+60,state}]})}}}); globalThis.conversationStore={conversationMap:new Map([['a',item(1,1)],['b',item(1,2)],['g',item(2,2)]])};</script>";
                web.loadDataWithBaseURL(WebSession.CHAT,html,"text/html","UTF-8",null);
            } else web.loadUrl(WebSession.CHAT);
            handler.postDelayed(check,1500);
        } catch(Exception e) {finish("engine-error",null);}
    }
    private void inspect() {
        if(finished || web==null || evaluating) return; evaluating=true;
        try {
            web.evaluateJavascript(script,value -> {
                evaluating=false; if(finished) return;
                try {
                    JSONObject r=new JSONObject(value);
                    if(r.optBoolean("ready")) {
                        if(fixture) {
                            boolean good=r.optInt("privateCount")==2 && r.optInt("candidateCount")==2 && r.optInt("done")==1 && r.optInt("pending")==1;
                            finish(good?"engine-check-passed":"engine-check-failed",r);
                        } else finish(r.optInt("privateCount")>0?"page-memory-readable":"no-conversations",r);
                        return;
                    }
                } catch(Exception ignored) {}
                save("waiting");handler.postDelayed(check,2000);
            });
        } catch(Exception e) {evaluating=false;finish("engine-error",null);}
    }
    private void save(String status) {try {JSONObject r=new JSONObject();r.put("status",status);write(r);} catch(Exception ignored) {}}
    private void write(JSONObject r) {getSharedPreferences("probe",0).edit().putString("report",r.toString()).apply();}
    private void finish(String status,JSONObject data) {
        if(finished) return; finished=true;
        try {
            JSONObject r=data==null?new JSONObject():data; r.put("status",status);r.put("mode",fixture?"fixture":"page-memory");r.put("readAt",System.currentTimeMillis());r.put("foregroundActivityVisible",MainActivity.visible);r.put("sentMessages",0);r.put("automaticTaskEnabled",false);
            write(r);Log.i("SparkBackground",r.toString());
        } catch(Exception ignored) {}
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onTimeout(int id) {finish("timeout",null);}
    @Override public void onTimeout(int id,int type) {finish("timeout",null);}
    @Override public void onDestroy() {finished=true;handler.removeCallbacksAndMessages(null);if(offscreen!=null) {CookieManager.getInstance().flush();offscreen.close();offscreen=null;web=null;} if(wake!=null && wake.isHeld()) wake.release();super.onDestroy();}
    @Override public IBinder onBind(Intent intent) {return null;}
}




