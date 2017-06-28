package com.wlanjie.streaming;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.IntDef;
import android.text.TextUtils;

import com.wlanjie.streaming.audio.AudioEncoder;
import com.wlanjie.streaming.audio.AudioProcessor;
import com.wlanjie.streaming.audio.AudioUtils;
import com.wlanjie.streaming.audio.OnAudioEncoderListener;
import com.wlanjie.streaming.audio.OnAudioRecordListener;
import com.wlanjie.streaming.camera.CameraView;
import com.wlanjie.streaming.rtmp.Rtmp;
import com.wlanjie.streaming.setting.AudioSetting;
import com.wlanjie.streaming.setting.CameraSetting;
import com.wlanjie.streaming.setting.StreamingSetting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public abstract class Encoder {

  public static final int SOFT_ENCODE = 0;

  public static final int HARD_ENCODE = 1;

  @IntDef({SOFT_ENCODE, HARD_ENCODE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface Encode {
  }

  private AudioSetting mAudioSetting;
  private CameraSetting mCameraSetting;
  private StreamingSetting mStreamingSetting;
  private AudioProcessor mAudioProcessor;
  private AudioEncoder mAudioEncoder;
  private byte[] aac = new byte[1024 * 1024];

  /**
   * first frame time
   */
  long mPresentTimeUs;

  Builder mBuilder;

  // Y, U (Cb) and V (Cr)
  // yuv420                     yuv yuv yuv yuv
  // yuv420p (planar)   yyyy*2 uu vv
  // yuv420sp(semi-planner)   yyyy*2 uv uv
  // I420 -> YUV420P   yyyy*2 uu vv
  // YV12 -> YUV420P   yyyy*2 vv uu
  // NV12 -> YUV420SP  yyyy*2 uv uv
  // NV21 -> YUV420SP  yyyy*2 vu vu
  // NV16 -> YUV422SP  yyyy uv uv
  // YUY2 -> YUV422SP  yuyv yuyvo

  public static class Builder {
    protected CameraView cameraView;
    protected int encode;
    protected int width = 360;
    protected int height = 640;
    protected int fps = 24;
    protected int audioSampleRate = 44100;
    protected int audioBitRate = 32 * 1000; // 32 kbps
    protected int previewWidth;
    protected int previewHeight;
    protected int videoBitRate = 500 * 1000; // 500 kbps
    protected String videoCodec = "video/avc";
    protected String audioCodec = "audio/mp4a-latm";

    public Builder setCameraView(CameraView cameraView) {
      this.cameraView = cameraView;
      return this;
    }

    public Builder setSoftEncoder(@Encode int encode) {
      this.encode = encode;
      return this;
    }

    public Builder setWidth(int width) {
      this.width = width;
      return this;
    }

    public Builder setHeight(int height) {
      this.height = height;
      return this;
    }

    public Builder setFps(int fps) {
      this.fps = fps;
      return this;
    }

    public Builder setAudioSampleRate(int audioSampleRate) {
      this.audioSampleRate = audioSampleRate;
      return this;
    }

    public Builder setAudioBitRate(int audioBitRate) {
      this.audioBitRate = audioBitRate;
      return this;
    }

    public Encoder build() {
      return null;
    }
  }

  public Encoder(Builder builder) {
    mBuilder = builder;
  }

  public void prepare(CameraSetting cameraSetting, StreamingSetting streamingSetting, AudioSetting audioSetting) {
    mCameraSetting = cameraSetting;
    mStreamingSetting = streamingSetting;
    mAudioSetting = audioSetting;
    mAudioProcessor = new AudioProcessor(AudioUtils.getAudioRecord(audioSetting), audioSetting);
  }

  public void startStreaming() {
    if (mStreamingSetting == null) {
      throw new IllegalArgumentException("StreamingSetting is null.");
    }
    if (TextUtils.isEmpty(mStreamingSetting.getRtmpUrl()) || !mStreamingSetting.getRtmpUrl().startsWith("rtmp://")) {
      throw new IllegalArgumentException("url must be rtmp://");
    }
    mPresentTimeUs = System.nanoTime() / 1000;
    mBuilder.previewWidth = mBuilder.cameraView.getSurfaceWidth();
    mBuilder.previewHeight = mBuilder.cameraView.getSurfaceHeight();
    setEncoderResolution(mBuilder.width, mBuilder.height);

    mAudioProcessor.start();
//    mAudioProcessor.setOnAudioRecordListener(new OnAudioRecordListener() {
//      @Override
//      public void onAudioRecord(byte[] buffer, int size) {
//        if (mBuilder.encode == SOFT_ENCODE) {
//          convertPcmToAac(buffer, size);
//        } else {
//          if (mAudioEncoder == null) {
//            mAudioEncoder = new AudioEncoder();
//            mAudioEncoder.setOnAudioEncoderListener(new OnAudioEncoderListener() {
//              @Override
//              public void onAudioEncode(ByteBuffer bb, MediaCodec.BufferInfo bi) {
//                int outBitSize = bi.size;
//                int outPacketSize = outBitSize + 7;
//                bb.position(bi.offset);
//                bb.limit(bi.offset + outBitSize);
//                addADTStoPacket(aac, outPacketSize);
//                bb.get(aac, 7, outBitSize);
//                bb.position(bi.offset);
//                muxerAac(aac, outPacketSize, (int) (bi.presentationTimeUs / 1000));
//              }
//            });
//            mAudioEncoder.offerEncoder(buffer);
//          }
//        }
//      }
//    });

    mBuilder.cameraView.addCallback(new CameraView.Callback() {
      @Override
      public void onPreviewFrame(CameraView cameraView, byte[] data) {
        super.onPreviewFrame(cameraView, data);
        rgbaEncoderToH264(data);
      }
    });

    int result = Rtmp.connect(mStreamingSetting.getRtmpUrl());
    if (result != 0) {
      throw new RuntimeException("connect rtmp server error.");
    }
    new Thread(){
      @Override
      public void run() {
        super.run();
        Rtmp.startPublish();
      }
    }.start();
  }

  /**
   * open h264 aac encoder
   *
   * @return true success, false failed
   */
  abstract boolean openEncoder();

  /**
   * close h264 aac encoder
   */
  abstract void closeEncoder();

  /**
   * covert pcm to aac
   *
   * @param aacFrame pcm data
   * @param size     pcm data size
   */
  abstract void convertPcmToAac(byte[] aacFrame, int size);

  /**
   * convert yuv to h264
   *
   * @param data yuv data
   */
  abstract void rgbaEncoderToH264(byte[] data);

  /**
   * stop audio record
   */
  private void stopAudioRecord() {
    mAudioProcessor.stopEncode();
  }

  /**
   * stop camera preview, audio record and close encoder
   */
  public void stop() {
    mBuilder.cameraView.stop();
    stopAudioRecord();
    closeEncoder();
    Rtmp.destroy();
  }

  /**
   * Add ADTS header at the beginning of each and every AAC packet.
   * This is needed as MediaCodec encoder generates a packet of raw
   * AAC data.
   * <p>
   * Note the packetLen must count in the ADTS header itself.
   **/
  protected void addADTStoPacket(byte[] packet, int packetLen) {
    int profile = 2;  //AAC LC
    //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
    int freqIdx = 4;  //44.1KHz
    int chanCfg = 2;  //CPE

    // fill in ADTS data
    packet[0] = (byte) 0xFF;
    packet[1] = (byte) 0xF9;
    packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
    packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
    packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
    packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
    packet[6] = (byte) 0xFC;
  }

  /**
   * set output width and height
   *
   * @param outWidth  output width
   * @param outHeight output height
   */
  public native void setEncoderResolution(int outWidth, int outHeight);

  /**
   * muxer flv h264 data
   *
   * @param data h264 data
   * @param pts  pts
   */
  protected native void muxerH264(byte[] data, int size, int pts);

  /**
   * muxer flv aac data
   *
   * @param data aac data
   * @param pts  pts
   */
  protected native void muxerAac(byte[] data, int size, int pts);

  protected native byte[] rgbaToI420(byte[] rgbaFrame, int width, int height, boolean flip, int rotate);

  static {
    System.loadLibrary("wlanjie");
  }
}
