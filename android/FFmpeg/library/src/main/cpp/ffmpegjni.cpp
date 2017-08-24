//
// Created by wlanjie on 2017/8/22.
//

#ifndef _Included_com_wlanjie_ffmpeg_library_FFmpeg
#define _Included_com_wlanjie_ffmpeg_library_FFmpeg

#include <jni.h>
#include <system_error>

#include "log.h"
#include "av.h"
#include "avutils.h"
#include "formatcontext.h"
#include "codec.h"
#include "codeccontext.h"
#include "videorescaler.h"

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif
#define CLASS_NAME  "com/wlanjie/ffmpeg/library/FFmpeg"

#ifdef __cplusplus
extern "C" {
#endif

using namespace std;
using namespace av;

size_t videoStream = -1;
Stream stream;

jint Android_JNI_openInput(JNIEnv *env, jobject object, jstring path) {
    int count = 0;
    const char *uri = env->GetStringUTFChars(path, 0);
    FormatContext inputContext;
    VideoDecoderContext videoDecoderContext;
    string uriString { uri };
    error_code ec;
    inputContext.openInput(uriString, ec);
    if (ec) {
        LOGE("Can't not open %s\n", uri);
        return -1;
    }
    inputContext.findStreamInfo(ec);
    if (ec) {
        LOGE("Can't find stream: %s", ec.message());
        return -2;
    }

    for (size_t i = 0; i < inputContext.streamsCount(); ++i) {
        Stream st = inputContext.stream(i);
        if (st.mediaType() == AVMEDIA_TYPE_VIDEO) {
            videoStream = i;
            stream = st;
            break;
        }
    }
    if (stream.isNull()) {
        LOGE("Video Stream not found \n");
        return -3;
    }
    if (stream.isValid()) {
        videoDecoderContext = VideoDecoderContext(stream);
        videoDecoderContext.setRefCountedFrames(true);
        LOGE("Video Codec ID: %d", videoDecoderContext.raw()->codec->id);
        videoDecoderContext.open(Codec(), ec);
        if (ec) {
            LOGE("Can't open decoder\n");
            return -4;
        }
    }
    OutputFormat outputFormat;
    FormatContext outputContext;
    outputFormat.setFormat("mp4", "/sdcard/output.mp4");
    outputContext.setFormat(outputFormat);
    Codec outputCodec = findEncodingCodec(outputFormat);
    Stream outputStream = outputContext.addStream(outputCodec);
    VideoEncoderContext encoder { outputStream };
    encoder.setWidth(videoDecoderContext.width() / 2);
    encoder.setHeight(videoDecoderContext.height() / 2);
    encoder.setPixelFormat(videoDecoderContext.pixelFormat());
    encoder.setTimeBase(Rational{ 1, 1000 });
    encoder.setBitRate(videoDecoderContext.bitRate());
    encoder.addFlags(outputContext.outputFormat().isFlags(AVFMT_GLOBALHEADER) ? CODEC_FLAG_GLOBAL_HEADER : 0);
    outputStream.setFrameRate(stream.frameRate());
    outputStream.setTimeBase(encoder.timeBase());
    outputContext.openOutput("/sdcard/output.mp4", ec);
    if (ec) {
        LOGE("Can't open output \n");
        return -5;
    }
    encoder.open(ec);
    if (ec) {
        LOGE("Can't open encoder\n");
        return -6;
    }
    outputContext.dump();
    outputContext.writeHeader(ec);
    if (ec) {
        LOGE("Can't not write header \n");
        return -7;
    }
    outputContext.flush();
    VideoRescaler videoRescaler;
    while (true) {
        Packet packet = inputContext.readPacket(ec);
        if (ec) {
            LOGE("Packet reading error: %s \n", ec.message());
            break;
        }
        // EOF
        if (!packet) {
            break;
        }
        if (packet.streamIndex() != videoStream) {
            continue;
        }
        LOGI("Read packet: pts=%d dts=%d / seconds=%d timeBase=%d / st=%d", packet.pts, packet.dts, packet.pts().seconds(), packet.timeBase(), packet.streamIndex());
        // DECODING
        VideoFrame inpFrame = videoDecoderContext.decode(packet, ec);
        count++;
        if (count > 200)
            break;

        if (ec) {
            LOGE("Decoding error: %s\n", ec.message());
            return -8;
        } else if (!inpFrame) {
            LOGE("Empty frame\n");
            continue;
        }
        LOGI("inpFrame: pts=%d / %d / %d , %d x %d, size=%d, ref=%d : %d / type=%d", inpFrame.pts, inpFrame.pts().seconds(), inpFrame.timeBase(), inpFrame.width(), inpFrame.height(), inpFrame.size(), inpFrame.isReferenced(), inpFrame.refCount(), inpFrame.pictureType());
        // Change timebase
        inpFrame.setTimeBase(encoder.timeBase());
        inpFrame.setStreamIndex(0);
        inpFrame.setPictureType();
        LOGI("inpFrame: pts=%d / %d / %d , %d x %d, size=%d, ref=%d : %d / type=%d", inpFrame.pts, inpFrame.pts().seconds(), inpFrame.timeBase(), inpFrame.width(), inpFrame.height(), inpFrame.size(), inpFrame.isReferenced(), inpFrame.refCount(), inpFrame.pictureType());

        VideoFrame outFrame{ videoDecoderContext.pixelFormat(), videoDecoderContext.width() / 2, videoDecoderContext.height() / 2 };
        // SCALE
        videoRescaler.rescale(outFrame, inpFrame, ec);
        if (ec) {
            LOGE("scale video error %s.\n", ec.message());
            break;
        }
        LOGI("inpFrame: pts=%d / %d / %d , %d x %d, size=%d, ref=%d : %d / type=%d", outFrame.pts, outFrame.pts().seconds(), outFrame.timeBase(), outFrame.width(), outFrame.height(), outFrame.size(), outFrame.isReferenced(), outFrame.refCount(), outFrame.pictureType());
        // ENCODE
        Packet encoderPacket = encoder.encode(outFrame, ec);
        if (ec) {
            LOGE("Encoding error: %s", ec.message());
            return -10;
        } else if (!encoderPacket) {
            LOGE("Empty packet.\n");
            continue;
        }
        // Only one output stream
        encoderPacket.setStreamIndex(0);

        LOGI("Write packet: pts=%d, dts=%d / %d / %d / st:%d \n", encoderPacket.pts, encoderPacket.dts(), encoderPacket.pts().seconds(), encoderPacket.timeBase(), encoderPacket.streamIndex());

        outputContext.writePacket(encoderPacket, ec);
        if (ec) {
            LOGE("Error write packet: %s", ec.message());
            return -11;
        }
    }
    outputContext.writeTrailer(ec);
    if (ec) {
        LOGE("Write Trailer error.\n");
        return -9;
    }
    inputContext.close();
    outputContext.close();
    env->ReleaseStringUTFChars(path, uri);
    return 0;
}

static JNINativeMethod method[] = {
        { "openInput", "(Ljava/lang/String;)I", (void *) Android_JNI_openInput }
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_VERSION_1_6;
    }
    jclass clazz = env->FindClass(CLASS_NAME);
    env->RegisterNatives(clazz, method, NELEM(method));
    init();
    setFFmpegLoggingLevel(AV_LOG_DEBUG);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {

}

#ifdef __cplusplus
}
#endif
#endif