package com.walnjie.ffmpeg;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


public class MainActivity extends AppCompatActivity {

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
                                    playPcm();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                });
    }

    void playPcm() throws IOException {
//        int minBuffers = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
//        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT, minBuffers * 2, AudioTrack.MODE_STREAM);
//        audioTrack.setStereoVolume(1.0f, 1.0f);
//        audioTrack.play();

        byte buffer[] = new byte[8129];
        File file = new File(Environment.getExternalStorageDirectory(), "audio_record.pcm");
        FileInputStream fileInputStream = new FileInputStream(file);

        AudioEncoder audioEncoder = new AudioEncoder();
        while (fileInputStream.read(buffer) >= 0) {
//            audioTrack.write(buffer, 0, buffer.length);
            audioEncoder.offerEncoder(buffer);
        }
    }
}
