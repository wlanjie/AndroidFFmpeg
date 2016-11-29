//
// Created by wlanjie on 2016/10/8.
//

#ifndef STREAMING_ENCODER_H
#define STREAMING_ENCODER_H

#include <jni.h>

unsigned char nv21_to_i420(signed char *nv21_frame, int src_width, int src_height,
                  unsigned char need_flip, int rotate_degree);
void setEncoderBitrate(int bitrate);
void setEncoderFps(int fps);
void setEncoderGop(int gop_size);
void setEncoderResolution(int out_width, int out_height);
void setEncoderPreset(const char *enc_preset);
jbyteArray encoderNV21ToI420(JNIEnv *env, signed char *nv21_frame, int src_width,
                      int src_height, unsigned char need_flip, int rotate_degree);
jbyteArray NV21ToNV12(JNIEnv* env, signed char *nv21_frame, int src_width,
                      int src_height, unsigned char need_flip, int rotate_degree);
int NV21EncodeToH264(JNIEnv* env, jobject thiz, jbyteArray frame, jint src_width,
                   jint src_height, jboolean need_flip, jint rotate_degree, jlong pts);
jboolean open_aac_encoder(int channels, int sample_rate, int bitrate);
int encoder_pcm_to_aac(JNIEnv *env, jobject thiz, signed char *pcm, int pcm_length);
void close_aac_encoder();
void closeH264Encoder();
jboolean openH264Encoder();
#endif //STREAMING_ENCODER_H
