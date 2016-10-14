package com.wlanjie.ffmpeg.library;

import android.graphics.ImageFormat;
import android.media.AudioFormat;

/**
 * Created by wlanjie on 2016/10/9.
 */

public class Parameters {
    public String url = "rtmp://192.168.1.102/live/test";
    public int PREVIEW_WIDTH = 1280;
    public int PREVIEW_HEIGHT = 720;
    public int VIDEO_WIDTH = 480;
    public int VIDEO_HEIGHT = 840;
    public int BITRATE = 500 * 1000; // 500kbps
    public int FPS = 24;
    public int GOP = 48;
    public int FORMAT = ImageFormat.NV21;
    public int SAMPLERATE = 44100;
    public int CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    public int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public int AUDIO_BITRATE = 32 * 1000; //32kbps
}
