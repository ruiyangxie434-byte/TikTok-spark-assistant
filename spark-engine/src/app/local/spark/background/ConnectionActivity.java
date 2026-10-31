package app.local.spark.background;
import android.app.Activity;
import android.os.*;
import android.content.Intent;
import android.net.Uri;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/** Private activity: only authorizes this helper's own protocol connection. */
public final class ConnectionActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService io=java.util.concurrent.Executors.newSingleThreadExecutor();
    private String token;
    private TextView status;private ImageView image;private Button verify;
    private volatile boolean busy;private boolean visible;private String verifyUrl,lastQr;
    private final Runnable poll=new Runnable(){public void run(){if(visible&&!busy){busy=true;io.execute(()->fetch());}if(visible)handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);token=saved==null?UUID.randomUUID().toString():saved.getString("bridgeToken",UUID.randomUUID().toString());getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(36,110,36,60);ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);
        TextView title=new TextView(this);title.setText("连接抖音账号");title.setTextSize(25);page.addView(title);
        TextView note=new TextView(this);note.setText("首次授权使用抖音官方扫码及验证。连接成功后还要核对原版抖音是否保持登录。此步骤不会发送消息。\n\n可用另一台设备显示扫码图，或保存到相册后在抖音扫一扫中选取。验证码只在官方验证页填写。");note.setTextSize(16);page.addView(note);
        status=new TextView(this);status.setTextSize(18);status.setText("正在连接…");page.addView(status);
        image=new ImageView(this);image.setAdjustViewBounds(true);page.addView(image,new LinearLayout.LayoutParams(-1,750));
        Button save=new Button(this);save.setText("保存本次扫码图到相册");page.addView(save);save.setOnClickListener(v->saveQr());
        verify=new Button(this);verify.setText("打开官方安全验证");page.addView(verify);verify.setEnabled(false);verify.setOnClickListener(v->openVerification());
        Button stop=new Button(this);stop.setText("停止连接并返回");page.addView(stop);stop.setOnClickListener(v->{stopService(new Intent(this,EngineService.class));finish();});
        if(saved==null)startForegroundService(new Intent(this,EngineService.class).putExtra("mode","connect").putExtra("token",token));
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putString("bridgeToken",token);super.onSaveInstanceState(out);}
    @Override public void onResume(){super.onResume();visible=true;handler.post(poll);}
    @Override public void onPause(){visible=false;handler.removeCallbacks(poll);super.onPause();}
    @Override public void onDestroy(){if(isFinishing())stopService(new Intent(this,EngineService.class));handler.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    private void fetch(){JSONObject result=null;HttpURLConnection connection=null;
        try{JSONObject report=new JSONObject(new String(Files.readAllBytes(new File(getFilesDir(),"engine-report.json").toPath()),StandardCharsets.UTF_8));int port=report.optInt("bridgePort");
            if(port<1024||port>65535)return;
            connection=(HttpURLConnection)new URL("http://127.0.0.1:"+port+"/status").openConnection();connection.setConnectTimeout(800);connection.setReadTimeout(800);connection.setRequestProperty("X-Spark-Token",token);connection.setInstanceFollowRedirects(false);
            if(connection.getResponseCode()!=200)return;
            ByteArrayOutputStream data=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] block=new byte[4096];int count;while((count=in.read(block))!=-1){data.write(block,0,count);if(data.size()>200000)throw new IOException();}}
            result=new JSONObject(new String(data.toByteArray(),StandardCharsets.UTF_8));
        }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();busy=false;}
        if(result!=null){final JSONObject value=result;handler.post(()->show(value));}
    }
    private void show(JSONObject r){if(isFinishing())return;String s=r.optString("status");String text;
        switch(s){case "qr":text="等待扫码并在原版抖音确认";break;case "scanned":text="已扫码，等待手机确认";break;case "confirmed":text="已确认，正在连接…";break;case "verification":text="需要官方安全验证，请点下方按钮完成";break;case "online":text="协议连接已建立。请核对原版抖音仍正常；续火花尚未开启。";break;case "expired":text="本次二维码已过期，请停止后重新连接。";break;case "failed":text="连接未完成："+r.optString("reason");break;case "timed-out":text="本次连接已超时。";break;default:text="正在连接…";}
        status.setText(text);verifyUrl=r.optString("verifyUrl",null);verify.setEnabled(verifyUrl!=null);
        String qr=r.optString("qr",null);if(qr!=null&&!qr.equals(lastQr)){try{byte[] bytes=Base64.decode(qr,Base64.DEFAULT);image.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length));lastQr=qr;}catch(Exception ignored){}}
        if(!"qr".equals(s)&&!"scanned".equals(s)&&!"confirmed".equals(s)){image.setImageDrawable(null);lastQr=null;}
    }
    private void openVerification(){try{Uri u=Uri.parse(verifyUrl);if(!"http".equals(u.getScheme())||!"127.0.0.1".equals(u.getHost())||u.getPort()<1024)throw new IllegalArgumentException();Intent open=new Intent(Intent.ACTION_VIEW,u);open.setPackage("com.android.chrome");startActivity(open);}catch(Exception e){status.setText("无法打开官方验证页，请检查是否安装 Chrome。");}}
    private void saveQr(){if(lastQr==null){status.setText("当前没有可保存的扫码图。");return;}
        android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"SparkLogin-"+System.currentTimeMillis()+".png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/SparkAssistant");values.put(android.provider.MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=null;try{uri=getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException();try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(Base64.decode(lastQr,Base64.DEFAULT));}values.clear();values.put(android.provider.MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);status.setText("已保存扫码图。请在原版抖音扫一扫中选择这张图片。");}catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);status.setText("保存失败，请重试。");}
    }
}
