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
//                                    int result = FFmpeg.compress("/sdcard/Download/sintel.mp4", "/sdcard/Download/compress.mp4", 0, 0);
//                                    int width = FFmpeg.getVideoWidth("/sdcard/Download/sintel.mp4");
//                                    int height = FFmpeg.getVideoHeight("/sdcard/Download/sintel.mp4");
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
                                    int result = FFmpeg.compress("/sdcard/DCIM/Camera/a.mp4", "/sdcard/DCIM/Camera/compress.mp4", newWidth, newHeight);
                                    long end = System.currentTimeMillis();
                                    if (result >= 0) {
                                        Looper.prepare();
                                        System.out.println("width = " + width + " height = " + height + " " + ((end - start) / 1000));
                                        Toast.makeText(MainActivity.this, "压缩完成" + (end - start) / 1000, Toast.LENGTH_SHORT).show();
                                        Looper.loop();
                                    }
                                    FFmpeg.release();
                                } catch (FileNotFoundException | IllegalStateException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                });
    }
}
