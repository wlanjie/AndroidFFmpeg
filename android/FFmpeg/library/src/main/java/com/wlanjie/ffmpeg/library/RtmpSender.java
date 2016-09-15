package com.wlanjie.ffmpeg.library;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

/**
 * Created by wlanjie on 16/9/10.
 */

public class RtmpSender {

    private final Object lock = new Object();
    private static final int TIMEGRANULARITY = 3000;
    public static final int FROM_AUDIO = 8;
    public static final int FROM_VIDEO = 6;
    private WorkHandler workHandler;
    private HandlerThread workHandlerThread;

    public void prepare(Parameters parameters) {
        synchronized (lock) {
            workHandlerThread = new HandlerThread("RtmpSend,workHandlerThread");
            workHandlerThread.start();
            workHandler = new WorkHandler(parameters.senderQueueLength, new FLVMetaData(parameters), workHandlerThread.getLooper());
        }
    }

    public void start(String rtmpAddress) {
        synchronized (lock) {
            workHandler.sendStart(rtmpAddress);
        }
    }

    public void feed(FLVData flvData, int type) {
        synchronized (lock) {
            workHandler.sendFood(flvData, type);
        }
    }

    public void stop() {
        synchronized (lock) {
            workHandler.sendStop();
        }
    }

    public void destory() {
        synchronized (lock) {
            workHandler.removeCallbacksAndMessages(null);
            workHandlerThread.quit();
        }
    }

    public int getTotalSpeed() {
        synchronized (lock) {
            if (workHandler != null) {
                return workHandler.getTotalSpeed();
            } else {
                return 0;
            }
        }
    }

    public void setConnectionListener(ConnectionListener connectionListener) {
        synchronized (lock) {
            workHandler.setConnectionListener(connectionListener);
        }
    }

    public String getServerIpAddress() {
        synchronized (lock) {
            return workHandler == null ? null : workHandler.getServerIpAddress();
        }
    }

    public float getSendFrameRate() {
        synchronized (lock) {
            return workHandler == null ? 0 : workHandler.getSendFrameRate();
        }
    }

    public float getSendBufferFreePercent() {
        synchronized (lock) {
            return workHandler == null ? 0 : workHandler.getSendBufferFreePercent();
        }
    }

    private static class WorkHandler extends Handler {

        private final static int MSG_START = 1;
        private final static int MSG_WRITE = 2;
        private final static int MSG_STOP = 3;
        private final Object connectionLock = new Object();
        private final Object writeMsgNumLock = new Object();
        private ConnectionListener connectionListener;
        private ByteSpeedometer videoByteSpeedometer = new ByteSpeedometer(TIMEGRANULARITY);
        private ByteSpeedometer audioByteSpeedometer = new ByteSpeedometer(TIMEGRANULARITY);

        private long jniRtmpPointer = 0;
        private final int maxQueueLength;
        private final FLVMetaData flvMetaData;
        private String serverIpAddress;
        private STATE state;
        private int errorTime = 0;
        private int writeMsgNum = 0;

        private FrameRateMeter sendFrameRateMeter = new FrameRateMeter();

        private enum STATE {
            IDLE,
            RUNNING,
            STOPPED
        }

        WorkHandler(int maxQueueLength, FLVMetaData flvMetaData, Looper looper) {
            super(looper);
            this.maxQueueLength = maxQueueLength;
            this.flvMetaData = flvMetaData;
            state = STATE.IDLE;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_START:
                    if (state == STATE.RUNNING) {
                        break;
                    }
                    sendFrameRateMeter.reSet();
                    jniRtmpPointer = RtmpClient.open((String) msg.obj, true);
                    final int openResult = jniRtmpPointer == 0 ? 1 : 0;
                    if (openResult == 0) {
                        serverIpAddress = RtmpClient.getIpAddress(jniRtmpPointer);
                    }

                    synchronized (connectionLock) {
                        if (connectionListener != null) {
                            CallbackDelivery.i().post(new Runnable() {
                                @Override
                                public void run() {
                                    connectionListener.onOpenConnectionResult(openResult);
                                }
                            });
                        }
                    }
                    if (jniRtmpPointer == 0) {
                        break;
                    } else {
                        byte[] metaData = flvMetaData.getMetaData();
                        RtmpClient.write(jniRtmpPointer, metaData, metaData.length, FLVData.FLV_RTMP_PACKET_TYPE_INFO, 0);
                        state = STATE.RUNNING;
                    }
                    break;
                case MSG_WRITE:
                    synchronized (writeMsgNumLock) {
                        --writeMsgNum;
                    }
                    if (state != STATE.RUNNING) {
                        break;
                    }
                    FLVData flvData = (FLVData) msg.obj;
                    if (writeMsgNum >= (maxQueueLength * 2 / 3) && flvData.flvTagType == FLVData.FLV_RTMP_PACKET_TYPE_VIDEO && !flvData.isKeyframe()) {
                        break;
                    }
                    final int result = RtmpClient.write(jniRtmpPointer, flvData.byteBuffer, flvData.byteBuffer.length, flvData.flvTagType, flvData.dts);
                    if (result == 0) {
                        errorTime = 0;
                        if (flvData.flvTagType == FLVData.FLV_RTMP_PACKET_TYPE_VIDEO) {
                            videoByteSpeedometer.gain(flvData.size);
                            sendFrameRateMeter.count();
                        } else {
                            audioByteSpeedometer.gain(flvData.size);
                        }
                    } else {
                        ++errorTime;
                        synchronized (connectionLock) {
                            if (connectionListener != null) {
                                CallbackDelivery.i().post(new ConnectionListener.WriteErrorRunnable(connectionListener, result));
                            }
                        }
                    }
                    break;
                case MSG_STOP:
                    if (state == STATE.STOPPED || jniRtmpPointer == 0) {
                        break;
                    }
                    errorTime = 0;
                    final int closeResult = RtmpClient.close(jniRtmpPointer);
                    serverIpAddress = null;
                    synchronized (connectionLock) {
                        if (connectionListener != null) {
                            CallbackDelivery.i().post(new Runnable() {
                                @Override
                                public void run() {
                                    connectionListener.onCloseConnectionResult(closeResult);
                                }
                            });
                        }
                    }
                    state = STATE.STOPPED;
                    break;
            }
        }

        public float getSendBufferFreePercent() {
            synchronized (writeMsgNumLock) {
                float res = (float) (maxQueueLength - writeMsgNum) / (float) maxQueueLength;
                return res <= 0 ? 0f : res;
            }
        }

        public float getSendFrameRate() {
            return sendFrameRateMeter.getFps();
        }

        public void sendStart(String rtmpAddress) {
            removeMessages(MSG_START);
            synchronized (writeMsgNumLock) {
                removeMessages(MSG_WRITE);
                writeMsgNum = 0;
            }
            sendMessage(obtainMessage(MSG_START, rtmpAddress));
        }

        public void sendStop() {
            removeMessages(MSG_STOP);
            synchronized (writeMsgNumLock) {
                removeMessages(MSG_WRITE);
                writeMsgNum = 0;
            }
            sendEmptyMessage(MSG_STOP);
        }

        public void sendFood(FLVData flvData, int type) {
            synchronized (writeMsgNumLock) {
                if (writeMsgNum <= maxQueueLength) {
                    sendMessage(obtainMessage(MSG_WRITE, type, 0, flvData));
                    ++writeMsgNum;
                }
            }
        }

        public int getVideoSpeed() {
            return videoByteSpeedometer.getSpeed();
        }

        public int getAudioSpeed() {
            return audioByteSpeedometer.getSpeed();
        }

        public int getTotalSpeed() {
            return getVideoSpeed() + getAudioSpeed();
        }

        public String getServerIpAddress() {
            return serverIpAddress;
        }

        public void setConnectionListener(ConnectionListener connectionListener) {
            synchronized (connectionLock) {
                this.connectionListener = connectionListener;
            }
        }
    }
}
