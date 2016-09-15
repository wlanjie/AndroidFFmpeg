package com.wlanjie.ffmpeg.library;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by wlanjie on 16/9/7.
 */

public class Encoder {

    AudioRecord audioRecord;
    private EncoderParameters parameters = new EncoderParameters();
    private int lastAudioQueueBufferIndex;
    private AudioBuffer[] originAudioBuffers;
    private AudioBuffer originBuffer;
    private byte[] audioBuffer;
    private MediaFormat audioEncoderFormat;
    private MediaCodec audioEncoder;
    private AudioRecordThread audioThread;
    private WriteFile writeFile;
    private AudioSenderThread audioSenderThread;
    private BufferedOutputStream outputStream;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean prepare() {
        synchronized (this) {
            // min sdk 16
            parameters.audioBufferQueueNum = 5;
            parameters.mediacodecAACProfile = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
            parameters.mediacodecAACSampleRate = 44100;
            parameters.mediacodecAACChannelCount = 1;
            parameters.mediacodecAACBitRate = 32 * 1024;
            parameters.mediacodecAACMaxInputSize = 8820;
            audioEncoderFormat = new MediaFormat();
            audioEncoderFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
            audioEncoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, parameters.mediacodecAACProfile);
            audioEncoderFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, parameters.mediacodecAACSampleRate);
            audioEncoderFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, parameters.mediacodecAACChannelCount);
            audioEncoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, parameters.mediacodecAACBitRate);
            audioEncoderFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, parameters.mediacodecAACMaxInputSize);
            try {
                audioEncoder = MediaCodec.createEncoderByType(audioEncoderFormat.getString(MediaFormat.KEY_MIME));
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            originAudioBuffers = new AudioBuffer[parameters.audioBufferQueueNum];
            for (int i = 0; i < parameters.audioBufferQueueNum; i++) {
                originAudioBuffers[i] = new AudioBuffer(AudioFormat.ENCODING_PCM_16BIT, parameters.mediacodecAACSampleRate / 5);
            }
            originBuffer = new AudioBuffer(AudioFormat.ENCODING_PCM_16BIT, parameters.mediacodecAACSampleRate / 5);

            parameters.audioRecoderFormat = AudioFormat.ENCODING_PCM_16BIT;
            parameters.audioRecoderChannelConfig = AudioFormat.CHANNEL_IN_MONO;
            parameters.audioRecoderSliceSize = parameters.mediacodecAACSampleRate / 10;
            parameters.audioRecoderBufferSize = parameters.audioRecoderSliceSize * 2;
            parameters.audioRecoderSource = MediaRecorder.AudioSource.DEFAULT;
            parameters.audioRecoderSampleRate = parameters.mediacodecAACSampleRate;

            try {
                outputStream = new BufferedOutputStream(new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "audio_record.pcm")));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            prepareAudio();
            return true;
        }
    }

    private boolean prepareAudio() {
        int minBufferSize = AudioRecord.getMinBufferSize(parameters.audioRecoderSampleRate,
                parameters.audioRecoderChannelConfig, parameters.audioRecoderFormat);
        audioRecord = new AudioRecord(parameters.audioRecoderSource,
                parameters.audioRecoderSampleRate,
                parameters.audioRecoderChannelConfig,
                parameters.audioRecoderFormat,
                minBufferSize * 5);
        audioBuffer = new byte[parameters.audioRecoderBufferSize];
        if (AudioRecord.STATE_INITIALIZED != audioRecord.getState())
            return false;
        return AudioRecord.SUCCESS != audioRecord.setPositionNotificationPeriod(parameters.audioRecoderSliceSize);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean start() {
        synchronized (this) {
            for (AudioBuffer originAudioBuffer : originAudioBuffers) {
                originAudioBuffer.isReadyToFill = true;
            }
            if (audioEncoder == null) {
                try {
                    audioEncoder = MediaCodec.createEncoderByType(audioEncoderFormat.getString(MediaFormat.KEY_MIME));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            audioEncoder.configure(audioEncoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();
            lastAudioQueueBufferIndex = 0;
            //start send thread
            audioSenderThread = new AudioSenderThread("AudioSenderThread", audioEncoder);
            audioSenderThread.start();

            writeFile = new WriteFile();
            audioRecord.startRecording();
            audioThread = new AudioRecordThread();
            audioThread.start();
            return true;
        }
    }

    class AudioSenderThread extends Thread {

        private MediaCodec.BufferInfo info;
        private long startTime = 0;
        private MediaCodec encoder;

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        AudioSenderThread(String name, MediaCodec encoder) {
            super(name);
            info = new MediaCodec.BufferInfo();
            startTime = 0;
            this.encoder = encoder;
        }

        private boolean shouldQuit = false;

        void quit() {
            shouldQuit = true;
            this.interrupt();
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            super.run();
            while (!shouldQuit) {
                int eobIndex = encoder.dequeueOutputBuffer(info, 5000);
                switch (eobIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        ByteBuffer csd0 = encoder.getOutputFormat().getByteBuffer("csd-0");
                        break;
                    default:
                        if (startTime == 0) {
                            startTime = info.presentationTimeUs / 1000;
                        }
                        if (info.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && info.size != 0) {
                            ByteBuffer data = encoder.getOutputBuffers()[eobIndex];
                            data.position(info.offset);
                            data.limit(info.offset + info.size);

                        }
//                        encoder.releaseOutputBuffer(eobIndex, false);
                        break;
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean stop() {
        synchronized (this) {
            audioThread.quit();
            try {
                audioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //stop send thread

            audioEncoder.stop();
            audioEncoder.release();
            audioEncoder = null;

            audioThread = null;
            audioRecord.stop();

            writeFile.close();
            audioSenderThread.quit();
            try {
                audioSenderThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }
    }

    public boolean destory() {
        synchronized (this) {
            audioRecord.release();
            return true;
        }
    }

    private class AudioRecordThread extends Thread {

        private boolean isRunning = true;

        AudioRecordThread() {
            isRunning = true;
        }

        public void quit() {
            isRunning = false;
        }

        @Override
        public void run() {
            super.run();
            while (isRunning) {
                int size = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (isRunning && size > 0) {
//                    try {
//                        outputStream.write(audioBuffer, 0, audioBuffer.length);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    int targetIndex = (lastAudioQueueBufferIndex + 1) % originAudioBuffers.length;
//                    if (originAudioBuffers[targetIndex].isReadyToFill) {
//                        System.arraycopy(audioBuffer, 0, originAudioBuffers[targetIndex].buffer, 0, parameters.audioRecoderBufferSize);
//                        originAudioBuffers[targetIndex].isReadyToFill = false;
//                        lastAudioQueueBufferIndex = targetIndex;
//                    }
                }
                writeFile.offerEncoder(audioBuffer);
            }
        }
    }

    private class AudioBuffer {
        boolean isReadyToFill;
        int audioFormat = -1;
        byte[] buffer;

        AudioBuffer(int audioFormat, int size) {
            this.audioFormat = audioFormat;
            buffer = new byte[size];
        }
    }
}
