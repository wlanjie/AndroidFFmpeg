package com.wlanjie.ffmpeg.library;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.TextureView;

/**
 * Created by wlanjie on 16/8/22.
 */

public class RecorderActivity extends Activity {

    private Client client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recorder);
        TextureView textureView = (TextureView) findViewById(R.id.texture_view);

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
}
