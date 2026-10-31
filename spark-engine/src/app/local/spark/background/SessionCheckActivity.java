package app.local.spark.background;

import android.app.Activity;
import android.graphics.Insets;
import android.os.*;
import android.util.Log;
import android.view.WindowInsets;
import android.webkit.*;
import android.webkit.CookieManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Uses only this app's existing cookies. Never loads an official page or follows redirects. */
public final class SessionCheckActivity extends Activity {
    private static final String ENDPOINT = "https://www.douyin.com/aweme/v1/web/user/profile/self/";
    private static final int MAX_BYTES = 262144;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView signer;
    private TextView status;
    private volatile HttpsURLConnection connection;
    private volatile boolean closed;
    private boolean started, completed;
    private volatile String phase="local-engine";
    private volatile int httpStatus, responseBytes;
    private String responseKind="unconfirmed", profileShape="missing", platformStatus="absent";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this); page.setOrientation(1); page.setPadding(24,24,24,24); setContentView(page);
        page.setOnApplyWindowInsetsListener((v,i) -> { Insets b=i.getInsets(WindowInsets.Type.systemBars()); v.setPadding(b.left+24,b.top+24,b.right+24,b.bottom+24); return i; });
        TextView title = new TextView(this); title.setText("验证助手已保存的会话"); title.setTextSize(24); page.addView(title);
        status = new TextView(this); status.setTextSize(17); status.setPadding(0,24,0,24); status.setText("正在检查…\n不重新登录，不发送消息。"); page.addView(status);
        Button back = new Button(this); back.setText("返回助手"); back.setOnClickListener(v -> finish()); page.addView(back);
        signer = new WebView(this);
        WebSettings settings = signer.getSettings(); settings.setJavaScriptEnabled(true); settings.setBlockNetworkLoads(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setDomStorageEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        signer.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view,String url) { if(!started && !closed) { started=true; begin(); } }
        });
        page.addView(signer,new LinearLayout.LayoutParams(1,1));
        save("session-checking",false);
        handler.postDelayed(() -> complete("session-unknown",false),15000);
        signer.loadDataWithBaseURL("https://local.invalid/","<!doctype html>","text/html","UTF-8",null);
    }
    private void begin() {
        try {
            // WebView's CookieManager is deliberately not installed as a native CookieHandler.
            phase="local-cookies";
            if(CookieHandler.getDefault()!=null) { complete("session-unknown",false); return; }
            final String cookies = CookieManager.getInstance().getCookie(ENDPOINT);
            Map<String,String> values = new HashMap<>();
            if(cookies!=null && cookies.length()<=65536 && cookies.indexOf('\r')<0 && cookies.indexOf('\n')<0) {
                for(String item : cookies.split(";")) {
                    int at=item.indexOf('='); if(at>0) values.put(item.substring(0,at).trim(),item.substring(at+1));
                }
            }
            boolean login=false;
            for(String name : new String[]{"sessionid","sessionid_ss","sid_tt","sid_guard"}) if(!values.getOrDefault(name,"").isEmpty()) login=true;
            if(!login) { complete("no-local-session",false); return; }
            final String ua = WebSession.userAgent(this);
            JSONObject options = new JSONObject(), signingValues = new JSONObject();
            String[][] fields={{"msToken","msToken"},{"verifyFp","s_v_web_id"},{"fp","s_v_web_id"},{"uifid","UIFID"}};
            for(String[] field:fields) if(values.containsKey(field[1])) signingValues.put(field[0],values.get(field[1]));
            options.put("userAgent",ua); options.put("values",signingValues);
            String script;
            try(InputStream in=getAssets().open("session-check.js"); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] block=new byte[4096]; int n; while((n=in.read(block))!=-1) out.write(block,0,n);
                script=new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
            phase="local-signature";
            signer.evaluateJavascript(script+";SparkSession.sign("+options.toString()+");", raw -> {
                if(closed || completed) return;
                try {
                    Object decoded=new JSONTokener(raw).nextValue();
                    if(!(decoded instanceof String)) throw new IOException();
                    final String signed=(String)decoded;
                    new Thread(() -> readProfile(signed,cookies,ua),"existing-session-read").start();
                } catch(Exception e) {complete("session-unknown",false);}
            });
        } catch(Exception e) {complete("session-unknown",false);}
    }
    private void readProfile(String signed,String cookies,String ua) {
        HttpsURLConnection active=null;
        try {
            URL url=new URL(signed);
            if(!"https".equals(url.getProtocol()) || !"www.douyin.com".equals(url.getHost()) || url.getPort()!=-1 || url.getUserInfo()!=null || url.getRef()!=null || !new URL(ENDPOINT).getPath().equals(url.getPath())) throw new IOException();
            if(closed) return;
            active=(HttpsURLConnection)url.openConnection(); connection=active;
            active.setInstanceFollowRedirects(false); active.setConnectTimeout(8000); active.setReadTimeout(8000);
            active.setRequestMethod("GET"); active.setUseCaches(false);
            active.setRequestProperty("Cookie",cookies); active.setRequestProperty("User-Agent",ua);
            active.setRequestProperty("Accept","application/json"); active.setRequestProperty("Accept-Encoding","identity");
            active.setRequestProperty("Referer",WebSession.CHAT);
            phase="profile-request";
            int http=active.getResponseCode(); httpStatus=http; String body="";
            if(http==200) {
                if(active.getContentLengthLong()>MAX_BYTES) throw new IOException();
                try(InputStream in=active.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] block=new byte[4096]; int n;
                    while((n=in.read(block))!=-1) { if(closed || out.size()+n>MAX_BYTES) throw new IOException(); out.write(block,0,n); }
                    responseBytes=out.size(); body=new String(out.toByteArray(),StandardCharsets.UTF_8);
                }
            }
            // Never copy Set-Cookie, response bodies, identity fields or signed URLs to storage/logs.
            phase="profile-response";
            final JSONObject input=new JSONObject(); input.put("httpStatus",http); input.put("body",body);
            handler.post(() -> {
                if(closed || completed) return;
                signer.evaluateJavascript("SparkSession.classify("+input.toString()+");", raw -> {
                    if(closed || completed) return;
                    try {
                        JSONObject result=new JSONObject(raw);
                        responseKind=result.optString("responseKind","unconfirmed");
                        profileShape=result.optString("profileShape","missing");
                        String code=result.optString("platformStatus","absent");
                        if(code.matches("-?[0-9]{1,10}")) platformStatus=code;
                        boolean readable="session-profile-readable".equals(result.optString("status")) && result.optBoolean("authenticationConfirmed");
                        complete(readable?"session-profile-readable":"session-unknown",readable);
                    } catch(Exception e) {complete("session-unknown",false);}
                });
            });
        } catch(Exception e) {phase="request-error-"+e.getClass().getSimpleName();handler.post(() -> complete("session-unknown",false));}
        finally {if(active!=null) active.disconnect(); if(connection==active) connection=null;}
    }
    private void complete(String value,boolean authenticated) {
        if(closed || completed) return; completed=true; handler.removeCallbacksAndMessages(null);
        HttpsURLConnection active=connection; if(active!=null) active.disconnect();
        save(value,authenticated);
        if("session-profile-readable".equals(value)) status.setText("助手旧会话可以读取账号资料。\n\n还需确认原版抖音保持在线，以及两端是否为同一账号。\n未重新登录，未发送消息。");
        else if("no-local-session".equals(value)) status.setText("助手本机没有可用于检查的会话凭据。\n\n未发起登录，未发送消息。");
        else status.setText("无法确认旧会话是否有效。\n\n不能据此认定会话已过期。没有重新登录，也没有发送消息。");
    }
    private void save(String value,boolean authenticated) {
        try {
            JSONObject r=new JSONObject(); r.put("status",value); r.put("mode","native-session-check"); r.put("readAt",System.currentTimeMillis());
            r.put("diagnosticStage",phase); r.put("httpStatus",httpStatus); r.put("responseBytes",responseBytes);
            r.put("responseKind",responseKind); r.put("profileShape",profileShape); r.put("platformStatus",platformStatus);
            r.put("authenticationConfirmed",authenticated); r.put("accountMatchConfirmed",false); r.put("sentMessages",0); r.put("automaticTaskEnabled",false);
            getSharedPreferences("probe",0).edit().putString("report",r.toString()).apply(); Log.i("SparkBackground",r.toString());
        } catch(Exception ignored) { }
    }
    @Override public void onPause() {
        if(!completed) { closed=true; save("cancelled",false); HttpsURLConnection active=connection; if(active!=null) active.disconnect(); }
        super.onPause();
    }
    @Override public void onDestroy() {
        closed=true; handler.removeCallbacksAndMessages(null); HttpsURLConnection active=connection; if(active!=null) active.disconnect();
        if(signer!=null) {signer.stopLoading();signer.destroy();signer=null;} super.onDestroy();
    }
}

