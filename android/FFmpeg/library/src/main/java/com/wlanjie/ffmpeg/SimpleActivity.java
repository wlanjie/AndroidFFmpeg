package com.wlanjie.ffmpeg;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Created by wlanjie on 2017/8/22.
 */
public class SimpleActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
//    FFmpeg.getInstance().openInput("/sdcard/tencent/MicroMsg/WeiXin/wx_camera_1502880546390.mp4");
    FFmpeg.getInstance().openInput("/sdcard/DCIM/Camera/20170726_173615_5692.mp4");
    Toast.makeText(this, "缩放完成", Toast.LENGTH_SHORT).show();
  }
}
