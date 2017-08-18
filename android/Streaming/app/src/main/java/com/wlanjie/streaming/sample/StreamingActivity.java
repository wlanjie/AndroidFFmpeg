package com.wlanjie.streaming.sample;

import android.content.Intent;
import android.opengl.GLES20;
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

import com.seu.magicfilter.filter.base.gpuimage.GPUImageFilter;
import com.seu.magicfilter.filter.helper.MagicFilterFactory;
import com.seu.magicfilter.filter.helper.MagicFilterType;
import com.seu.magicfilter.utils.MagicParams;
import com.seu.magicfilter.utils.TextureRotationUtil;
import com.wlanjie.streaming.MediaStreamingManager;
import com.wlanjie.streaming.camera.CameraCallback;
import com.wlanjie.streaming.setting.AudioSetting;
import com.wlanjie.streaming.setting.CameraSetting;
import com.wlanjie.streaming.setting.EncoderType;
import com.wlanjie.streaming.setting.StreamingSetting;
import com.wlanjie.streaming.util.OpenGLUtils;
import com.wlanjie.streaming.video.MagicCameraInputFilter;
import com.wlanjie.streaming.video.SurfaceTextureCallback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Created by wlanjie on 2017/7/16.
 */
public class StreamingActivity extends AppCompatActivity implements SurfaceTextureCallback, FilterAdapter.onFilterChangeListener {

  private MediaStreamingManager mMediaStreamingManager;
  private MagicCameraInputFilter mCameraInputFilter;
  private GPUImageFilter mBeautyFilter;
  private GPUImageFilter mFilter;
  private FloatBuffer mCubeBuffer;
  private FloatBuffer mTextureBuffer;
  private GPUImageFilter mChangeFilter;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_streaming);

    MagicParams.context = this;
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

//    mCubeBuffer = ByteBuffer.allocateDirect(OpenGLUtils.CUBE.length * 4)
//        .order(ByteOrder.nativeOrder())
//        .asFloatBuffer();
//    mCubeBuffer.put(OpenGLUtils.CUBE).position(0);
//
//    mTextureBuffer = ByteBuffer.allocateDirect(OpenGLUtils.TEXTURE.length * 4)
//        .order(ByteOrder.nativeOrder())
//        .asFloatBuffer();
//    mTextureBuffer.put(OpenGLUtils.TEXTURE).position(0);

    mCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

    mTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    mTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
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
    mBeautyFilter = new GPUImageFilter();
    mBeautyFilter.init();
    mFilter = MagicFilterFactory.initFilters(MagicFilterType.NONE);
    mCameraInputFilter = new MagicCameraInputFilter(this);
    mCameraInputFilter.init();
  }

  @Override
  public void onSurfaceChanged(int width, int height) {
    mBeautyFilter.init();
    mBeautyFilter.onDisplaySizeChanged(width, height);
    if (mFilter != null) {
      mFilter.onDisplaySizeChanged(width, height);
    }
//    mCameraInputFilter.onDisplaySizeChanged(width, height);
  }

  @Override
  public void onSurfaceDestroyed() {

  }

  private boolean isInit = false;

  @Override
  public int onDrawFrame(int textureId, int textureWidth, int textureHeight, float[] transformMatrix) {
//    if (mFilter == null) {
//      return -1;
//    }
    if (mFilter != mChangeFilter) {
      if (mFilter != null) {
        mFilter.destroy();
        mFilter = null;
      }
      if (mChangeFilter != null) {
        mChangeFilter.init();
      }
      mFilter = mChangeFilter;
    }

//    if (!isInit) {
//      mCameraInputFilter.initCameraFrameBuffer(textureWidth, textureHeight);
//      isInit = true;
//    }
//
//    mCameraInputFilter.setTextureTransformMatrix(transformMatrix);
//    int id = mCameraInputFilter.onDrawToTexture(textureId);
    if (mFilter != null) {
      mFilter.onInputSizeChanged(textureWidth, textureHeight);
      mFilter.onDisplaySizeChanged(1440, 2320);
      mFilter.onDrawFrame(textureId);
    }
    return 2;
  }

  @Override
  public void onFilterChanged(MagicFilterType filterType) {
    mChangeFilter = MagicFilterFactory.initFilters(filterType);
  }
}
