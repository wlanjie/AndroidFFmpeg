package com.wlanjie.ffmpeg.library;

import android.os.SystemClock;

import java.util.LinkedList;

/**
 * Created by wlanjie on 16/9/11.
 */
public class ByteSpeedometer {

    private int timeGranularity;
    private LinkedList<ByteFrame> byteList;
    private final Object lock = new Object();

    public ByteSpeedometer(int timeGranularity) {
        this.timeGranularity = timeGranularity;
        byteList = new LinkedList<>();
    }

    public int getSpeed() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            trim(now);
            long sumByte = 0;
            for (ByteFrame byteFrame : byteList) {
                sumByte += byteFrame.byteNum;
            }
            return (int) (sumByte * 1000 / timeGranularity);
        }
    }

    public void gain(int byteCount) {
        synchronized (lock) {
            long now = SystemClock.currentThreadTimeMillis();
            byteList.addLast(new ByteFrame(now, byteCount));
            trim(now);
        }
    }

    public void trim(long time) {
        while (!byteList.isEmpty() && (time - byteList.getFirst().time) > timeGranularity) {
            byteList.removeFirst();
        }
    }

    public void reset() {
        synchronized (lock) {
            byteList.clear();
        }
    }

    private class ByteFrame {
        long time;
        long byteNum;
        public ByteFrame(long time, long byteNum) {
            this.time = time;
            this.byteNum = byteNum;
        }
    }
}
