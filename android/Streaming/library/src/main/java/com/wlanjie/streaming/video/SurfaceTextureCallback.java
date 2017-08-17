package com.wlanjie.streaming.video;

import java.nio.IntBuffer;

/**
 * Created by wlanjie on 2017/8/8.
 */
public interface SurfaceTextureCallback {
  void onSurfaceCreated();
  void onSurfaceChanged(int width, int height);
  void onSurfaceDestroyed();
  int onDrawFrame(int textureId, int textureWidth, int textureHeight, float[] transformMatrix);
}
