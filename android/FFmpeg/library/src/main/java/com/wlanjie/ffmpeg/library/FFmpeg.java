package com.wlanjie.ffmpeg.library;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Created by wlanjie on 16/4/26.
 */
public class FFmpeg {

    static {
        System.loadLibrary("ffmpeg");
        System.loadLibrary("wlanjie");
    }

    private volatile static FFmpeg instance;

    protected FFmpeg() {}

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

    private MediaSource mediaSource = new MediaSource();

    /**
     * 设置视频输入和输出文件
     * @param inputFile 输入视频文件
     * @throws FileNotFoundException 如果输入视频文件不存在抛出此异常
     * @throws IllegalArgumentException 如果输入文件和输出文件为null,抛出此异常
     */
    public void openInput(File inputFile) throws FileNotFoundException, IllegalArgumentException {
        if (inputFile == null) {
            throw new IllegalArgumentException("input file must be not null");
        }
        if (!inputFile.exists()) {
            throw new FileNotFoundException("input file not found");
        }
        mediaSource.setInputDataSource(inputFile.getAbsolutePath());
    }

    /**
     * 打开输入文件
     * @return >= 0 success, <0 error
     * @throws IllegalStateException 不能打开时,或者打开的路径为空时抛出此异常
     */
    public native int openInput(String inputPath) throws IllegalStateException, IllegalArgumentException;

    /**
     * 获取视频的宽
     * @return 视频的宽
     * @throws FileNotFoundException 如果视频文件不存在,则抛出此异常
     */
    public int getVideoWidth() {
        return mediaSource.getWidth();
    }
//    public native int getVideoWidth() throws FileNotFoundException, IllegalStateException;

    /**
     * 获取视频的高
     * @return 视频的高
     * @throws FileNotFoundException 如果视频文件不存在,则抛出此异常
     */
    public int getVideoHeight() {
        return mediaSource.getHeight();
    }
//    public native int getVideoHeight() throws FileNotFoundException, IllegalStateException;

    /**
     * 压缩视频,视频采用h264编码,音频采用aac编码,此方法是阻塞式操作,如果在ui线程操作,会产生anr异常
     * 如果需要缩放,指定高度的值,宽度-1,则宽度根据指定的高度自动计算而得
     * 如果指定宽度的值,高度为-1,则高度根据指定的宽度自动计算而得
     * 如果宽和高同时为-1,则不缩放
     * @param newWidth 压缩的视频宽,如果需要保持原来的宽,则是0,如果指定高度为原视频的一半,宽度传入-1,则根据高度自动计算而得
     * @param newHeight 压缩的视频高,如果需要保持原来的高,则是0, 如果指定宽度为原视频的一半,高度传入-1,则根据宽度自动计算而得
     * @throws FileNotFoundException 如果文件不存在抛出此异常
     * @throws IllegalStateException
     */

    public native int compress(String outputPath, int width, int height) throws FileNotFoundException;

    /**
     * 获取视频的角度
     * @return 视频的角度
     */
    public double getRotation() {
        return mediaSource.getRotation();
    }
//    public native double getRotation();

    /**
     * 裁剪视频
     * @param x 视频x坐标
     * @param y 视频y坐标
     * @param width 裁剪视频之后的宽度
     * @param height 裁剪视频之后的高度
     */
    public native int crop(String outputPath, int x, int y, int width, int height);

    /**
     * 释放资源
     */
    public native void release();
}
