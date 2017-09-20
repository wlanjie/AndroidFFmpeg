package com.wlanjie.ffmpeg;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Created by wlanjie on 16/4/26.
 */
public class FFmpeg {

  private static final String TAG = "FFmpeg";

  static {
    System.loadLibrary("ffmpeg");
    System.loadLibrary("wlanjie");
  }

  private volatile static FFmpeg instance;

  private FFmpeg() {
  }

  public static FFmpeg getInstance() {
    if (instance == null) {
      synchronized (FFmpeg.class) {
        if (instance == null) {
          instance = new FFmpeg();
        }
      }
    }
    return instance;
  }


  /**
   * 设置视频输入和输出文件
   *
   * @param inputFile 输入视频文件
   * @throws FileNotFoundException    如果输入视频文件不存在抛出此异常
   * @throws IllegalArgumentException 如果输入文件和输出文件为null,抛出此异常
   */
  public void openInput(File inputFile) throws FileNotFoundException, IllegalArgumentException {
    if (inputFile == null) {
      throw new IllegalArgumentException("input file must be not null");
    }
    if (!inputFile.exists()) {
      throw new FileNotFoundException("input file not found");
    }
    openInput(inputFile.getAbsolutePath());
  }

  /**
   * 打开输入文件
   *
   * @return >= 0 success, <0 error
   * @throws IllegalStateException 不能打开时,或者打开的路径为空时抛出此异常
   */
  public native int openInput(String inputPath) throws IllegalStateException, IllegalArgumentException;

  /**
   * 设置视频输入和输出文件
   *
   * @param outputFile 输入视频文件
   * @throws IllegalArgumentException 如果输出文件为null,抛出此异常
   */
  public void openOutput(File outputFile) throws IllegalArgumentException {
    if (outputFile == null) {
      throw new IllegalArgumentException("output file must be not null");
    }
    openOutput(outputFile.getAbsolutePath());
  }

  public native int openOutput(String outputPath);

  public native List<Bitmap> getVideoFrame();

  public native int scale(int newWidth, int newHeight);

  public native Video getVideoInfo();

  public native int beginSection();

  public native int endSection();

  public native int encoderVideo(byte[] frame);

  public native int encoderAudio(byte[] audio);

  /**
   * 释放资源
   */
  public native void release();

}
