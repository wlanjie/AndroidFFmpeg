package com.wlanjie.ffmpeg.library;

import android.hardware.Camera;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

/**
 * Created by wlanjie on 2016/10/9.
 */

class Preview implements Camera.PreviewCallback, SurfaceHolder.Callback {

    private final static String TAG = "wlanjie";

    private AudioRecord mAudioRecord;
    private boolean audioLoop = true;
    private Thread audioWorker;
    private Camera mCamera;
    private int mCameraId = Camera.getNumberOfCameras() - 1;
    private byte[] mYuvFrameBuffer;
    private final Parameters parameters;
    private final Frame frame;

    Preview(Parameters parameters, Frame frame) {
        this.parameters = parameters;
        this.frame = frame;
    }

    private void startPreview(SurfaceView surfaceView) throws IOException {
        if (mCamera != null) {
            Log.d(TAG, "start camera, already started.");
            return;
        }
        if (mCameraId > (Camera.getNumberOfCameras() - 1) || mCameraId < 0) {
            Log.e(TAG, "start camera failed. inviald params, camera no = " + mCameraId);
            return;
        }
        surfaceView.getHolder().addCallback(this);
        mCamera = Camera.open(mCameraId);
        Camera.Parameters p = mCamera.getParameters();
        Camera.Size size = mCamera.new Size(parameters.PREVIEW_WIDTH, parameters.PREVIEW_HEIGHT);
        if (!p.getSupportedPreviewSizes().contains(size)) {
            throw new IllegalArgumentException("Unsupported preview size " + size.width + " x " + size.height);
        }
        if (!p.getSupportedPictureSizes().contains(size)) {
            throw new IllegalArgumentException("Unsupported picture size " + size.width + " x " + size.height);
        }
        p.setPictureSize(parameters.PREVIEW_WIDTH, parameters.PREVIEW_HEIGHT);
        p.setPreviewSize(parameters.PREVIEW_WIDTH, parameters.PREVIEW_HEIGHT);
        int[] range = findCloseFpsRange(parameters.FPS, p.getSupportedPreviewFpsRange());
        p.setPreviewFpsRange(range[0], range[1]);
        p.setPreviewFormat(parameters.FORMAT);
        p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        p.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        p.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        if (!p.getSupportedFocusModes().isEmpty()) {
            p.setFocusMode(p.getSupportedFocusModes().get(0));
        }
        mCamera.setParameters(p);
        mCamera.setDisplayOrientation(90);
        mYuvFrameBuffer = new byte[parameters.PREVIEW_WIDTH * parameters.PREVIEW_HEIGHT * 3 / 2];
        mCamera.addCallbackBuffer(mYuvFrameBuffer);
        mCamera.setPreviewCallbackWithBuffer(this);
        mCamera.setPreviewDisplay(surfaceView.getHolder());
        mCamera.startPreview();
    }

    private void stopPreview() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private int[] findCloseFpsRange(int fps, List<int[]> fpsRanges) {
        fps *= 1000;
        int[] range = fpsRanges.get(0);
        int measure = Math.abs(range[0] - fps) + Math.abs(range[1] - fps);
        for (int[] fpsRange : fpsRanges) {
            if (fpsRange[0] <= fps && fpsRange[1] >= fps) {
                int curMeasure = Math.abs(fpsRange[0] - fps) + Math.abs(fpsRange[1] - fps);
                if (curMeasure < measure) {
                    range = fpsRange;
                    measure = curMeasure;
                }
            }
        }
        return range;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        frame.onGetYuvFrame(data);
        camera.addCallbackBuffer(mYuvFrameBuffer);
    }

    private void startRecordAudio() {
        if (mAudioRecord != null) {
            Log.d(TAG, "start record audio, already started.");
            return;
        }

        int bufferSize = AudioRecord.getMinBufferSize(parameters.SAMPLERATE, parameters.CHANNEL, parameters.AUDIO_FORMAT) * 2;
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, parameters.SAMPLERATE, parameters.CHANNEL, parameters.AUDIO_FORMAT, bufferSize);
        mAudioRecord.startRecording();
        byte[] pcmBuffer = new byte[4096];
        while (audioLoop && !Thread.interrupted()) {
            int size = mAudioRecord.read(pcmBuffer, 0, pcmBuffer.length);
            if (size <= 0) {
                break;
            }
            frame.onGetPcmFrame(pcmBuffer, size);
        }
    }

    private void stopRecordAudio() {
        audioLoop = false;
        if (mAudioRecord != null) {
            mAudioRecord.setRecordPositionUpdateListener(null);
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    public void startEncoder(SurfaceView surfaceView) throws IOException {
        startPreview(surfaceView);
        audioWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                startRecordAudio();
            }
        });
        audioLoop = true;
        audioWorker.start();
    }

    public void stopEncoder() {
        stopRecordAudio();
        stopPreview();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(holder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    interface Frame {
        void onGetYuvFrame(byte[] data);
        void onGetPcmFrame(byte[] data, int size);
    }
}
