package com.wlanjie.ffmpeg.library;

import android.app.Activity;
import android.os.Bundle;

/**
 * Created by wlanjie on 2017/8/22.
 */
public class SimpleActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    FFmpeg.getInstance().openInput("/sdcard/DCIM/Camera/20170726_173615_5692.mp4");
  }
}
