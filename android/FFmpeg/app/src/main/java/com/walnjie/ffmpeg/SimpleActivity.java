package com.walnjie.ffmpeg;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.wlanjie.ffmpeg.FFmpeg;
import com.wlanjie.ffmpeg.Video;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by wlanjie on 2017/8/22.
 */
public class SimpleActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_simple);
    findViewById(R.id.short_video)
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            RxPermissions rxPermissions = new RxPermissions(SimpleActivity.this);
            rxPermissions.request(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe(new io.reactivex.functions.Consumer<Boolean>() {
                  @Override
                  public void accept(Boolean grantResults) throws Exception {
                    Intent intent = new Intent(SimpleActivity.this, VideoRecordActivity.class);
                    startActivity(intent);
                  }
                });
          }
        });
    findViewById(R.id.get_video_info)
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Observable.create(new ObservableOnSubscribe<Video>() {
              @Override
              public void subscribe(ObservableEmitter<Video> e) throws Exception {
                int result = FFmpeg.getInstance().openInput("/sdcard/DCIM/Camera/20170726_173615_5692.mp4");
                if (result != 0) {
                  e.onError(new RuntimeException());
                  return;
                }
                e.onNext(FFmpeg.getInstance().getVideoInfo());
              }
            }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Video>() {
                  @Override
                  public void accept(Video video) throws Exception {
                    FFmpeg.getInstance().release();
                  }
                }, new Consumer<Throwable>() {
                  @Override
                  public void accept(Throwable throwable) throws Exception {
                    throwable.printStackTrace();
                    FFmpeg.getInstance().release();
                  }
                });
          }
        });
    findViewById(R.id.get_video_frame)
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            startActivity(new Intent(SimpleActivity.this, VideoFrameActivity.class));
          }
        });
    findViewById(R.id.scale)
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Observable.create(new ObservableOnSubscribe<Integer>() {
              @Override
              public void subscribe(ObservableEmitter<Integer> e) throws Exception {
                int result = FFmpeg.getInstance().openInput("/sdcard/DCIM/Camera/20170726_173615_5692.mp4");
                if (result != 0) {
                  e.onError(new RuntimeException());
                  return;
                }
                result = FFmpeg.getInstance().openOutput("/sdcard/output.mp4");
                if (result != 0) {
                  e.onError(new RuntimeException());
                  return;
                }
                result = FFmpeg.getInstance().scale(280, 480);
                e.onNext(result);
              }
            }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Integer>() {
                  @Override
                  public void accept(Integer integer) throws Exception {
                    if (integer == 0) {
                      Toast.makeText(SimpleActivity.this, "缩放完成", Toast.LENGTH_SHORT).show();
                      FFmpeg.getInstance().release();
                    }
                  }
                }, new Consumer<Throwable>() {
                  @Override
                  public void accept(Throwable throwable) throws Exception {
                    throwable.printStackTrace();
                    FFmpeg.getInstance().release();
                  }
                });
          }
        });
  }
}
