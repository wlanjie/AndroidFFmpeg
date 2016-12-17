/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wlanjie.streaming.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

@TargetApi(21)
class Camera2 extends CameraViewImpl {

    private static final String TAG = "Camera2";

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static {
        INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private final CameraManager mCameraManager;

    private final CameraDevice.StateCallback mCameraDeviceCallback
            = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            Size size = chooseOptimalSize();
            startPreview(size.getWidth(), size.getHeight());
            mCallback.onCameraOpened(size.getWidth(), size.getHeight());
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            mCallback.onCameraClosed();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "onError: " + camera.getId() + " (" + error + ")");
            mCamera = null;
        }

    };

    private final CameraCaptureSession.StateCallback mSessionCallback
            = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (mCamera == null) {
                return;
            }
            mCaptureSession = session;
            updateAutoFocus();
            updateFlash();
            try {
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                        mCaptureCallback, null);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to start camera preview because it couldn't access camera", e);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to start camera preview.", e);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "Failed to configure capture session.");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            if (mCaptureSession != null && mCaptureSession.equals(session)) {
                mCaptureSession = null;
            }
        }

    };
//
//    PictureCaptureCallback mCaptureCallback = new PictureCaptureCallback() {
//
//        @Override
//        public void onPrecaptureRequired() {
//            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
//            setState(STATE_PRECAPTURE);
//            try {
//                mCaptureSession.capture(mPreviewRequestBuilder.build(), this, null);
//                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
//            } catch (CameraAccessException e) {
//                Log.e(TAG, "Failed to run precapture sequence.", e);
//            }
//        }
//
//        @Override
//        public void onReady() {
//
//        }
//
//    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            try (Image image = reader.acquireNextImage()) {
                Image.Plane[] planes = image.getPlanes();
                if (planes.length > 0) {

                    Image.Plane Y = image.getPlanes()[0];
                    Image.Plane U = image.getPlanes()[1];
                    Image.Plane V = image.getPlanes()[2];

                    int Yb = Y.getBuffer().remaining();
                    int Ub = U.getBuffer().remaining();
                    int Vb = V.getBuffer().remaining();

                    byte[] data = new byte[Yb + Ub + Vb];


                    Y.getBuffer().get(data, 0, Yb);
                    U.getBuffer().get(data, Yb, Ub);
                    V.getBuffer().get(data, Yb + Ub, Vb);
                    mCallback.onPreviewFrame(data);
//                    ByteBuffer buffer = planes[0].getBuffer();
//                    byte[] data = new byte[buffer.remaining()];
//                    buffer.get(data);
//                    mCallback.onPreviewFrame(data);
                }
            }
        }

    };


    private String mCameraId;

    private CameraCharacteristics mCameraCharacteristics;

    CameraDevice mCamera;

    CameraCaptureSession mCaptureSession;

    CameraCaptureSession mPreviewSession;

    CaptureRequest.Builder mPreviewRequestBuilder;

    private Handler mBackgroundHandler;

    private HandlerThread mBackgroundThread;

    private ImageReader mImageReader;

    private final SizeMap mPreviewSizes = new SizeMap();

    private final SizeMap mPictureSizes = new SizeMap();

    private int mFacing;

    private AspectRatio mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;

    private boolean mAutoFocus;

    private int mFlash;

    private int mDisplayOrientation;

    Camera2(CameraCallback callback, Context context) {
        super(callback);
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
//        mPreview.setCallback(new PreviewImpl.Callback() {
//                @Override
//                public void onSurfaceChanged() {
//                    startPreview();
//                }
//            });
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    void start() {
        startBackgroundThread();
        chooseCameraIdByFacing();
        collectCameraInfo();
        startOpeningCamera();
    }

    @Override
    void stop() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mBackgroundHandler != null && mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    boolean isCameraOpened() {
        return mCamera != null;
    }

    @Override
    void setFacing(int facing) {
        if (mFacing == facing) {
            return;
        }
        mFacing = facing;
        if (isCameraOpened()) {
            stop();
            start();
        }
    }

    @Override
    int getFacing() {
        return mFacing;
    }

    @Override
    Set<AspectRatio> getSupportedAspectRatios() {
        return mPreviewSizes.ratios();
    }

    @Override
    void setAspectRatio(AspectRatio ratio) {
        if (ratio == null || ratio.equals(mAspectRatio) ||
                !mPreviewSizes.ratios().contains(ratio)) {
            // TODO: Better error handling
            return;
        }
        mAspectRatio = ratio;
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
//            startPreview();
        }
    }

    @Override
    AspectRatio getAspectRatio() {
        return mAspectRatio;
    }

    @Override
    void setAutoFocus(boolean autoFocus) {
        if (mAutoFocus == autoFocus) {
            return;
        }
        mAutoFocus = autoFocus;
        if (mPreviewRequestBuilder != null) {
            updateAutoFocus();
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                            mCaptureCallback, null);
                } catch (CameraAccessException e) {
                    mAutoFocus = !mAutoFocus; // Revert
                }
            }
        }
    }

    @Override
    boolean getAutoFocus() {
        return mAutoFocus;
    }

    @Override
    void setFlash(int flash) {
        if (mFlash == flash) {
            return;
        }
        int saved = mFlash;
        mFlash = flash;
        if (mPreviewRequestBuilder != null) {
            updateFlash();
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                            mCaptureCallback, null);
                } catch (CameraAccessException e) {
                    mFlash = saved; // Revert
                }
            }
        }
    }

    @Override
    int getFlash() {
        return mFlash;
    }

    @Override
    void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
//        mPreview.setDisplayOrientation(mDisplayOrientation);
    }

    /**
     * <p>Chooses a camera ID by the specified camera facing ({@link #mFacing}).</p>
     * <p>This rewrites {@link #mCameraId}, {@link #mCameraCharacteristics}, and optionally
     * {@link #mFacing}.</p>
     */
    private void chooseCameraIdByFacing() {
        try {
            int internalFacing = INTERNAL_FACINGS.get(mFacing);
            final String[] ids = mCameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    throw new NullPointerException("Unexpected state: LENS_FACING null");
                }
                if (internal == internalFacing) {
                    mCameraId = id;
                    mCameraCharacteristics = characteristics;
                    return;
                }
            }
            // Not found
            mCameraId = ids[0];
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            Integer internal = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (internal == null) {
                throw new NullPointerException("Unexpected state: LENS_FACING null");
            }
            for (int i = 0, count = INTERNAL_FACINGS.size(); i < count; i++) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    mFacing = INTERNAL_FACINGS.keyAt(i);
                    return;
                }
            }
            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            mFacing = Constants.FACING_BACK;
        } catch (CameraAccessException e) {
            throw new RuntimeException("Failed to get a list of camera devices", e);
        }
    }

    /**
     * <p>Collects some information from {@link #mCameraCharacteristics}.</p>
     * <p>This rewrites {@link #mPreviewSizes}, {@link #mPictureSizes}, and optionally,
     * {@link #mAspectRatio}.</p>
     */
    private void collectCameraInfo() {
        StreamConfigurationMap map = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            throw new IllegalStateException("Failed to get configuration map: " + mCameraId);
        }
        mPreviewSizes.clear();
        for (android.util.Size size : map.getOutputSizes(SurfaceTexture.class)) {
            mPreviewSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
        mPictureSizes.clear();
        collectPictureSizes(mPictureSizes, map);

        if (!mPreviewSizes.ratios().contains(mAspectRatio)) {
            mAspectRatio = mPreviewSizes.ratios().iterator().next();
        }
    }

    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            mPictureSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
    }

    private void prepareImageReader() {
//        Size largest = mPictureSizes.sizes(mAspectRatio).last();
        Size largest = chooseOptimalSize();
        mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                ImageFormat.YUV_420_888, /* maxImages */ 2);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
    }

    /**
     * <p>Starts opening a camera device.</p>
     * <p>The result will be processed in {@link #mCameraDeviceCallback}.</p>
     */
    private void startOpeningCamera() {
        try {
            mCameraManager.openCamera(mCameraId, mCameraDeviceCallback, null);
        } catch (CameraAccessException e) {
            throw new RuntimeException("Failed to open camera: " + mCameraId, e);
        }
    }

    /**
     * <p>Starts a capture session for camera preview.</p>
     * <p>This rewrites {@link #mPreviewRequestBuilder}.</p>
     * <p>The result will be continuously processed in {@link #mSessionCallback}.</p>
     */
    @Override
    void startPreview(int width, int height) {
        if (!isCameraOpened()) {
            return;
        }
        Size previewSize = chooseOptimalSize();
        mPreviewSurface.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

//        mPreview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
        try {
            mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            List<Surface> surfaces = new ArrayList<>();
            Surface surface = new Surface(mPreviewSurface);
            surfaces.add(surface);
            mPreviewRequestBuilder.addTarget(surface);
            mCamera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mPreviewSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException("Failed to start camera session");
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called an advance.
     */
    private void updatePreview() {
        if (!isCameraOpened()) {
            return;
        }
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

    };

    /**
     * Chooses the optimal preview size based on {@link #mPreviewSizes} and the surface size.
     *
     * @return The picked size for camera preview.
     */
    private Size chooseOptimalSize() {
        int surfaceLonger, surfaceShorter;
        final int surfaceWidth = mWidth;
        final int surfaceHeight = mHeight;
        if (surfaceWidth < surfaceHeight) {
            surfaceLonger = surfaceHeight;
            surfaceShorter = surfaceWidth;
        } else {
            surfaceLonger = surfaceWidth;
            surfaceShorter = surfaceHeight;
        }
        SortedSet<Size> candidates = mPreviewSizes.sizes(mAspectRatio);
        // Pick the smallest of those big enough.
        for (Size size : candidates) {
            if (size.getWidth() >= surfaceLonger && size.getHeight() >= surfaceShorter) {
                return size;
            }
        }
        // If no size is big enough, pick the largest one.
        return candidates.last();
    }

    /**
     * Updates the internal state of auto-focus to {@link #mAutoFocus}.
     */
    void updateAutoFocus() {
        if (mAutoFocus) {
            int[] modes = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            // Auto focus is not supported
            if (modes == null || modes.length == 0 ||
                    (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                mAutoFocus = false;
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF);
            } else {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }
        } else {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF);
        }
    }

    /**
     * Updates the internal state of flash to {@link #mFlash}.
     */
    void updateFlash() {
        switch (mFlash) {
            case Constants.FLASH_OFF:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_ON:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_TORCH:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH);
                break;
            case Constants.FLASH_AUTO:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_RED_EYE:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} for capturing a still picture.
     */
    private static abstract class PictureCaptureCallback
            extends CameraCaptureSession.CaptureCallback {

        static final int STATE_PREVIEW = 0;
        static final int STATE_LOCKING = 1;
        static final int STATE_LOCKED = 2;
        static final int STATE_PRECAPTURE = 3;
        static final int STATE_WAITING = 4;
        static final int STATE_CAPTURING = 5;

        private int mState;

        PictureCaptureCallback() {
        }

        void setState(int state) {
            mState = state;
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

        private void process(@NonNull CaptureResult result) {
            switch (mState) {
                case STATE_LOCKING: {
                    Integer af = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (af == null) {
                        break;
                    }
                    if (af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            setState(STATE_CAPTURING);
                            onReady();
                        } else {
                            setState(STATE_LOCKED);
                            onPrecaptureRequired();
                        }
                    }
                    break;
                }
                case STATE_PRECAPTURE: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        setState(STATE_WAITING);
                    }
                    break;
                }
                case STATE_WAITING: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        setState(STATE_CAPTURING);
                        onReady();
                    }
                    break;
                }
            }
        }

        /**
         * Called when it is ready to take a still picture.
         */
        public abstract void onReady();

        /**
         * Called when it is necessary to run the precapture sequence.
         */
        public abstract void onPrecaptureRequired();

    }

}
