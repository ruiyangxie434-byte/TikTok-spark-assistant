package app.local.spark.background;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import org.json.JSONObject;
public final class MainActivity extends Activity { public static volatile boolean visible;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private final Runnable refresh = new Runnable() { public void run() { render(); handler.postDelayed(this, 1500); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView scroll = new ScrollView(this); LinearLayout page = new LinearLayout(this);
        page.setOrientation(1); page.setPadding(30, 28, 30, 40); scroll.addView(page); setContentView(scroll); getWindow().getInsetsController().setSystemBarsAppearance(android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        scroll.setOnApplyWindowInsetsListener((v, i) -> { android.graphics.Insets b = i.getInsets(android.view.WindowInsets.Type.systemBars()); v.setPadding(b.left,b.top,b.right,b.bottom); return i; });
        label(page, "火花助手·后台版", 25);
        label(page, "手机本地运行 · 每日22点检查", 17);
        label(page, "每天北京时间22:00，检查已识别的火花好友。今天已续上就跳过，只给确认待续的好友发一次普通表情；未知状态不发送。", 15);
        label(page, "首次使用需授权。日常任务由手机助手后台运行；会话失效时停止本次任务，需要在助手内重新授权。计划22:00执行；本机系统可能推迟到22:00–23:00。不显示闹钟标记。", 15);
        Switch daily=new Switch(this);daily.setText("每天北京时间22:00自动续火花");daily.setChecked(getSharedPreferences("schedule",0).getBoolean("enabled",false));page.addView(daily);daily.setOnCheckedChangeListener((v,on)->{try{if(on)DailySchedule.enable(this);else{DailySchedule.disable(this);stopService(new Intent(this,EngineService.class));}render();}catch(Exception error){daily.setChecked(false);Toast.makeText(this,"请先完成真实状态读取，并允许定时权限",Toast.LENGTH_LONG).show();}});
        button(page, "只读演练每日任务（不发消息）", () -> startForegroundService(new Intent(this,EngineService.class).putExtra("mode","nightly").putExtra("dryRun",true).putExtra("plannedAt",System.currentTimeMillis())));
        button(page, "检查定时唤醒（15秒后模拟，不发消息）", () -> {DailySchedule.test(this);render();});
        button(page, "定时所需系统权限", () -> startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+getPackageName()))));
        button(page, "查看好友火花状态（只读快照）", () -> startActivity(new Intent(this,StreakPreviewActivity.class)));
        button(page, "刷新真实火花状态（只读）", () -> startForegroundService(new Intent(this,EngineService.class).putExtra("mode","read")));
        button(page, "连接账号（首次授权，不发送）", () -> startActivity(new Intent(this,ConnectionActivity.class)));
        button(page, "检查本地后台引擎（不登录、不发送）", () -> startForegroundService(new android.content.Intent(this,EngineService.class)));
        
        button(page, "停止本次检查", () -> { stopService(new Intent(this, ProbeService.class));stopService(new Intent(this,EngineService.class));DailySchedule.cancelTest(this);try{java.nio.file.Files.write(new java.io.File(getFilesDir(),"engine-report.json").toPath(),"{\"status\":\"engine-cancelled\",\"sentMessages\":0,\"automaticTaskEnabled\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){} getSharedPreferences("probe",0).edit().putString("report","{\"status\":\"cancelled\"}").apply(); render(); });
        status = label(page, "", 16); status.setTextIsSelectable(true); render();
        if(getIntent().getBooleanExtra("nightly_dry_run",false))handler.postDelayed(()->startForegroundService(new Intent(this,EngineService.class).putExtra("mode","nightly").putExtra("dryRun",true).putExtra("plannedAt",System.currentTimeMillis())),300);
        if(getIntent().getBooleanExtra("state_read_test",false))handler.postDelayed(()->startForegroundService(new Intent(this,EngineService.class).putExtra("mode","read")),300);
        if(getIntent().getBooleanExtra("alarm_test",false))handler.postDelayed(()->DailySchedule.test(this),300);
        if(getIntent().getBooleanExtra("protocol_probe",false))handler.postDelayed(()->startForegroundService(new Intent(this,EngineService.class).putExtra("mode","connect").putExtra("token",java.util.UUID.randomUUID().toString()).putExtra("probeOnly",true)),300);
        if(getIntent().getBooleanExtra("connect_test",false))handler.postDelayed(()->startActivity(new Intent(this,ConnectionActivity.class)),300);
        if (getIntent().getBooleanExtra("native_engine_test",false)) handler.postDelayed(() -> startForegroundService(new android.content.Intent(this,EngineService.class)),500);
        if (getIntent().getBooleanExtra("self_test",false)) handler.postDelayed(() -> probe(true,true),500);
    }
    private void probe(boolean fixture, boolean delayed) {
        if (!fixture && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
        try { startForegroundService(new Intent(this, ProbeService.class).putExtra("fixture",fixture).putExtra("delayed",delayed)); }
        catch (Exception e) { status.setText("未能启动检查：" + e.getClass().getSimpleName()); }
    }
    private void render() {
        if (status == null) return;
        try {
            JSONObject r = new JSONObject(getSharedPreferences("probe",0).getString("report","{}"));
            String s = r.optString("status","none"), text;
            switch(s) {
                case "none": text="尚未检查"; break;
                case "starting": text="检查即将开始…"; break;
                case "loading": text="正在连接抖音网页…"; break;
                case "waiting": text="等待聊天数据…"; break;
                case "session-checking": text="正在验证助手已保存的会话…"; break;
                case "session-profile-readable": text="助手旧会话可读取账号资料。仍需确认原版抖音保持在线，以及两端是否为同一账号。"; break;
                case "session-unknown": text="无法确认旧会话是否有效；可能涉及签名、验证或会话状态。未重新登录。"; break;
                case "no-local-session": text="助手本机没有可用于检查的会话凭据。未发起登录。"; break;
                case "cancelled": text="本次检查已停止"; break;
                case "timeout": text="暂未读到聊天数据，原因尚未确认。请勿反复重新登录。"; break;
                case "engine-check-passed": text="手机本地运行检查通过。尚未验证抖音账号数据。"; break;
                case "page-memory-readable": text="已读到页面中的部分会话数据。还需与抖音当天实际状态核对，未开启发送。"; break;
                case "no-conversations": text="页面已响应，但未读到会话。暂不重新登录。"; break;
                default: text="检查未完成，请重试。";
            }
            if (!"fixture".equals(r.optString("mode")) && r.has("privateCount")) {
                text += "\n读取到私聊：" + r.optInt("privateCount") + "\n火花候选：" + r.optInt("candidateCount");
                text += "\n待核对：已续 " + r.optInt("done") + "，待续 " + r.optInt("pending") + "，其他 " + (r.optInt("recover")+r.optInt("inactive")+r.optInt("unknown"));
            }
            java.io.File engineReport=new java.io.File(getFilesDir(),"engine-report.json");
            if(engineReport.exists()) {
                byte[] data=java.nio.file.Files.readAllBytes(engineReport.toPath());
                JSONObject engine=new JSONObject(new String(data,java.nio.charset.StandardCharsets.UTF_8));
                String state=engine.optString("status");
                text="后台引擎："+("engine-passed".equals(state)?"本地计时与网络检查通过":"engine-running".equals(state)?"检查中…":"检查未通过");
                text+="\n此检查不代表抖音登录或发送已接通。";
                if("engine-cancelled".equals(state))text="本次检查已停止";
                else if("policy-fixture-passed".equals(state))text="模拟检查通过：已续跳过、未知跳过、重复拦截。没有发送真实消息。";
                else if(state.startsWith("nightly-")){text="每日任务："+("nightly-dry-complete".equals(state)?"只读演练完成":"nightly-complete".equals(state)?"本次任务完成":"处理中或未完成，请保留登录");text+="\n已续跳过 "+engine.optInt("done")+"，其他跳过 "+engine.optInt("skipped")+"，未知 "+engine.optInt("unknown");text+="\n"+(engine.optBoolean("dryRun")?"本次可发送 "+engine.optInt("wouldSend")+"（演练没有发送）":"服务器确认发送 "+engine.optInt("sentMessages")+"，官方点亮确认 "+engine.optInt("verifiedDone"));}
                else if(state.startsWith("send-")){text="发送测试："+("send-renewal-confirmed".equals(state)?"官方状态已确认今天续上":"send-acknowledged-pending".equals(state)?"服务器已接收表情，火花尚未确认点亮":"send-skipped-already-done".equals(state)?"今天已续上，已跳过":"send-skipped".equals(state)?"条件不满足，已跳过":"send-result-uncertain".equals(state)?"结果不确定，已保留尝试记录，不会重复发送":"send-restoring".equals(state)||"send-checking".equals(state)||"send-dispatching".equals(state)?"处理中…":"未完成，请检查连接");text+="\n发送尝试 "+engine.optInt("dispatchAttempts")+"，服务器确认 "+engine.optInt("sentMessages")+"，官方点亮确认 "+engine.optInt("verifiedDone");}
                else if(state.startsWith("state-")){text="真实状态检查："+("state-read-complete".equals(state)?"已刷新（只读）":"state-reading".equals(state)||"state-restoring".equals(state)?"正在读取…":"未完成，请保留原版登录");if(engine.has("privateCount"))text+="\n私聊 "+engine.optInt("privateCount")+"，火花候选 "+engine.optInt("candidateCount")+"\n今天已续 "+engine.optInt("done")+"，待续 "+engine.optInt("pending")+"，未知 "+engine.optInt("unknown");}
                else if(state.startsWith("connect-"))text="账号连接："+("connect-qr-ready".equals(state)?"手机原生引擎已取得官方二维码（未扫码登录）":"connect-online".equals(state)?"已建立协议连接，仍待核对":"尚未完成；请进入连接页面查看");
            }
            java.io.File alarm=new java.io.File(getFilesDir(),"alarm-report.json");if(alarm.exists()){JSONObject a=new JSONObject(new String(java.nio.file.Files.readAllBytes(alarm.toPath()),java.nio.charset.StandardCharsets.UTF_8));String astate=a.optString("status");text+="\n定时唤醒："+("alarm-fired".equals(astate)?"系统已触发模拟检查":"alarm-scheduled".equals(astate)?"等待15秒模拟检查":"alarm-permission-required".equals(astate)?"需要允许闹钟和提醒":"alarm-cancelled".equals(astate)?"已取消模拟检查":"未通过");}
            boolean enabled=getSharedPreferences("schedule",0).getBoolean("enabled",false);text+="\n\n每日22点任务："+(enabled?"已开启":"未开启");if(enabled){java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("MM-dd HH:mm:ss");format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));text+="\n下次（北京时间）："+format.format(new java.util.Date(getSharedPreferences("schedule",0).getLong("nextAt",0)));}status.setText(text);
        } catch(Exception e) { status.setText("检查记录暂不可读，请重试。"); }
    }
    @Override public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        try {
            java.io.File file=new java.io.File(getFilesDir(),"engine-report.json");
            if(!file.exists() || file.length()>10000) {writer.println("SPARK_ENGINE_REPORT unavailable");return;}
            JSONObject raw=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            JSONObject safe=new JSONObject();
            for(String key:new String[]{"status","reason","frames","request","runtime","platform","elapsedMs","cryptoReady","networkReady","sdkReady","timerWhileUiVisible","finishedWhileUiVisible","totalMs","stage","fixtureOnly","authenticationConfirmed","sentMessages","automaticTaskEnabled","mockDispatches","privateCount","candidateCount","done","pending","recover","inactive","unknown","readErrors","pages","complete","freshReadConfirmed","scope","friendCount","discoveredConversations","discoveredThreads","inboxes","namedCount","streakFieldNames","positiveCounterFields","dispatchAttempts","verifiedDone","skipped","dryRun","wouldSend","discoveryComplete","discoveredCount","discoveryErrors"})
                if(raw.has(key))safe.put(key,raw.get(key));
            writer.println("SPARK_ENGINE_REPORT "+safe.toString());JSONObject schedule=new JSONObject();schedule.put("enabled",getSharedPreferences("schedule",0).getBoolean("enabled",false));schedule.put("nextAt",getSharedPreferences("schedule",0).getLong("nextAt",0));writer.println("SPARK_SCHEDULE_REPORT "+schedule.toString());
            java.io.File alarm=new java.io.File(getFilesDir(),"alarm-report.json");if(alarm.exists()&&alarm.length()<10000)writer.println("SPARK_ALARM_REPORT "+new String(java.nio.file.Files.readAllBytes(alarm.toPath()),java.nio.charset.StandardCharsets.UTF_8));
        } catch(Exception e) {writer.println("SPARK_ENGINE_REPORT unavailable");}
    }
    @Override public void onResume() { super.onResume(); visible=true; recordEngineUi(true); handler.post(refresh); }
    @Override public void onPause() { visible=false; recordEngineUi(false); handler.removeCallbacks(refresh); super.onPause(); }
    private void recordEngineUi(boolean state) {
        try {java.nio.file.Files.write(new java.io.File(getFilesDir(),"engine-ui.json").toPath(),("{\"visible\":"+state+"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}
    }
    private TextView label(LinearLayout p,String text,int size) { TextView v=new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(0xff242424); v.setPadding(0,12,0,12); p.addView(v); return v; }
    private void button(LinearLayout p,String title,Runnable action) { Button v=new Button(this); v.setText(title); v.setAllCaps(false); v.setOnClickListener(w -> action.run()); p.addView(v,new LinearLayout.LayoutParams(-1,-2)); }
}



