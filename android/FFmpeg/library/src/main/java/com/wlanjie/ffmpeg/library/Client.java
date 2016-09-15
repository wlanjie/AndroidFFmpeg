package com.wlanjie.ffmpeg.library;

import android.graphics.SurfaceTexture;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by wlanjie on 16/9/10.
 */

public class Client {

    private final Object lock = new Object();
    private Parameters parameters = new Parameters();
    private AudioClient audioClient;
    private VideoClient videoClient;
    private RtmpSender rtmpSender;
    private FLVDataCollecter dataCollecter;
    private BufferedOutputStream bufferedOutputStream;

    public Client() {

    }

    public boolean prepare(final Config config) {
        synchronized (lock) {
            try {
                if (config.isPrintDetailMsg()) {
                    File file = new File(Environment.getExternalStorageDirectory(), "debug_aac.aac");
                    bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            parameters.filterMode = config.getFilterMode();
            parameters.rtmpAddress = config.getRtmpAddress();
            parameters.printDetailMsg = config.isPrintDetailMsg();
            parameters.senderQueueLength = 150;
            audioClient = new AudioClient(parameters);
            videoClient = new VideoClient(parameters);
            if (!audioClient.prepare(config) || !videoClient.prepare(config)) {
                return false;
            }
            rtmpSender = new RtmpSender();
            rtmpSender.prepare(parameters);
            dataCollecter = new FLVDataCollecter() {
                @Override
                public void collect(FLVData flvData, int type) {
                    rtmpSender.feed(flvData, type);
                    try {
                        if (config.isPrintDetailMsg()) {
                            bufferedOutputStream.write(flvData.byteBuffer, 0, flvData.byteBuffer.length);
                            bufferedOutputStream.flush();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            parameters.done = true;
        }
        return true;
    }

    /**
     * start streaming
     */
    public void startStreaming() {
        synchronized (lock) {
            videoClient.startStreaming(dataCollecter);
            rtmpSender.start(parameters.rtmpAddress);
            audioClient.start(dataCollecter);
        }
    }

    public void stopStreaming() {
        synchronized (lock) {
            audioClient.stop();
            videoClient.stopStreaming();
            rtmpSender.stop();
            try {
                if (bufferedOutputStream != null) {
                    bufferedOutputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void destroy() {
        synchronized (lock) {
            rtmpSender.destory();
            audioClient.destory();
            videoClient.destroy();
            rtmpSender = null;
            audioClient = null;
        }
    }

    public void startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight) {
        videoClient.startPreview(surfaceTexture, visualWidth, visualHeight);
    }

    public void updatePreview(int visualWidth, int visualHeight) {
        videoClient.updatePreview(visualWidth, visualHeight);
    }

    public void stopPreview() {
        videoClient.stopPreview();
    }

    public boolean swapCamera() {
        synchronized (lock) {
            return videoClient.swapCamera();
        }
    }

    public SurfaceVideoFilter acquireVideoFilter() {
        return videoClient.acquireSurfaceVideoFilter();
    }

    public void setVideoFilter(SurfaceVideoFilter videoFilter) {
        videoClient.setSurfaceVideoFilter(videoFilter);
    }

    public void releaseVideoFilter() {
        videoClient.releaseSurfaceVideoFilter();
    }

    public Size getVideoSize() {
        return new Size(parameters.videoWidth, parameters.videoHeight);
    }

    /**
     * set zoom by percent [0.0f, 1.0f]
     * @param targetPercent
     * @return
     */
    public void setZoomByPercent(float targetPercent) {
        videoClient.setZoomByPercent(targetPercent);
    }

    /**
     * toggle flash light
     * @return
     */
    public boolean toggleFlashLight() {
        return videoClient.toggleFlashLight();
    }

    /**
     * get video & audio real send speed
     * @return speed in B/s
     */
    public int getAVSpeed() {
        synchronized (lock) {
            return rtmpSender == null ? 0 : rtmpSender.getTotalSpeed();
        }
    }

    /**
     * use it to update filter property.<br/>
     * call it with {@link #releaseAudioFilter()}<br/>
     * make sure to release it in 3ms
     *
     * @return the audiofilter in use
     */
    public AudioFilter acquireAudioFilter() {
        return audioClient.acquireAudioFilter();
    }

    public String getServerIpAddress() {
        synchronized (lock) {
            return rtmpSender == null ? null : rtmpSender.getServerIpAddress();
        }
    }

    public float getDrawFrameRate() {
        synchronized (lock) {
            return videoClient == null ? 0 : videoClient.getDrawFrameRate();
        }
    }

    /**
     * get the rate of video frame sent by rtmp
     * @return
     */
    public float getSendFrameRate() {
        synchronized (lock) {
            return rtmpSender == null ? 0 : rtmpSender.getSendFrameRate();
        }
    }

    /**
     * set audiofilter.<br/>
     * can be called Repeatedly.<br/>
     * do NOT call it between {@link #acquireAudioFilter()} & {@link #releaseAudioFilter()}
     *
     * @param audioFilter audiofilter to apply
     */
    public void setAudioFilter(AudioFilter audioFilter) {
        audioClient.setAudioFilter(audioFilter);
    }

    public void releaseAudioFilter() {
        audioClient.releaseAudioFilter();
    }

    /**
     * get free percent of send buffer
     * return ~0.0 if the netspeed is not enough or net is blocked.
     * @return
     */
    public float getSendBufferFreePercent() {
        synchronized (lock) {
            return rtmpSender == null ? 0 : rtmpSender.getSendBufferFreePercent();
        }
    }
}
