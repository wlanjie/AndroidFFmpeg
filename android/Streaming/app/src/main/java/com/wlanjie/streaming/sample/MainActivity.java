package com.wlanjie.streaming.sample;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.wlanjie.streaming.Encoder;
import com.wlanjie.streaming.camera.CameraView;

public class MainActivity extends AppCompatActivity {

    private CameraView mCameraView;
    private Encoder encoder;

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

        mCameraView = (CameraView) findViewById(R.id.surface_view);
        mCameraView.postDelayed(new Runnable() {
            @Override
            public void run() {
                encoder = new Encoder.Builder()
                        .setSoftEncoder(true)
                        .setCameraView(mCameraView)
                        .build();
//                String url = "rtmp://192.168.1.100/live/livestream";
                String url = "rtmp://192.168.0.143/live/livestream";
                encoder.start(url);
            }
        }, 1000);
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
                if (mCameraView != null) {
                    int facing = mCameraView.getFacing();
                    mCameraView.setFacing(facing == CameraView.FACING_FRONT ?
                            CameraView.FACING_BACK : CameraView.FACING_FRONT);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (encoder != null) {
            encoder.stop();
        }
    }
}
