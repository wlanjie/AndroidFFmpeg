package com.wlanjie.ffmpeg.library;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import java.io.FileNotFoundException;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.compress)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new Thread(){
                            @Override
                            public void run() {
                                super.run();
                                try {
                                    long start = System.currentTimeMillis();
                                    int width = FFmpeg.getVideoWidth("/sdcard/DCIM/Camera/a.mp4");
                                    int height = FFmpeg.getVideoHeight("/sdcard/DCIM/Camera/a.mp4");
                                    double rotation = FFmpeg.getRotation("/sdcard/DCIM/Camera/a.mp4");
                                    int newWidth;
                                    int newHeight;
                                    if (rotation == 90) {
                                        newWidth = height / 2;
                                        newHeight = width / 2;
                                    } else {
                                        newWidth = width / 2;
                                        newHeight = height / 2;
                                    }
                                    int result = FFmpeg.compress("/sdcard/DCIM/Camera/a.mp4", "/sdcard/DCIM/Camera/compress.mp4", -1, -1);
                                    FFmpeg.release();
                                    long end = System.currentTimeMillis();
                                    if (result >= 0) {
                                        Looper.prepare();
                                        System.out.println( ((end - start) / 1000));
                                        Toast.makeText(MainActivity.this, "压缩完成" + (end - start) / 1000, Toast.LENGTH_SHORT).show();
                                        Looper.loop();
                                    }


                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                });
    }
}
