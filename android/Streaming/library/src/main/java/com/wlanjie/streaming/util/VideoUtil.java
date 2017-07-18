package com.wlanjie.streaming.util;

/**
 * Created by wlanjie on 2017/7/18.
 */
public class VideoUtil {

  public static int getVideoSize(int size) {
    int multiple = (int) Math.ceil(size / 16.0);
    return multiple * 16;
  }

}
