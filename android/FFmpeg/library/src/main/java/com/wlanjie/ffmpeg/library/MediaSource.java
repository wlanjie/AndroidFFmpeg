package com.wlanjie.ffmpeg.library;

/**
 * Created by wlanjie on 16/5/16.
 */
class MediaSource {
    private String inputDataSource;
    private String outputDataSource;
    private int width;
    private int height;
    private double rotation;
    private int duration;

    public String getInputDataSource() {
        return inputDataSource;
    }

    public void setInputDataSource(String inputDataSource) {
        this.inputDataSource = inputDataSource;
    }

    public String getOutputDataSource() {
        return outputDataSource;
    }

    public void setOutputDataSource(String outputDataSource) {
        this.outputDataSource = outputDataSource;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public double getRotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}
