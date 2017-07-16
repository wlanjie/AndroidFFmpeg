package com.wlanjie.streaming.sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

import com.wlanjie.streaming.setting.EncoderType;

public class MainActivity extends AppCompatActivity {

  private int encodeType = EncoderType.SOFT.ordinal();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    final EditText rtmpUrlEdit = (EditText) findViewById(R.id.rtmp_url);
    rtmpUrlEdit.setText("rtmp://www.ossrs.net:1935/live/wlanjie");

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
