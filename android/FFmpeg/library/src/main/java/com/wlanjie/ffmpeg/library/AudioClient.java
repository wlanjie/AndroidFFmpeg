package com.wlanjie.ffmpeg.library;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Created by wlanjie on 16/9/10.
 */

public class AudioClient {

    private final Parameters parameters;
    private final Object lock = new Object();
    private AudioCore audioCore;
    private AudioRecord audioRecord;
    private byte[] audioBuffer;
    private AudioRecordThread audioRecordThread;

    public AudioClient(Parameters parameters) {
        this.parameters = parameters;
    }

    public boolean prepare(Config config) {
        synchronized (lock) {
            parameters.audioBufferQueueNum = 5;
            audioCore = new AudioCore(parameters);
            if (!audioCore.prepare(config)) {
                return false;
            }
            parameters.audioRecoderFormat = AudioFormat.ENCODING_PCM_16BIT;
            parameters.audioRecoderChannelConfig = AudioFormat.CHANNEL_IN_MONO;
            parameters.audioRecoderSliceSize = parameters.mediacodecAACSampleRate / 10;
            parameters.audioRecoderBufferSize = parameters.audioRecoderSliceSize * 2;
            parameters.audioRecoderSource = MediaRecorder.AudioSource.DEFAULT;
            parameters.audioRecoderSampleRate = parameters.mediacodecAACSampleRate;
            prepareAudio();
            return true;
        }
    }

    private void prepareAudio() {
        int minBufferSize = AudioRecord.getMinBufferSize(parameters.audioRecoderSampleRate,
                parameters.audioRecoderChannelConfig,
                parameters.audioRecoderFormat);
        audioRecord = new AudioRecord(parameters.audioRecoderSource,
                parameters.audioRecoderSampleRate,
                parameters.audioRecoderChannelConfig,
                parameters.audioRecoderFormat,
                minBufferSize * 5);
        audioBuffer = new byte[parameters.audioRecoderBufferSize];
    }

    public void start(FLVDataCollecter dataCollecter) {
        synchronized (lock) {
            audioCore.start(dataCollecter);
            audioRecord.startRecording();
            audioRecordThread = new AudioRecordThread();
            audioRecordThread.start();
        }
    }

    public void stop() {
        synchronized (lock) {
            audioRecordThread.quit();
            try {
                audioRecordThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            audioCore.stop();
            audioRecordThread = null;
            audioRecord.stop();
        }
    }

    public void destory() {
        synchronized (lock) {
            audioRecord.release();
        }
    }

    public void setAudioFilter(AudioFilter audioFilter) {
        audioCore.setAudioFilter(audioFilter);
    }

    public AudioFilter acquireAudioFilter() {
        return audioCore.acquireAudioFilter();
    }

    public void releaseAudioFilter() {
        audioCore.releaseAudioFilter();
    }

    private class AudioRecordThread extends Thread {

        private boolean isRunning = true;

        AudioRecordThread() {
            isRunning = true;
        }

        void quit() {
            isRunning = false;
        }

        @Override
        public void run() {
            super.run();
            while (isRunning) {
                int size = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (isRunning && audioCore != null && size > 0) {
                    audioCore.queueAudio(audioBuffer);
                }
            }
        }
    }
}
