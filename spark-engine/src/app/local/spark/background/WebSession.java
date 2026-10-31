package app.local.spark.background;
import android.content.Context;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
final class WebSession {
    static final String HOME = "https://www.douyin.com/jingxuan"; static final String CHAT = "https://www.douyin.com/chat";
    static WebView create(Context context) {
        WebView view = new WebView(context);
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(userAgent(context));
        s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);
        return view;
    }
    static String userAgent(Context context) {
        String original = WebSettings.getDefaultUserAgent(context);
        Matcher chrome = Pattern.compile("Chrome/[0-9.]+").matcher(original);
        return chrome.find() ? "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " + chrome.group() + " Safari/537.36" : original;
    }
    static boolean https(String url) {
        try { return "https".equals(Uri.parse(url).getScheme()); } catch (Exception e) { return false; }
    }
    static String script(Context context) throws Exception {
        try (InputStream in = context.getAssets().open("probe.js"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) >= 0) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}



