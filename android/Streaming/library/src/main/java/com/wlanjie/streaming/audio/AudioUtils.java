package com.wlanjie.streaming.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.wlanjie.streaming.setting.AudioSetting;

/**
 * Created by wlanjie on 2017/6/24.
 */
public class AudioUtils {

  public static boolean checkMicSupport(AudioSetting audioSetting) {
    boolean result;
    int recordBufferSize = getRecordBufferSize(audioSetting.getChannelCount(), audioSetting.getSampleRate());
    byte[] mRecordBuffer = new byte[recordBufferSize];
    AudioRecord audioRecord = getAudioRecord(audioSetting);
    try {
      audioRecord.startRecording();
    } catch (Exception e) {
      e.printStackTrace();
    }
    int readLen = audioRecord.read(mRecordBuffer, 0, recordBufferSize);
    result = readLen >= 0;
    try {
      audioRecord.release();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  public static int getRecordBufferSize(int channelCount, int sampleRate) {
    int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    if(channelCount == 2) {
      channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    }
    return AudioRecord.getMinBufferSize(sampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
  }

  public static AudioRecord getAudioRecord(AudioSetting audioSetting) {
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    if (audioSetting.getChannelCount() == 2) {
      channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
    }
    int audioSource = MediaRecorder.AudioSource.MIC;
    if (audioSetting.isAec()) {
      audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
    }
    return new AudioRecord(audioSource, audioSetting.getSampleRate(), channelConfiguration, audioSetting.getAudioEncoding(), getRecordBufferSize(audioSetting.getChannelCount(), audioSetting.getSampleRate()));
  }
}
