package com.wlanjie.streaming;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.wlanjie.streaming.camera.CameraView;

public class MainActivity extends Activity {

    private CameraView mCameraView;
    private Encoder encoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCameraView = (CameraView) findViewById(R.id.surface_view);
        mCameraView.postDelayed(new Runnable() {
            @Override
            public void run() {
                encoder = new Encoder.Builder()
                        .setSoftEncoder(true)
                        .setCameraView(mCameraView)
                        .build();
                String url = "rtmp://192.168.1.103/live/livestream";
//                String url = "rtmp://192.168.0.143/live/livestream";
                encoder.start(url);
            }
        }, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (encoder != null) {
            encoder.stop();
        }
    }
}
