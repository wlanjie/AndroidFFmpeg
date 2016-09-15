package com.walnjie.ffmpeg;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by wlanjie on 16/9/9.
 */


public class AudioEncoder {


    private MediaCodec mediaCodec;
    private BufferedOutputStream outputStream;
    private String mediaType = "OMX.google.aac.encoder";


    ByteBuffer[] inputBuffers = null;
    ByteBuffer[] outputBuffers = null;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public AudioEncoder() {
        File f = new File(Environment.getExternalStorageDirectory(),
                "audio_audio_encoded.aac");
        touch(f);
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(f));
            Log.e("AudioEncoder", "outputStream initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }


// mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
        try {
            mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
        } catch (IOException e) {
            e.printStackTrace();
        }
        final int kSampleRates[] = {8000, 11025, 22050, 44100, 48000};
        final int kBitRates[] = {64000, 96000, 128000};
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(
                "audio/mp4a-latm", 44100, 1);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 32 * 1024);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);// It will
// increase
// capacity
// of
// inputBuffers
        mediaCodec.configure(mediaFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();


        inputBuffers = mediaCodec.getInputBuffers();
        outputBuffers = mediaCodec.getOutputBuffers();
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // called AudioRecord's read
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public synchronized void offerEncoder(byte[] input) {
        Log.e("AudioEncoder", input.length + " is coming");


        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();


            inputBuffer.put(input);


            mediaCodec
                    .queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
        }


        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);


// //trying to add a ADTS
        while (outputBufferIndex >= 0) {
            int outBitsSize = bufferInfo.size;
            int outPacketSize = outBitsSize + 7; // 7 is ADTS size
            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];


            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + outBitsSize);


            byte[] outData = new byte[outPacketSize];
            addADTStoPacket(outData, outPacketSize);


            outputBuffer.get(outData, 7, outBitsSize);
            outputBuffer.position(bufferInfo.offset);


// byte[] outData = new byte[bufferInfo.size];
            try {
                outputStream.write(outData, 0, outData.length);
            } catch (IOException e) {
// TODO Auto-generated catch block
                e.printStackTrace();
            }
            Log.e("AudioEncoder", outData.length + " bytes written");


            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);


        }


// Without ADTS header
/*
* while (outputBufferIndex >= 0) { ByteBuffer outputBuffer =
* outputBuffers[outputBufferIndex]; byte[] outData = new
* byte[bufferInfo.size];
*
* outputBuffer.get(outData); try { outputStream.write(outData, 0,
* outData.length); } catch (IOException e) { // TODO Auto-generated
* catch block e.printStackTrace(); } Log.e("AudioEncoder",
* outData.length + " bytes written");
*
* mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
* outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
*
* }
*/
    }


    /**
     * Add ADTS header at the beginning of each and every AAC packet. This is
     * needed as MediaCodec encoder generates a packet of raw AAC data.
     * <p>
     * Note the packetLen must count in the ADTS header itself.
     **/
    public void addADTStoPacket(byte[] packet, int packetLen) {
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


    public void touch(File f) {
        try {
            if (!f.exists())
                f.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
