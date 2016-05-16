package com.wlanjie.ffmpeg.library;

import java.io.FileNotFoundException;

/**
 * Created by wlanjie on 16/4/26.
 */
public class FFmpeg {

    static {
        System.loadLibrary("ffmpeg");
        System.loadLibrary("wlanjie");
    }

    /**
     * 获取视频的宽
     * @param inputPath 视频文件
     * @return 视频的宽
     * @throws FileNotFoundException 如果视频文件不存在,则抛出此异常
     */
    public static native int getVideoWidth(String inputPath) throws FileNotFoundException, IllegalStateException;

    /**
     * 获取视频的高
     * @param inputPath 视频文件
     * @return 视频的高
     * @throws FileNotFoundException 如果视频文件不存在,则抛出此异常
     */
    public static native int getVideoHeight(String inputPath) throws FileNotFoundException, IllegalStateException;

    /**
     * 压缩视频,视频采用h264编码,音频采用aac编码,此方法是阻塞式操作,如果在ui线程操作,会产生anr异常
     * 如果需要缩放,指定高度的值,宽度-1,则宽度根据指定的高度自动计算而得
     * 如果指定宽度的值,高度为-1,则高度根据指定的宽度自动计算而得
     * 如果宽和高同时为-1,则不缩放
     * @param inputPath 原视频文件
     * @param outputPath 输出的视频文件,如果文件已经存在则覆盖
     * @param newWidth 压缩的视频宽,如果需要保持原来的宽,则是0,如果指定高度为原视频的一半,宽度传入-1,则根据高度自动计算而得
     * @param newHeight 压缩的视频高,如果需要保持原来的高,则是0, 如果指定宽度为原视频的一半,高度传入-1,则根据宽度自动计算而得
     * @throws FileNotFoundException 如果文件不存在抛出此异常
     * @throws IllegalStateException
     */

    public static native int compress(String inputPath, String outputPath, int newWidth, int newHeight) throws FileNotFoundException, IllegalStateException;

    /**
     * 获取视频的角度
     * @param inputPath
     * @return
     */
    public static native double getRotation(String inputPath);

    /**
     * 释放资源
     */
    public static native void release();
}
