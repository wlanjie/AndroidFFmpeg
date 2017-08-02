package com.wlanjie.streaming.util;

/**
 * Created by wlanjie on 2017/8/1.
 */
public class StreamingLog {
  private static final String TAG = "Streaming";
  private static boolean open = true;

  public static void disableLog() {
    open = false;
  }

  public static boolean isOpen() {
    return open;
  }

  public static void d(String msg) {
    if (open) {
      android.util.Log.d(TAG, msg);
    }
  }

  public static void w(String msg) {
    if (open) {
      android.util.Log.w(TAG, msg);
    }
  }

  public static void e(String msg) {
    if (open) {
      android.util.Log.e(TAG, msg);
    }
  }

  public static void e(String msg, Throwable throwable) {
    if (open) {
      android.util.Log.e(TAG, msg, throwable);
    }
  }
}
