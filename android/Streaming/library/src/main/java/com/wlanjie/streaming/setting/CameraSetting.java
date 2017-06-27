package com.wlanjie.streaming.setting;

import android.graphics.SurfaceTexture;

/**
 * Created by wlanjie on 2017/6/14.
 */
public class CameraSetting {

  private CameraFacingId facing;
  private SurfaceTexture surfaceTexture;
  private int displayOrientation = 0;
  private int previewWidth;
  private int previewHeight;

  public CameraFacingId getFacing() {
    return facing;
  }

  public CameraSetting setFacing(CameraFacingId facing) {
    this.facing = facing;
    return this;
  }

  public SurfaceTexture getSurfaceTexture() {
    return surfaceTexture;
  }

  public CameraSetting setSurfaceTexture(SurfaceTexture surfaceTexture) {
    this.surfaceTexture = surfaceTexture;
    return this;
  }

  public int getDisplayOrientation() {
    return displayOrientation;
  }

  public CameraSetting setDisplayOrientation(int displayOrientation) {
    this.displayOrientation = displayOrientation;
    return this;
  }

  public int getPreviewWidth() {
    return previewWidth;
  }

  public void setPreviewWidth(int previewWidth) {
    this.previewWidth = previewWidth;
  }

  public int getPreviewHeight() {
    return previewHeight;
  }

  public void setPreviewHeight(int previewHeight) {
    this.previewHeight = previewHeight;
  }

  public static enum CameraFacingId {
    CAMERA_FACING_BACK,
    CAMERA_FACING_FRONT
  }
}
