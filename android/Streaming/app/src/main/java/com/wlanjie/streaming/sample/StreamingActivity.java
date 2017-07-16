package com.wlanjie.streaming.sample;

import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.wlanjie.streaming.MediaStreamingManager;
import com.wlanjie.streaming.setting.AudioSetting;
import com.wlanjie.streaming.setting.CameraSetting;
import com.wlanjie.streaming.setting.EncoderType;
import com.wlanjie.streaming.setting.StreamingSetting;

/**
 * Created by wlanjie on 2017/7/16.
 */
public class StreamingActivity extends AppCompatActivity {

  private MediaStreamingManager mMediaStreamingManager;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_streaming);

    Intent intent = getIntent();
    EncoderType encoderType = intent.getIntExtra(Constant.ENCODE_TYPE, EncoderType.SOFT.ordinal()) == EncoderType.SOFT.ordinal()
        ? EncoderType.SOFT : EncoderType.HARD;


    CameraSetting cameraSetting = new CameraSetting();
    AudioSetting audioSetting = new AudioSetting();
    StreamingSetting streamingSetting = new StreamingSetting();
    streamingSetting.setRtmpUrl(intent.getStringExtra(Constant.RTMP))
        .setEncoderType(encoderType);

    GLSurfaceView glSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
    mMediaStreamingManager = new MediaStreamingManager(glSurfaceView);
    mMediaStreamingManager.prepare(cameraSetting, streamingSetting, audioSetting);
  }

  @Override
  protected void onResume() {
    super.onResume();
    mMediaStreamingManager.resume();
    mMediaStreamingManager.startStreaming();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mMediaStreamingManager.pause();
    mMediaStreamingManager.stopStreaming();
  }
}
