package com.wlanjie.streaming.camera;

/**
 * Created by caowu15 on 2017/6/27.
 */

public interface LivingCamera {
  void start();
  void stop();
  void setFacing(int facing);
  int getFacing();
  boolean isCameraOpened();
}
