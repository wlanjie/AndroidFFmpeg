package com.wlanjie.ffmpeg.library;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by wlanjie on 16/9/8.
 */

public class WriteFile {

    private BufferedOutputStream outputStream;
    private MediaCodec mediaCodec;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public WriteFile() {
        try {
            this.mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
            MediaFormat mediaFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 1);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 32 * 1024);
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 44100 / 10 * 2);
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.inputBuffers = mediaCodec.getInputBuffers();
        this.outputBuffers = mediaCodec.getOutputBuffers();
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "audio_encoded.aac")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public synchronized void offerEncoder(byte[] input) {
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(input);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        while (outputBufferIndex >= 0) {
            int outBitSize = bufferInfo.size;
            int outPacketSize = outBitSize + 7;
            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + outBitSize);

            byte[] outData = new byte[outPacketSize];
            addADTStoPacket(outData, outPacketSize);
            outputBuffer.get(outData, 7, outBitSize);
            outputBuffer.position(bufferInfo.offset);

            try {
                outputStream.write(outData, 0, outData.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
// 39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = 4; // 44.1KHz
        int chanCfg = 2; // CPE


// fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}
