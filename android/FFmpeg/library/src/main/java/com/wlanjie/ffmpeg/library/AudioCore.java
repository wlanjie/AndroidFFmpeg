package com.wlanjie.ffmpeg.library;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by wlanjie on 16/9/10.
 */

public class AudioCore {

    private final Parameters parameters;
    private final Object lock = new Object();
    private MediaFormat audioFormat;
    private MediaCodec audioEncoder;
    private AudioBuffer[] originAudioBuffers;
    private AudioBuffer originAudioBuffer;
    private AudioBuffer filteredAudioBuffer;
    private int lastAudioQueueBufferIndex;
    private AudioSenderThread audioSenderThread;
    private AudioFilter audioFilter;
    private Lock lockAudioFilter = null;
    private AudioFilterHandler audioFilterHandler;
    private HandlerThread audioFilterHandlerThread;

    public AudioCore(Parameters parameters) {
        this.parameters = parameters;
        lockAudioFilter = new ReentrantLock(false);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean prepare(Config config) {
        synchronized (lock) {
            parameters.mediacodecAACProfile = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
            parameters.mediacodecAACSampleRate = 44100;
            parameters.mediacodecAACChannelCount = 1;
            parameters.mediacdoecAVCBitRate = 32 * 1024;
            parameters.mediacodecAACMaxInputSize = 8820;

            try {
                audioFormat = new MediaFormat();
                audioFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
                audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, parameters.mediacodecAACProfile);
                audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, parameters.mediacodecAACSampleRate);
                audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, parameters.mediacodecAACChannelCount);
                audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, parameters.mediacdoecAVCBitRate);
                audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, parameters.mediacodecAACMaxInputSize);
                audioEncoder = MediaCodec.createEncoderByType(audioFormat.getString(MediaFormat.KEY_MIME));

                originAudioBuffers = new AudioBuffer[parameters.audioBufferQueueNum];
                for (int i = 0; i < parameters.audioBufferQueueNum; i++) {
                    originAudioBuffers[i] = new AudioBuffer(AudioFormat.ENCODING_PCM_16BIT, parameters.mediacodecAACSampleRate / 5);
                }
                originAudioBuffer = new AudioBuffer(AudioFormat.ENCODING_PCM_16BIT, parameters.mediacodecAACSampleRate / 5);
                filteredAudioBuffer = new AudioBuffer(AudioFormat.ENCODING_PCM_16BIT, parameters.mediacodecAACSampleRate / 5);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void start(FLVDataCollecter dataCollecter) {
        synchronized (lock) {
            try {
                for (AudioBuffer audioBuffer : originAudioBuffers) {
                    audioBuffer.isReadyToFill = true;
                }
                if (audioEncoder == null) {
                    audioEncoder = MediaCodec.createEncoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
                }
                audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                audioEncoder.start();
                lastAudioQueueBufferIndex = 0;

                audioSenderThread = new AudioSenderThread("AudioSenderThread", audioEncoder, dataCollecter);
                audioSenderThread.start();

                audioFilterHandlerThread = new HandlerThread("audioFilterHandlerThread");
                audioFilterHandlerThread.start();

                audioFilterHandler = new AudioFilterHandler(audioFilterHandlerThread.getLooper());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stop() {
        synchronized (lock) {
            audioFilterHandler.removeCallbacksAndMessages(null);
            audioFilterHandlerThread.quit();
            try {
                audioFilterHandlerThread.join();
                audioSenderThread.quit();
                audioSenderThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            audioEncoder.stop();
            audioEncoder.release();
            audioEncoder = null;
        }
    }

    public void queueAudio(byte[] rawAudioFrame) {
        int targetIndex = (lastAudioQueueBufferIndex + 1) % originAudioBuffers.length;
        if (originAudioBuffers[targetIndex].isReadyToFill) {
            System.arraycopy(rawAudioFrame, 0, originAudioBuffers[targetIndex].buffer, 0, parameters.audioRecoderBufferSize);
            originAudioBuffers[targetIndex].isReadyToFill = false;
            lastAudioQueueBufferIndex = targetIndex;
            audioFilterHandler.sendMessage(audioFilterHandler.obtainMessage(AudioFilterHandler.WHAT_INCOMING_BUFF, targetIndex, 0));
        }
    }

    public AudioFilter acquireAudioFilter() {
        lockAudioFilter.lock();
        return audioFilter;
    }

    public void setAudioFilter(AudioFilter audioFilter) {
        lockAudioFilter.lock();
        if (audioFilter != null) {
            audioFilter.onDestroy();
        }
        this.audioFilter = audioFilter;
        if (audioFilter != null) {
            audioFilter.onInit(parameters.mediacodecAACSampleRate / 5);
        }
        lockAudioFilter.unlock();
    }

    public void destroy() {
        synchronized (lock) {
            lockAudioFilter.lock();
            if (audioFilter != null) {
                audioFilter.onDestroy();
            }
            lockAudioFilter.unlock();
        }
    }

    public void releaseAudioFilter() {
        lockAudioFilter.unlock();
    }

    private class AudioBuffer {
        public boolean isReadyToFill;
        public int audioFormat = -1;
        public byte[] buffer;

        AudioBuffer(int audioFormat, int size) {
            isReadyToFill = true;
            this.audioFormat = audioFormat;
            buffer = new byte[size];
        }
    }

    private class AudioFilterHandler extends Handler {

        public static final int FILTER_LOCK_TOLERATION = 3;//3ms
        public static final int WHAT_INCOMING_BUFF = 1;
        private int sequenceNum;

        AudioFilterHandler(Looper looper) {
            super(looper);
            sequenceNum = 0;
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what != WHAT_INCOMING_BUFF) {
                return;
            }
            sequenceNum++;
            int targetIndex = msg.arg1;
            long nowTimeMs = SystemClock.uptimeMillis();
            System.arraycopy(originAudioBuffers[targetIndex].buffer, 0,
                    originAudioBuffer.buffer, 0, originAudioBuffer.buffer.length);
            originAudioBuffers[targetIndex].isReadyToFill = true;
            boolean isFilterLocked = lockAudioFilter();
            boolean filtered = false;
            if (isFilterLocked) {
                filtered = audioFilter.onFrame(originAudioBuffer.buffer, filteredAudioBuffer.buffer, nowTimeMs, sequenceNum);
                unlockAudioFilter();
            } else {
                System.arraycopy(originAudioBuffers[targetIndex].buffer, 0,
                        originAudioBuffer.buffer, 0, originAudioBuffer.buffer.length);
                originAudioBuffers[targetIndex].isReadyToFill = true;
            }
            int eibIndex = audioEncoder.dequeueInputBuffer(-1);
            if (eibIndex >= 0) {
                ByteBuffer audioEncoderInputBuffer = audioEncoder.getInputBuffers()[eibIndex];
                audioEncoderInputBuffer.position(0);
                audioEncoderInputBuffer.put(filtered ? filteredAudioBuffer.buffer : originAudioBuffer.buffer, 0, originAudioBuffer.buffer.length);
                audioEncoder.queueInputBuffer(eibIndex, 0, originAudioBuffer.buffer.length, nowTimeMs * 1000, 0);
            }
        }

        private boolean lockAudioFilter() {
            try {
                boolean locked = lockAudioFilter.tryLock(FILTER_LOCK_TOLERATION, TimeUnit.MICROSECONDS);
                if (locked) {
                    if (audioFilter != null) {
                        return true;
                    } else {
                        lockAudioFilter.unlock();
                        return false;
                    }
                } else {
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        private void unlockAudioFilter() {
            lockAudioFilter.unlock();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private class AudioSenderThread extends Thread {

        private static final long WAIT_TIME = 5000;
        private MediaCodec.BufferInfo bufferInfo;
        private long startTime = 0;
        private MediaCodec audioEncoder;
        private FLVDataCollecter dataCollecter;
        private boolean shouldQuit = false;

        AudioSenderThread(String name, MediaCodec encoder, FLVDataCollecter dataCollecter) {
            super(name);
            bufferInfo = new MediaCodec.BufferInfo();
            startTime = 0;
            this.audioEncoder = encoder;
            this.dataCollecter = dataCollecter;
        }

        void quit() {
            shouldQuit = true;
            interrupt();
        }

        @Override
        public void run() {
            super.run();
            while (!shouldQuit) {
                int eobIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, WAIT_TIME);
                switch (eobIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        ByteBuffer csd0 = audioEncoder.getOutputFormat().getByteBuffer("csd-0");
                        sendAudioSpecificConfig(0, csd0);
                        break;
                    default:
                        if (startTime == 0) {
                            startTime = bufferInfo.presentationTimeUs / 1000;
                        }
                        /**
                         * we send audio SpecificConfig already in INFO_OUTPUT_FORMAT_CHANGED
                         * so we ignore MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                         */
                        if (bufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && bufferInfo.size != 0) {
                            ByteBuffer realData = audioEncoder.getOutputBuffers()[eobIndex];
                            realData.position(bufferInfo.offset);
                            realData.limit(bufferInfo.offset + bufferInfo.size);
                            sendRealData(bufferInfo.presentationTimeUs / 1000, realData);
                        }
                        audioEncoder.releaseOutputBuffer(eobIndex, false);
                        break;
                }
            }
            bufferInfo = null;
        }

        private void sendRealData(long tms, ByteBuffer realData) {
            int packetLen = Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH + realData.remaining();
            byte[] finalBuffer = new byte[packetLen];
            realData.get(finalBuffer, Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH, realData.remaining());
            Packager.FLVPackager.fillFLVAudioTag(finalBuffer, 0, false);
            FLVData flvData = new FLVData();
            flvData.byteBuffer = finalBuffer;
            flvData.size = finalBuffer.length;
            flvData.dts = (int) tms;
            flvData.flvTagType = FLVData.FLV_RTMP_PACKET_TYPE_AUDIO;
            dataCollecter.collect(flvData, RtmpSender.FROM_AUDIO);
        }

        private void sendAudioSpecificConfig(long tms, ByteBuffer realData) {
            int packetLen = Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH + realData.remaining();
            byte[] finalBuffer = new byte[packetLen];
            realData.get(finalBuffer, Packager.FLVPackager.FLV_AUDIO_TAG_LENGTH, realData.remaining());
            Packager.FLVPackager.fillFLVAudioTag(finalBuffer, 0, true);
            FLVData flvData = new FLVData();
            flvData.byteBuffer = finalBuffer;
            flvData.size = finalBuffer.length;
            flvData.dts = (int) tms;
            flvData.flvTagType = FLVData.FLV_RTMP_PACKET_TYPE_AUDIO;
            dataCollecter.collect(flvData, RtmpSender.FROM_AUDIO);
        }
    }
}
