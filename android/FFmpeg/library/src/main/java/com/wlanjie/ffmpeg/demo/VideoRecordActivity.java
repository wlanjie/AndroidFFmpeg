package com.wlanjie.ffmpeg.demo;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.wlanjie.ffmpeg.FFmpeg;
import com.wlanjie.ffmpeg.VideoRecorder;
import com.wlanjie.ffmpeg.library.R;
import com.wlanjie.ffmpeg.setting.AudioSetting;
import com.wlanjie.ffmpeg.setting.CameraSetting;
import com.wlanjie.ffmpeg.setting.VideoSetting;

/**
 * Created by wlanjie on 2017/9/13.
 */
public class VideoRecordActivity extends AppCompatActivity {

  private VideoRecorder mVideoRecorder;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_recorder);
    GLSurfaceView surfaceView = (GLSurfaceView) findViewById(R.id.surface_view);
    mVideoRecorder = new VideoRecorder(this);
    mVideoRecorder.prepare(surfaceView, new CameraSetting(), new AudioSetting(), new VideoSetting());
    findViewById(R.id.record)
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (mVideoRecorder.isRecording()) {
              mVideoRecorder.stopRecorder();
              FFmpeg.getInstance().release();
              return;
            }
            mVideoRecorder.startRecorder("/sdcard/" + System.currentTimeMillis() + ".mp4");
          }
        });
  }

  @Override
  protected void onResume() {
    super.onResume();
    mVideoRecorder.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mVideoRecorder.onPause();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mVideoRecorder.onDestroy();
  }
}
