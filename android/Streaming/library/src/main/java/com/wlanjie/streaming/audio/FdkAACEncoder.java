package com.wlanjie.streaming.audio;

/**
 * Created by caowu15 on 2017/6/27.
 */

public class FdkAACEncoder {

  public native static boolean openEncoder(int channelCount, int sampleRate, int bitRate);

  public native static void closeEncoder();

  public native static int encode(byte[] data, int size);
}
