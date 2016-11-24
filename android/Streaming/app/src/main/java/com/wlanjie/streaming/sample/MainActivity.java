package com.wlanjie.streaming.sample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.wlanjie.streaming.CameraView;
import com.wlanjie.streaming.Encoder;

public class MainActivity extends AppCompatActivity {

    private Encoder encoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
//        Encoder.Parameters parameters = new Encoder.Parameters();
//        parameters.previewWidth = width;
//        parameters.previewHeight = height;

//        encoder = new Encoder(parameters, (CameraView) findViewById(R.id.surface_view));
        final CameraView cameraView = (CameraView) findViewById(R.id.surface_view);
        cameraView.postDelayed(new Runnable() {
            @Override
            public void run() {
                encoder = new Encoder(cameraView);
                String url = "rtmp://192.168.0.68/live/livestream";
                int result = encoder.connect(url);
                if (result < 0) {
                    Toast.makeText(MainActivity.this, "连接服务器失败", Toast.LENGTH_LONG).show();
                    return;
                }
                encoder.start();
            }
        }, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (encoder != null) {
            encoder.stop();
            encoder.destroy();
        }
    }
}
