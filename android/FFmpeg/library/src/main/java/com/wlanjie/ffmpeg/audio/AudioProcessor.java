package com.wlanjie.ffmpeg.audio;

import android.media.AudioRecord;

import com.wlanjie.ffmpeg.setting.AudioSetting;

/**
 * Created by wlanjie on 2017/6/24.
 */
public class AudioProcessor extends Thread {
  private byte[] mRecordBuffer;
  private int mRecordBufferSize;
  private AudioRecord mAudioRecord;
  private AudioSetting mAudioSetting;
  private volatile boolean mStop;
  private OnAudioRecordListener mOnAudioRecordListener;

  public AudioProcessor(AudioRecord audioRecord, AudioSetting audioSetting) {
    mAudioRecord = audioRecord;
    mAudioSetting = audioSetting;
    mRecordBufferSize = AudioUtils.getRecordBufferSize(audioSetting.getChannelCount(), audioSetting.getSampleRate());
    mRecordBuffer = new byte[2048];
  }

  public void setOnAudioRecordListener(OnAudioRecordListener l) {
    mOnAudioRecordListener = l;
  }

  public void stopEncode() {
    mStop = true;
    if (mAudioRecord != null) {
      mAudioRecord.stop();
      mAudioRecord.release();
      mAudioRecord = null;
    }
  }

  @Override
  public void run() {
    super.run();
    mAudioRecord.startRecording();
    while (!mStop) {
      int readLen = mAudioRecord.read(mRecordBuffer, 0, mRecordBuffer.length);
      if (readLen > 0) {
        if (mOnAudioRecordListener != null) {
          mOnAudioRecordListener.onAudioRecord(mRecordBuffer, readLen);
        }
      }
    }
  }
}
