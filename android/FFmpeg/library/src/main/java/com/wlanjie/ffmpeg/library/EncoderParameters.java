package com.wlanjie.ffmpeg.library;

/**
 * Created by wlanjie on 16/9/7.
 */

public class EncoderParameters {

    // recoder
    public int audioRecoderFormat;
    public int audioRecoderChannelConfig;
    public int audioRecoderSliceSize;
    public int audioRecoderBufferSize;
    public int audioRecoderSource;
    public int audioRecoderSampleRate;

    //encoder
    public int mediacodecAACProfile;
    public int mediacodecAACSampleRate;
    public int mediacodecAACChannelCount;
    public int mediacodecAACBitRate;
    public int mediacodecAACMaxInputSize;

    public int audioBufferQueueNum;
}
