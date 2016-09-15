package com.wlanjie.ffmpeg.library;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by wlanjie on 16/8/22.
 */

public class RecorderActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private TextureView textureView;
    private boolean isPreviewOn = false;
    private int imageWidth = 720;
    private int imageHeight = 1280;
    private AudioRecord audioRecord;
    private int bufferSize;
    private boolean runAudioRecordThread = true;
    private Encoder encoder = new Encoder();
    private Client client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recorder);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        textureView = (TextureView) findViewById(R.id.texture_view);
//        mSurfaceView.getHolder().addCallback(this);
//        initRecorder();
//        mCamera = Camera.open();
//        mCamera.setPreviewCallback(this);
//        encoder.prepare();
//        encoder.start();

        textureView.setKeepScreenOn(true);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (client != null) {
                    client.startPreview(surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                if (client != null) {
                    client.updatePreview(width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (client != null) {
                    client.stopPreview();
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        if (!initConfig())
            return;
        client.setAudioFilter(new AudioFilter());
    }

    private boolean initConfig() {
        Config config = Config.obtain();
        config.setPrintDetailMsg(true);
        config.setTargetVideoSize(new Size(720, 480));
        config.setBitRate(1000 * 1024);
        config.setVideoFPS(20);
        config.setDefaultCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
        config.setRenderingMode(Config.RenderingMode.NativeWindow);
        config.setRtmpAddress("rtmp://192.168.1.101/live/test");
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, cameraInfo);
        int frontDirection = cameraInfo.orientation;
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo);
        int backDirection = cameraInfo.orientation;
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            config.setFrontCameraDirectionMode((frontDirection == 90 ? Config.DirectionMode.FLAG_DIRECTION_ROATATION_270 : Config.DirectionMode.FLAG_DIRECTION_ROATATION_90) | Config.DirectionMode.FLAG_DIRECTION_FLIP_HORIZONTAL);
            config.setBackCameraDirectionMode((backDirection == 90 ? Config.DirectionMode.FLAG_DIRECTION_ROATATION_90 : Config.DirectionMode.FLAG_DIRECTION_ROATATION_270));
        } else {
            config.setBackCameraDirectionMode((backDirection == 90 ? Config.DirectionMode.FLAG_DIRECTION_ROATATION_0 : Config.DirectionMode.FLAG_DIRECTION_ROATATION_180));
            config.setFrontCameraDirectionMode((frontDirection == 90 ? Config.DirectionMode.FLAG_DIRECTION_ROATATION_180 : Config.DirectionMode.FLAG_DIRECTION_ROATATION_0) | Config.DirectionMode.FLAG_DIRECTION_FLIP_HORIZONTAL);
        }

        client = new Client();
        return client.prepare(config);
    }

    private void initRecorder() {
        String url = "rtmp://192.168.1.104/live/test";

        bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        mCamera = Camera.open();
        mCamera.setPreviewCallback(this);
        FFmpeg.getInstance().initRecorder(url);
        AudioRecordRunnable recordRunnable = new AudioRecordRunnable();
        recordRunnable.run();
    }

    class AudioRecordRunnable implements Runnable {

        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            audioRecord.startRecording();
            while (runAudioRecordThread) {
                ShortBuffer buffer = ShortBuffer.allocate(1024);
                int bufferReadResult = audioRecord.read(buffer.array(), 0, buffer.capacity());
                buffer.limit(bufferReadResult);
                if (bufferReadResult > 0) {
                    FFmpeg.getInstance().recordSamples(buffer.array());
                }
            }
        }
    }

    public void startPreview() {
        if (!isPreviewOn && mCamera != null) {
            isPreviewOn = true;
            mCamera.startPreview();
        }
    }

    public void stopPreview() {
        if (isPreviewOn && mCamera != null) {
            isPreviewOn = false;
            mCamera.stopPreview();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        client.startStreaming();
    }

    @Override
    protected void onPause() {
        super.onPause();
        client.stopStreaming();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.destroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            stopPreview();
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        stopPreview();

        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        Collections.sort(sizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size lhs, Camera.Size rhs) {
                return lhs.width * lhs.height - rhs.width * rhs.height;
            }
        });
        for (Camera.Size size : sizes) {
            if (size.width >= imageWidth && size.height >= imageHeight) {
                imageWidth = size.width;
                imageHeight = size.height;
                break;
            }
        }
        parameters.setPreviewSize(imageWidth, imageHeight);
        parameters.setPreviewFrameRate(25);
//        mCamera.setParameters(parameters);
//        try {
//            mCamera.setPreviewDisplay(holder);
//            mCamera.setPreviewCallback(this);
//            startPreview();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        holder.addCallback(null);
        mCamera.setPreviewCallback(null);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {

    }
}
