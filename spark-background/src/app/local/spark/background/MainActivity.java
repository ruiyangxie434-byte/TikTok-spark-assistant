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
        ScrollView scroll = new ScrollView(this); LinearLayout page = new LinearLayout(this);
        page.setOrientation(1); page.setPadding(30, 28, 30, 40); scroll.addView(page); setContentView(scroll); getWindow().getInsetsController().setSystemBarsAppearance(android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        scroll.setOnApplyWindowInsetsListener((v, i) -> { android.graphics.Insets b = i.getInsets(android.view.WindowInsets.Type.systemBars()); v.setPadding(b.left,b.top,b.right,b.bottom); return i; });
        label(page, "火花助手·后台版", 25);
        label(page, "连接检查版 · 自动发送未开启", 17);
        label(page, "目标：每天北京时间 22:00，跳过今天已续上的好友，只给确认待续的好友发一次普通表情。\n\n现在先验证手机后台能否读取状态。本版本不发送消息，也没有启动定时任务。", 15);
        label(page, "原版抖音与助手的会话共存尚未验证，网页登录和页面检查暂缓。请保留原版登录。", 15);
        button(page, "验证已保存会话（不登录、不发送）", () -> startActivity(new Intent(this, SessionCheckActivity.class)));
        label(page, "只尝试读取助手旧会话的账号资料，不加载官方登录页。检查结果未知时也不会重新登录。", 14);
        button(page, "停止本次检查", () -> { stopService(new Intent(this, ProbeService.class)); getSharedPreferences("probe",0).edit().putString("report","{\"status\":\"cancelled\"}").apply(); render(); });
        status = label(page, "", 16); status.setTextIsSelectable(true); render();
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
            status.setText(text + "\n\n自动发送：未开启\n每日定时：未开启");
        } catch(Exception e) { status.setText("检查记录暂不可读，请重试。"); }
    }
    @Override public void onResume() { super.onResume(); visible=true; handler.post(refresh); }
    @Override public void onPause() { visible=false; handler.removeCallbacks(refresh); super.onPause(); }
    private TextView label(LinearLayout p,String text,int size) { TextView v=new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(0xff242424); v.setPadding(0,12,0,12); p.addView(v); return v; }
    private void button(LinearLayout p,String title,Runnable action) { Button v=new Button(this); v.setText(title); v.setAllCaps(false); v.setOnClickListener(w -> action.run()); p.addView(v,new LinearLayout.LayoutParams(-1,-2)); }
}



