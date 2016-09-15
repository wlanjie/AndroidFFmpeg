package com.wlanjie.ffmpeg.library;

import android.hardware.Camera;

/**
 * Created by wlanjie on 16/9/10.
 */

public class Config {

    private int filterMode;
    private Size targetVideoSize;
    private int videoBufferQueueNum;
    private int bitRate;
    private String rtmpAddress;
    private int renderingMode;
    private int defaultCamera;
    private int frontCameraDirectionMode;
    private int backCameraDirectionMode;
    private int videoFPS;
    private boolean printDetailMsg;

    public static class DirectionMode {
        public static final int FLAG_DIRECTION_FLIP_HORIZONTAL = Parameters.FLAG_DIRECTION_FLIP_HORIZONTAL;
        public static final int FLAG_DIRECTION_FLIP_VERTICAL = Parameters.FLAG_DIRECTION_FLIP_VERTICAL;
        public static final int FLAG_DIRECTION_ROATATION_0 = Parameters.FLAG_DIRECTION_ROATATION_0;
        public static final int FLAG_DIRECTION_ROATATION_90 = Parameters.FLAG_DIRECTION_ROATATION_90;
        public static final int FLAG_DIRECTION_ROATATION_180 = Parameters.FLAG_DIRECTION_ROATATION_180;
        public static final int FLAG_DIRECTION_ROATATION_270 = Parameters.FLAG_DIRECTION_ROATATION_270;
    }

    public static class RenderingMode {
        public static final int NativeWindow = Parameters.RENDERING_MODE_NATIVE_WINDOW;
        public static final int OpenGLES = Parameters.RENDERING_MODE_OPENGLES;
    }

    private Config() {
    }

    public static Config obtain() {
        Config config = new Config();
//        config.setFilterMode(FilterMode.SOFT);
//        config.setRenderingMode(RenderingMode.NativeWindow);
        config.setTargetVideoSize(new Size(1280, 720));
        config.setVideoFPS(15);
        config.setVideoBufferQueueNum(5);
        config.setBitRate(2000000);
        config.setPrintDetailMsg(false);
        config.setFilterMode(Parameters.FILTER_MODE_SOFT);
        config.setDefaultCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
        config.setBackCameraDirectionMode(DirectionMode.FLAG_DIRECTION_ROATATION_0);
        config.setFrontCameraDirectionMode(DirectionMode.FLAG_DIRECTION_ROATATION_0);
        return config;
    }


    /**
     * set the filter mode.
     *
     * @param filterMode {@link FilterMode}
     */
    public void setFilterMode(int filterMode) {
        this.filterMode = filterMode;
    }

    /**
     * set the default camera to start stream
     */
    public void setDefaultCamera(int defaultCamera) {
        this.defaultCamera = defaultCamera;
    }

    /**
     * set front camera rotation & flip
     *
     * @param frontCameraDirectionMode {@link DirectionMode}
     */
    public void setFrontCameraDirectionMode(int frontCameraDirectionMode) {
        this.frontCameraDirectionMode = frontCameraDirectionMode;
    }

    /**
     * set front camera rotation & flip
     *
     * @param backCameraDirectionMode {@link DirectionMode}
     */
    public void setBackCameraDirectionMode(int backCameraDirectionMode) {
        this.backCameraDirectionMode = backCameraDirectionMode;
    }

    /**
     * set  renderingMode when using soft mode<br/>
     * no use for hard mode
     *
     * @param renderingMode {@link RenderingMode}
     */
    public void setRenderingMode(int renderingMode) {
        this.renderingMode = renderingMode;
    }

    /**
     * no use for now
     *
     * @param printDetailMsg
     */
    public void setPrintDetailMsg(boolean printDetailMsg) {
        this.printDetailMsg = printDetailMsg;
    }

    /**
     * set the target video size.<br/>
     * real video size may different from it.Depend on device.
     *
     * @param videoSize
     */
    public void setTargetVideoSize(Size videoSize) {
        targetVideoSize = videoSize;
    }

    /**
     * set video buffer number for soft mode.<br/>
     * num larger:video Smoother,more memory.
     *
     * @param num
     */
    public void setVideoBufferQueueNum(int num) {
        videoBufferQueueNum = num;
    }

    /**
     * set video bitrate
     *
     * @param bitRate
     */
    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public int getVideoFPS() {
        return videoFPS;
    }

    public void setVideoFPS(int videoFPS) {
        this.videoFPS = videoFPS;
    }

    public int getVideoBufferQueueNum() {
        return videoBufferQueueNum;
    }

    public int getBitRate() {
        return bitRate;
    }

    public Size getTargetVideoSize() {
        return targetVideoSize;
    }

    public int getFilterMode() {
        return filterMode;
    }

    public int getDefaultCamera() {
        return defaultCamera;
    }

    public int getBackCameraDirectionMode() {
        return backCameraDirectionMode;
    }

    public int getFrontCameraDirectionMode() {
        return frontCameraDirectionMode;
    }

    public int getRenderingMode() {
        return renderingMode;
    }

    public String getRtmpAddress() {
        return rtmpAddress;
    }

    public void setRtmpAddress(String rtmpAddress) {
        this.rtmpAddress = rtmpAddress;
    }

    public boolean isPrintDetailMsg() {
        return printDetailMsg;
    }
}
