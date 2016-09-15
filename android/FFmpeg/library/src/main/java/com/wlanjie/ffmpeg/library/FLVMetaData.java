package com.wlanjie.ffmpeg.library;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wlanjie on 16/9/10.
 */
public class FLVMetaData {
    private static final int EMPTY_SIZE = 21;
    private static final String NAME = "onMetaData";
    private static final byte[] OBJECT_END_MARKER = { 0x00, 0x00, 0x09 };
    private List<byte[]> metaData;
    private int dataSize;
    private int pointer;
    private byte[] metaDataFrame;

    public FLVMetaData() {
        metaData = new ArrayList<>();
        dataSize = 0;
    }

    public FLVMetaData(Parameters parameters) {
        this();
        //Audio AAC
        setProperty("audiocodecid", 10);
        switch (parameters.mediacodecAACBitRate) {
            case 32 * 1024:
                setProperty("audiodatarate", 32);
                break;
            case 48 * 1024:
                setProperty("audiodatarate", 48);
                break;
            case 64 * 1024:
                setProperty("audiodatarate", 64);
                break;
        }
        switch (parameters.mediacodecAACSampleRate) {
            case 44100:
                setProperty("audiosamplerate", 44100);
                break;
            default:
                break;
        }

        //Video H264
        setProperty("videocodecid", 7);
        setProperty("framerate", parameters.mediacodecAVCFrameRate);
        setProperty("width", parameters.videoWidth);
        setProperty("height", parameters.videoHeight);
    }

    public void setProperty(String key, int value) {
        addProperty(toFLVString(key), (byte)0, toFLVNum(value));
    }

    public void setProperty(String key, String value) {
        addProperty(toFLVString(key), (byte)2, toFLVString(value));
    }

    private void addProperty(byte[] key, byte dataType, byte[] data) {
        int propertySize = key.length + 1 + data.length;
        byte[] property = new byte[propertySize];

        System.arraycopy(key, 0, property, 0, key.length);
        property[key.length] = dataType;
        System.arraycopy(data, 0, property, key.length + 1, data.length);

        metaData.add(property);
        dataSize += propertySize;
    }

    private byte[] toUI(long value, int bytes) {
        byte[] ui = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            ui[bytes - 1 - i] = (byte) (value >> (8 * i) & 0xff);
        }
        return ui;
    }

    private byte[] toFLVString(String text) {
        byte[] flvString = new byte[text.length() + 2];
        System.arraycopy(toUI(text.length(), 2), 0, flvString, 0, 2);
        System.arraycopy(text.getBytes(), 0, flvString, 2, text.length());
        return flvString;
    }

    private byte[] toFLVNum(int value) {
        long tmp = Double.doubleToLongBits(value);
        return toUI(tmp, 8);
    }

    public byte[] getMetaData() {
        metaDataFrame = new byte[dataSize + EMPTY_SIZE];
        pointer = 0;
        //SCRIPTDATA.name
        addByte(2);
        addByteArray(toFLVString(NAME));
        //SCRIPTDATA.value ECMA array
        addByte(8);
        addByteArray(toUI(metaData.size(), 4));
        for (byte[] bytes : metaData) {
            addByteArray(bytes);
        }
        addByteArray(OBJECT_END_MARKER);
        return metaDataFrame;
    }

    public void addByte(int value) {
        metaDataFrame[pointer] = (byte) (value);
        pointer++;
    }

    public void addByteArray(byte[] value) {
        System.arraycopy(value, 0, metaDataFrame, pointer, value.length);
        pointer += value.length;
    }
}
