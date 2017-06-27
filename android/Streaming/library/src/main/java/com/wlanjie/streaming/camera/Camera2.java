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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import com.wlanjie.streaming.setting.CameraSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

@TargetApi(21)
public class Camera2 implements LivingCamera {

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

  private CameraCallback mCallback;

  private CameraSetting mCameraSetting;

  private String mCameraId;

  private CameraCharacteristics mCameraCharacteristics;

  private CameraDevice mCamera;

  private CameraCaptureSession mCaptureSession;

  private CameraCaptureSession mPreviewSession;

  private CaptureRequest.Builder mPreviewRequestBuilder;

  private Handler mBackgroundHandler;

  private HandlerThread mBackgroundThread;

  private ImageReader mImageReader;

  private final SizeMap mPreviewSizes = new SizeMap();

  private final SizeMap mPictureSizes = new SizeMap();

  private int mFacing;

  private AspectRatio mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;

  private boolean mAutoFocus;

  private int mFlash;

  public Camera2(CameraCallback callback, CameraSetting cameraSetting, Context context) {
    mCallback = callback;
    mCameraSetting = cameraSetting;
    mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  private void startBackgroundThread() {
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  @Override
  public void start() {
    startBackgroundThread();
    chooseCameraIdByFacing();
    collectCameraInfo();
    startOpeningCamera();
  }

  @Override
  public void stop() {
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
  public boolean isCameraOpened() {
    return mCamera != null;
  }

  @Override
  public void setFacing(int facing) {
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
  public int getFacing() {
    return mFacing;
  }

  Set<AspectRatio> getSupportedAspectRatios() {
    return mPreviewSizes.ratios();
  }

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
    }
  }

  AspectRatio getAspectRatio() {
    return mAspectRatio;
  }

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

  boolean getAutoFocus() {
    return mAutoFocus;
  }

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

  int getFlash() {
    return mFlash;
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
   */
  public void startPreview(int width, int height) {
    if (!isCameraOpened()) {
      return;
    }
    Size previewSize = chooseOptimalSize();
    mCameraSetting.getSurfaceTexture().setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

//        mPreview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
    try {
      mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

      List<Surface> surfaces = new ArrayList<>();
      Surface surface = new Surface(mCameraSetting.getSurfaceTexture());
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
      }, null);
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
      mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
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
    final int surfaceWidth = mCameraSetting.getPreviewWidth();
    final int surfaceHeight = mCameraSetting.getPreviewHeight();
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
    private void updateAutoFocus() {
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
}
