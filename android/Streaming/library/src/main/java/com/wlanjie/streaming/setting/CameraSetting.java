package com.wlanjie.streaming.setting;

import android.graphics.SurfaceTexture;

/**
 * Created by wlanjie on 2017/6/14.
 */
public class CameraSetting {

  private CameraFacingId facing;
  private SurfaceTexture surfaceTexture;
  private int displayOrientation = 0;

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

  public static enum CameraFacingId {
    CAMERA_FACING_BACK,
    CAMERA_FACING_FRONT
  }
}
