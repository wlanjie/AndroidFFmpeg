package com.wlanjie.streaming.sample;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.wlanjie.streaming.MediaStreamingManager;
import com.wlanjie.streaming.setting.AudioSetting;
import com.wlanjie.streaming.setting.CameraSetting;
import com.wlanjie.streaming.setting.EncoderType;
import com.wlanjie.streaming.setting.StreamingSetting;

public class MainActivity extends AppCompatActivity {

  private MediaStreamingManager mMediaStreamingManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayShowTitleEnabled(false);
    }

    CameraSetting cameraSetting = new CameraSetting();
    AudioSetting audioSetting = new AudioSetting();
    StreamingSetting streamingSetting = new StreamingSetting();
    streamingSetting.setRtmpUrl("rtmp://www.ossrs.net:1935/live/wlanjie")
        .setEncoderType(EncoderType.HARD);

    GLSurfaceView glSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
    mMediaStreamingManager = new MediaStreamingManager(glSurfaceView);
    mMediaStreamingManager.prepare(cameraSetting, streamingSetting, audioSetting);

    findViewById(R.id.publish)
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            mMediaStreamingManager.startStreaming();
          }
        });

    findViewById(R.id.stop)
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            mMediaStreamingManager.stopStreaming();
          }
        });
  }

  @Override
  protected void onResume() {
    super.onResume();
    mMediaStreamingManager.resume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mMediaStreamingManager.pause();
    mMediaStreamingManager.stopStreaming();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.switch_camera:
        break;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }
}
