package app.local.spark.background;
import android.app.Presentation;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;

/** Renders only this app's WebView on an app-owned private display. */
final class OffscreenPage implements AutoCloseable {
    private ImageReader frames;
    private VirtualDisplay display;
    private Presentation window;
    WebView web;
    OffscreenPage(Context context, Handler handler) {
        try {
            frames = ImageReader.newInstance(1024,768,PixelFormat.RGBA_8888,2);
            frames.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireLatestImage()) { }
                catch (IllegalStateException ignored) { }
            },handler);
            display = context.getSystemService(DisplayManager.class).createVirtualDisplay(
                "SparkBackground",1024,768,160,frames.getSurface(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
            if (display == null) throw new IllegalStateException("Private display unavailable");
            window = new Presentation(context,display.getDisplay());
            window.getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            web = WebSession.create(window.getContext());
            window.setContentView(web,new ViewGroup.LayoutParams(-1,-1));
            window.show();
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT,false);
            web.onResume(); web.resumeTimers();
        } catch (RuntimeException e) { close(); throw e; }
    }
    @Override public void close() {
        if (window != null) {
            window.setContentView(new FrameLayout(window.getContext()));
            window.dismiss(); window = null;
        }
        if (web != null) { web.stopLoading();web.destroy();web=null; }
        if (display != null) { display.release();display=null; }
        if (frames != null) { frames.close();frames=null; }
    }
}

