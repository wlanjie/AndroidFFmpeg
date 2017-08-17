package com.wlanjie.streaming.sample;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.wlanjie.streaming.setting.EncoderType;
import com.wlanjie.streaming.util.StreamingLog;

import io.reactivex.functions.Consumer;

public class MainActivity extends AppCompatActivity {

  private int encodeType = EncoderType.SOFT.ordinal();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    final EditText rtmpUrlEdit = (EditText) findViewById(R.id.rtmp_url);
    rtmpUrlEdit.setText("rtmp://192.168.1.103/live/wlanjie");

    RxPermissions rxPermissions = new RxPermissions(this);
    rxPermissions.request(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        .subscribe(new Consumer<Boolean>() {
          @Override
          public void accept(Boolean grantResults) throws Exception {

          }
        });
    final RadioGroup encodeGroup = (RadioGroup) findViewById(R.id.encode_group);
    encodeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
          case R.id.hard_encode:
            encodeType = EncoderType.HARD.ordinal();
            break;
          case R.id.soft_encode:
            encodeType = EncoderType.SOFT.ordinal();
            break;
        }
      }
    });

    findViewById(R.id.start)
        .setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Intent intent = new Intent(v.getContext(), StreamingActivity.class);
            intent.putExtra(Constant.RTMP, rtmpUrlEdit.getText().toString());
            intent.putExtra(Constant.ENCODE_TYPE, encodeType);
            startActivity(intent);
          }
        });
  }
}
