package com.wlanjie.streaming.video;

/**
 * Created by wlanjie on 2017/8/8.
 */
public interface SurfaceTextureCallback {
  void onSurfaceCreated();
  void onSurfaceChanged(int width, int height);
  void onSurfaceDestroyed();
  int onDrawFrame(int textureId, int textureWidth, int textureHeight, float[] transformMatrix);
}
