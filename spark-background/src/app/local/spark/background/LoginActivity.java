package app.local.spark.background;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
public final class LoginActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page=new LinearLayout(this); page.setOrientation(1); setContentView(page); getWindow().getInsetsController().setSystemBarsAppearance(android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        page.setOnApplyWindowInsetsListener((v,i) -> { android.graphics.Insets b=i.getInsets(android.view.WindowInsets.Type.systemBars()); v.setPadding(b.left,b.top,b.right,b.bottom); return i; });
        TextView notice=new TextView(this); notice.setPadding(20,16,20,16);
        notice.setText("抖音官方网页登录 · 首次或失效时需要\n请在抖音主页完成登录与安全验证，进入消息页后返回助手检查。"); page.addView(notice);
        Button back=new Button(this); back.setText("完成登录，返回助手"); back.setOnClickListener(v -> { CookieManager.getInstance().flush(); finish(); }); page.addView(back);
        web=WebSession.create(this);
        web.setWebViewClient(new WebViewClient() { @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) { return !WebSession.https(r.getUrl().toString()); } });
        web.setWebChromeClient(new WebChromeClient());
        page.addView(web,new LinearLayout.LayoutParams(-1,0,1)); web.loadUrl(WebSession.HOME);
    }
    @Override public void onPause() { CookieManager.getInstance().flush(); super.onPause(); }
    @Override public void onDestroy() { if(web!=null) {web.stopLoading();web.destroy();} super.onDestroy(); }
}



