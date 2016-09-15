package com.wlanjie.ffmpeg.library;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by wlanjie on 16/9/10.
 */

public class VideoClient {

    private final Object lock = new Object();
    private final Parameters parameters;
    private boolean isStreaming;
    private boolean isPreviewing;
    private int cameraNumber;
    private int currentCameraIndex;
    private Camera camera;
    private int targetFps = 30000;
    private int[] supportedSrcVideoFrameColorType = new int[] {ImageFormat.NV21, ImageFormat.YV12};
    private VideoCore videoCore;
    private SurfaceTexture cameraTexture;

    public VideoClient(Parameters parameters) {
        this.parameters = parameters;
        cameraNumber = Camera.getNumberOfCameras();
        currentCameraIndex = Camera.CameraInfo.CAMERA_FACING_BACK;
        isStreaming = false;
        isPreviewing = false;
    }

    public boolean prepare(Config config) {
        synchronized (lock) {
            if ((cameraNumber - 1) >= config.getDefaultCamera()) {
                currentCameraIndex = config.getDefaultCamera();
            }
            if (createCamera(currentCameraIndex) == null) {
                return false;
            }
            Camera.Parameters cameraParameters = camera.getParameters();
            selectCameraPreview(cameraParameters, config.getTargetVideoSize());
            selectCameraFpsRange(cameraParameters);
            //TODO fps
//            if (config.getVideoFPS() > parameters.previewMaxFps / 1000) {
//                parameters.videoFPS = parameters.previewMaxFps / 1000;
//            } else {
                parameters.videoFPS = config.getVideoFPS();
//            }
            resoveResolution(config);

            if (!selectCameraColorFormat(cameraParameters)) {
                parameters.dump();
                return false;
            }
            if (!configCamera()) {
                parameters.dump();
                return false;
            }
            switch (parameters.filterMode) {
                case Parameters.FILTER_MODE_SOFT:
                    videoCore = new SurfaceVideoCore(parameters);
                    break;
                case Parameters.FILTER_MODE_HARD:
                    videoCore = new HardVideoCore(parameters);
                    break;
            }
            if (!videoCore.prepare(config))
                return false;
            videoCore.setCurrentCamera(currentCameraIndex);
            prepareVideo();
            return true;
        }
    }

    private void prepareVideo() {
        if (parameters.filterMode == Parameters.FILTER_MODE_SOFT) {
            camera.addCallbackBuffer(new byte[parameters.previewBufferSize]);
//            camera.addCallbackBuffer(new byte[parameters.previewBufferSize]);
        }
    }

    private boolean startVideo() {
        cameraTexture = new SurfaceTexture(VideoCore.OVERWATCH_TEXTURE_ID);
        if (parameters.filterMode == Parameters.FILTER_MODE_SOFT) {
            camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    synchronized (lock) {
                        if (videoCore != null && data != null) {
                            ((SurfaceVideoCore) videoCore).queueVideo(data);
                        }
                        camera.addCallbackBuffer(data);
                    }
                }
            });
        } else {
            cameraTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    synchronized (lock) {
                        if (videoCore != null) {
                            ((HardVideoCore) videoCore).onFrameAvailable();
                        }
                    }
                }
            });
        }
        try {
            camera.setPreviewTexture(cameraTexture);
        } catch (IOException e) {
            e.printStackTrace();
            camera.release();
            return false;
        }
        camera.startPreview();
        return true;
    }

    public boolean startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight) {
        synchronized (lock) {
            if (!isStreaming && !isPreviewing) {
                if (!startVideo()) {
                    parameters.dump();
                    return false;
                }
                videoCore.updateCameraTexture(cameraTexture);
            }
            videoCore.startPreview(surfaceTexture, visualWidth, visualHeight);
            isPreviewing = true;
            return true;
        }
    }

    public void updatePreview(int visualWidth, int visualHeight) {
        videoCore.updatePreview(visualWidth, visualHeight);
    }

    public boolean stopPreview() {
        synchronized (lock) {
            if (isPreviewing) {
                videoCore.stopPreview();
                if (!isStreaming) {
                    camera.stopPreview();
                    videoCore.updateCameraTexture(null);
                    cameraTexture.release();
                }
            }
            isPreviewing = false;
            return true;
        }
    }

    public boolean startStreaming(FLVDataCollecter dataCollecter) {
        synchronized (lock) {
            if (!isStreaming && !isPreviewing) {
                if (!startVideo()) {
                    parameters.dump();
                    return false;
                }
                videoCore.updateCameraTexture(cameraTexture);
            }
            videoCore.startStreaming(dataCollecter);
            isStreaming = true;
            return true;
        }
    }

    public void stopStreaming() {
        synchronized (lock) {
            if (isStreaming) {
                videoCore.stopStreaming();
                if (!isPreviewing) {
                    camera.stopPreview();
                    videoCore.updateCameraTexture(null);
                    cameraTexture.release();
                }
            }
            isStreaming = false;
        }
    }

    public void destroy() {
        synchronized (lock) {
            camera.release();
            videoCore.destroy();
            videoCore = null;
            camera = null;
        }
    }

    public boolean swapCamera() {
        synchronized (lock) {
            camera.stopPreview();
            camera.release();
            camera = null;
            if (null == createCamera(currentCameraIndex = (++currentCameraIndex) % cameraNumber)) {
                return false;
            }
            videoCore.setCurrentCamera(currentCameraIndex);
            selectCameraFpsRange(camera.getParameters());
            if (!configCamera()) {
                camera.release();
                return false;
            }
            prepareVideo();
            cameraTexture.release();
            videoCore.updateCameraTexture(null);
            startVideo();
            videoCore.updateCameraTexture(cameraTexture);
            return true;
        }
    }

    public boolean toggleFlashLight() {
        synchronized (lock) {
            try {
                Camera.Parameters cameraParameters = camera.getParameters();
                List<String> flashModes = cameraParameters.getSupportedFlashModes();
                String flashMode = cameraParameters.getFlashMode();
                if (!Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                    cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    camera.setParameters(cameraParameters);
                    return true;
                } else if (!Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                    cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    camera.setParameters(cameraParameters);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return false;
        }
    }

    public void setZoomByPercent(float targetPercent) {
        targetPercent = Math.min(Math.max(0f, targetPercent), 1f);
        Camera.Parameters p = camera.getParameters();
        p.setZoom((int) (p.getMaxZoom() * targetPercent));
        camera.setParameters(p);
    }

    public SurfaceVideoFilter acquireSurfaceVideoFilter() {
        if (parameters.filterMode == Parameters.FILTER_MODE_SOFT) {
            return ((SurfaceVideoCore) videoCore).acquireVideoFilter();
        }
        return null;
    }

    public void releaseSurfaceVideoFilter() {
        if (parameters.filterMode == Parameters.FILTER_MODE_SOFT) {
            ((SurfaceVideoCore) videoCore).releaseVideoFilter();
        }
    }

    public void setSurfaceVideoFilter(SurfaceVideoFilter surfaceVideoFilter) {
        if (parameters.filterMode == Parameters.FILTER_MODE_SOFT) {
            ((SurfaceVideoCore) videoCore).setVideoFilter(surfaceVideoFilter);
        }
    }

    public HardVideoFilter acquireHardVideoFilter() {
        if (parameters.filterMode == Parameters.FILTER_MODE_HARD) {
            return ((HardVideoCore) videoCore).acquireVideoFilter();
        }
        return null;
    }

    public void releaseHardVideoFilter() {
        if (parameters.filterMode == Parameters.FILTER_MODE_HARD) {
            ((HardVideoCore) videoCore).releaseVideoFilter();
        }
    }

    public void setHardVideoFilter(HardVideoFilter hardVideoFilter) {
        if (parameters.filterMode == Parameters.FILTER_MODE_SOFT) {
            ((HardVideoCore) videoCore).setVideoFilter(hardVideoFilter);
        }
    }

    public void takeScreenShot(ScreenShotListener l) {
        synchronized (lock) {
            if (videoCore != null) {
                videoCore.takeScreenShot(l);
            }
        }
    }

    public float getDrawFrameRate() {
        synchronized (lock) {
            return videoCore == null ? 0 : videoCore.getDrawFrameRate();
        }
    }

    private boolean configCamera() {
        if (camera == null) return false;
        Camera.Parameters cameraParameters = camera.getParameters();
        cameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        List<String> focusModes = cameraParameters.getSupportedFocusModes();
        if (focusModes != null) {
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            }
        }
        //不设置fps会花屏,绿底
        cameraParameters.setPreviewSize(parameters.previewVideoWidth, parameters.previewVideoHeight);
        cameraParameters.setPreviewFpsRange(parameters.previewMinFps, parameters.previewMaxFps);
        camera.setParameters(cameraParameters);
        return true;
    }

    private boolean selectCameraColorFormat(Camera.Parameters cameraParameters) {
        List<Integer> srcColorTypes = new LinkedList<>();
        List<Integer> supportedPreviewFormats = cameraParameters.getSupportedPreviewFormats();
        for (int colorType : supportedSrcVideoFrameColorType) {
            if (supportedPreviewFormats.contains(colorType)) {
                srcColorTypes.add(colorType);
            }
        }
        // select preview colorFormat
        if (srcColorTypes.contains(parameters.previewColorFormat = ImageFormat.NV21)) {
            parameters.previewColorFormat = ImageFormat.NV21;
        } else if (srcColorTypes.contains(parameters.previewColorFormat = ImageFormat.YV12)) {
            parameters.previewColorFormat = ImageFormat.YV12;
        } else {
            return false;
        }
        return true;
    }

    private void selectCameraFpsRange(Camera.Parameters cameraParameters) {
        List<int[]> fpsRange = cameraParameters.getSupportedPreviewFpsRange();
        Collections.sort(fpsRange, new Comparator<int[]>() {
            @Override
            public int compare(int[] lhs, int[] rhs) {
                int r = Math.abs(lhs[0] - targetFps) + Math.abs(lhs[1] - targetFps);
                int l = Math.abs(rhs[0] - targetFps) + Math.abs(rhs[1] - targetFps);
                if (r > l) {
                    return 1;
                } else if (r < l) {
                    return -1;
                }
                return 0;
            }
        });
        parameters.previewMinFps = fpsRange.get(0)[0];
        parameters.previewMaxFps = fpsRange.get(0)[1];
    }

    private void selectCameraPreview(Camera.Parameters cameraParameters, Size targetSize) {
        List<Camera.Size> previewsSizes = cameraParameters.getSupportedPreviewSizes();
        Collections.sort(previewsSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size lhs, Camera.Size rhs) {
                if ((lhs.width * lhs.height) > (rhs.width * rhs.height)) {
                    return 1;
                }
                return -1;
            }
        });
        for (Camera.Size previewsSize : previewsSizes) {
            if (previewsSize.width >= targetSize.getWidth() && previewsSize.height >= targetSize.getHeight()) {
                parameters.previewVideoWidth = previewsSize.width;
                parameters.previewVideoHeight = previewsSize.height;
                break;
            }
        }
    }

    private void resoveResolution(Config config) {
        if (parameters.filterMode == Parameters.FILTER_MODE_SOFT) {
            if (parameters.isPortrait) {
                parameters.videoHeight = parameters.previewVideoWidth;
                parameters.videoWidth = parameters.previewVideoHeight;
            } else {
                parameters.videoWidth = parameters.previewVideoWidth;
                parameters.videoHeight = parameters.previewVideoHeight;
            }
        } else {
            float pw, ph, vw, vh;
            if (parameters.isPortrait) {
                parameters.videoHeight = config.getTargetVideoSize().getWidth();
                parameters.videoWidth = config.getTargetVideoSize().getHeight();
                pw = parameters.previewVideoHeight;
                ph = parameters.previewVideoWidth;
            } else {
                parameters.videoWidth = config.getTargetVideoSize().getWidth();
                parameters.videoHeight = config.getTargetVideoSize().getHeight();
                pw = parameters.previewVideoWidth;
                ph = parameters.previewVideoHeight;
            }
            vw = parameters.videoWidth;
            vh = parameters.videoHeight;
            float pr = ph / pw, vr = vh / vw;
            if (pr == vr) {
                parameters.cropRatio = 0.0f;
            } else if (pr > vr) {
                parameters.cropRatio = (1.0f - vr / pr) / 2.0f;
            } else {
                parameters.cropRatio = -(1.0f - pr / vr) / 2.0f;
            }
        }
    }

    private Camera createCamera(int cameraId) {
        try {
            camera = Camera.open(cameraId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return camera;
    }

}
