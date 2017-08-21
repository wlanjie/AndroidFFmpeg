package com.wlanjie.streaming.sample;

import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.seu.magicfilter.base.gpuimage.GPUImageFilter;
import com.seu.magicfilter.utils.MagicFilterFactory;
import com.seu.magicfilter.utils.MagicFilterType;
import com.wlanjie.streaming.MediaStreamingManager;
import com.wlanjie.streaming.camera.CameraCallback;
import com.wlanjie.streaming.setting.AudioSetting;
import com.wlanjie.streaming.setting.CameraSetting;
import com.wlanjie.streaming.setting.EncoderType;
import com.wlanjie.streaming.setting.StreamingSetting;
import com.wlanjie.streaming.video.SurfaceTextureCallback;

/**
 * Created by wlanjie on 2017/7/16.
 */
public class StreamingActivity extends AppCompatActivity implements SurfaceTextureCallback, FilterAdapter.onFilterChangeListener {

  private MediaStreamingManager mMediaStreamingManager;
  private GPUImageFilter mFilter;
  private GPUImageFilter mChangeFilter;
  private int mSurfaceWidth;
  private int mSurfaceHeight;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_streaming);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayShowTitleEnabled(false);
    }

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
    mMediaStreamingManager.setSurfaceTextureCallback(this);
    mMediaStreamingManager.setCameraCallback(new CameraCallback() {
      @Override
      public void onCameraOpened(int previewWidth, int previewHeight) {
        if (!mMediaStreamingManager.isStartPublish()) {
          mMediaStreamingManager.startStreaming();
        }
      }

      @Override
      public void onCameraClosed() {

      }

      @Override
      public void onPreviewFrame(byte[] data) {

      }
    });

    RecyclerView recyclerView = (RecyclerView) findViewById(R.id.filter_recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
    FilterAdapter adapter = new FilterAdapter(this, Util.TYPES);
    adapter.setOnFilterChangeListener(this);
    recyclerView.setAdapter(adapter);
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
        mMediaStreamingManager.switchCamera();
        break;
    }
    return super.onOptionsItemSelected(item);
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
  public void onSurfaceCreated() {
    mFilter = MagicFilterFactory.initFilters(MagicFilterType.NONE);
  }

  @Override
  public void onSurfaceChanged(int width, int height) {
    mSurfaceWidth = width;
    mSurfaceHeight = height;
    if (mFilter != null) {
      mFilter.onDisplaySizeChanged(width, height);
    }
  }

  @Override
  public void onSurfaceDestroyed() {

  }

  @Override
  public int onDrawFrame(int textureId, int textureWidth, int textureHeight, float[] transformMatrix) {
    if (mFilter != mChangeFilter) {
      if (mFilter != null) {
        mFilter.destroy();
        mFilter = null;
      }
      if (mChangeFilter != null) {
        mChangeFilter.init(this);
        mChangeFilter.onInputSizeChanged(textureWidth, textureHeight);
        mChangeFilter.setTextureTransformMatrix(transformMatrix);
      }
      mFilter = mChangeFilter;
    }

    if (mFilter != null) {
      mFilter.onDisplaySizeChanged(mSurfaceWidth, mSurfaceHeight);
      return mFilter.onDrawFrame(textureId);
    }
    return textureId;
  }

  @Override
  public void onFilterChanged(MagicFilterType filterType) {
    mChangeFilter = MagicFilterFactory.initFilters(filterType);
  }
}
