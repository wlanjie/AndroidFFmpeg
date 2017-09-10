package com.walnjie.ffmpeg;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.wlanjie.ffmpeg.FFmpeg;
import com.wlanjie.ffmpeg.Video;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by wlanjie on 2017/8/22.
 */
public class SimpleActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_simple);
    findViewById(R.id.get_video_info)
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Observable.create(new Observable.OnSubscribe<Video>() {
              @Override
              public void call(Subscriber<? super Video> subscriber) {
                int result = FFmpeg.getInstance().openInput("/sdcard/DCIM/Camera/20170726_173615_5692.mp4");
                if (result != 0) {
                  subscriber.onError(new RuntimeException());
                  return;
                }
                subscriber.onNext(FFmpeg.getInstance().getVideoInfo());
              }
            }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Video>() {
                  @Override
                  public void call(Video video) {
                    FFmpeg.getInstance().release();
                  }
                }, new Action1<Throwable>() {
                  @Override
                  public void call(Throwable throwable) {
                    throwable.printStackTrace();
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
            Observable.create(new Observable.OnSubscribe<Integer>() {
              @Override
              public void call(Subscriber<? super Integer> subscriber) {
                int result = FFmpeg.getInstance().openInput("/sdcard/DCIM/Camera/20170726_173615_5692.mp4");
                if (result != 0) {
                  subscriber.onError(new RuntimeException());
                  return;
                }
                result = FFmpeg.getInstance().openOutput("/sdcard/output.mp4");
                if (result != 0) {
                  subscriber.onError(new RuntimeException());
                  return;
                }
                result = FFmpeg.getInstance().scale(280, 480);
                subscriber.onNext(result);
              }
            }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Integer>() {
                  @Override
                  public void call(Integer integer) {
                    if (integer == 0) {
                      Toast.makeText(SimpleActivity.this, "缩放完成", Toast.LENGTH_SHORT).show();
                      FFmpeg.getInstance().release();
                    }
                  }
                }, new Action1<Throwable>() {
                  @Override
                  public void call(Throwable throwable) {
                    throwable.printStackTrace();
                    FFmpeg.getInstance().release();
                  }
                });
          }
        });
  }
}
