//
// Created by wlanjie on 2016/10/8.
//

#include <jni.h>
#include <libyuv.h>
#include <x264.h>
#include "libAACenc/include/aacenc_lib.h"
#include "log.h"

using namespace libyuv;

struct YuvFrame {
    int width;
    int height;
    uint8_t *data;
    uint8_t *y;
    uint8_t *u;
    uint8_t *v;
};

typedef struct x264_context {
    // encode parameter
    x264_param_t params;
    x264_t *encoder;
    x264_picture_t picture;
    // sei buffer
    uint8_t sei[256];
    int sei_size;
    // input
    int width;
    int height;
    int bitrate;
    int fps;
    int gop;
    char preset[16];
    // output
    int pts;
    int dts;
    bool is_key_frame;
} x264_context;

typedef struct fdk_aac_context {
    HANDLE_AACENCODER aac_handle;
    AACENC_InfoStruct info = { 0 };

    int channel;
    int sample_rate;
    int bit_rate;
} fdk_aac_context;

struct x264_context x264_ctx;
uint8_t h264_es[1024 * 1024];

struct fdk_aac_context aac_ctx;

struct YuvFrame i420_rotated_frame;
struct YuvFrame i420_scaled_frame;
struct YuvFrame nv12_frame;

unsigned char nv21_to_i420(signed char *nv21_frame, int src_width, int src_height,
                         unsigned char need_flip, int rotate_degree) {
    int y_size = src_width * src_height;

    if (rotate_degree % 180 == 0) {
        if (i420_rotated_frame.width != src_width || i420_rotated_frame.height != src_height) {
            free(i420_rotated_frame.data);
            i420_rotated_frame.width = src_width;
            i420_rotated_frame.height = src_height;
            i420_rotated_frame.data = (uint8_t *) malloc(y_size * 3 / 2);
            i420_rotated_frame.y = i420_rotated_frame.data;
            i420_rotated_frame.u = i420_rotated_frame.y + y_size;
            i420_rotated_frame.v = i420_rotated_frame.u + y_size / 4;
        }
    } else {
        if (i420_rotated_frame.width != src_height || i420_rotated_frame.height != src_width) {
            free(i420_rotated_frame.data);
            i420_rotated_frame.width = src_height;
            i420_rotated_frame.height = src_width;
            i420_rotated_frame.data = (uint8_t *) malloc(y_size * 3 / 2);
            i420_rotated_frame.y = i420_rotated_frame.data;
            i420_rotated_frame.u = i420_rotated_frame.y + y_size;
            i420_rotated_frame.v = i420_rotated_frame.u + y_size / 4;
        }
    }

    jint ret = ConvertToI420((uint8_t *) nv21_frame, y_size,
                             i420_rotated_frame.y, i420_rotated_frame.width,
                             i420_rotated_frame.u, i420_rotated_frame.width / 2,
                             i420_rotated_frame.v, i420_rotated_frame.width / 2,
                             0, 0,
                             src_width, src_height,
                             src_width, src_height,
                             (RotationMode) rotate_degree, FOURCC_NV21);
    if (ret < 0) {
        LOGE("ConvertToI420 failure");
        return JNI_FALSE;
    }

    ret = I420Scale(i420_rotated_frame.y, i420_rotated_frame.width,
                    i420_rotated_frame.u, i420_rotated_frame.width / 2,
                    i420_rotated_frame.v, i420_rotated_frame.width / 2,
                    need_flip ? -i420_rotated_frame.width : i420_rotated_frame.width, i420_rotated_frame.height,
                    i420_scaled_frame.y, i420_scaled_frame.width,
                    i420_scaled_frame.u, i420_scaled_frame.width / 2,
                    i420_scaled_frame.v, i420_scaled_frame.width / 2,
                    i420_scaled_frame.width, i420_scaled_frame.height,
                    kFilterNone);
    if (ret < 0) {
        LOGE("I420Scale failure");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

void setEncoderBitrate(int bitrate) {
    // Default setting i_rc_method as X264_RC_CRF which is better than X264_RC_ABR
    x264_ctx.bitrate = bitrate / 1000;  // kbps
}

void setEncoderFps(int fps) {
    x264_ctx.fps = fps;
}

void setEncoderGop(int gop_size) {
    x264_ctx.gop = gop_size;
}

void setEncoderPreset(const char *enc_preset) {
    strcpy(x264_ctx.preset, enc_preset);
}

void setEncoderResolution(int out_width, int out_height) {
    int y_size = out_width * out_height;

    if (i420_scaled_frame.width != out_width || i420_scaled_frame.height != out_height) {
        free(i420_scaled_frame.data);
        i420_scaled_frame.width = out_width;
        i420_scaled_frame.height = out_height;
        i420_scaled_frame.data = (uint8_t *) malloc(y_size * 3 / 2);
        i420_scaled_frame.y = i420_scaled_frame.data;
        i420_scaled_frame.u = i420_scaled_frame.y + y_size;
        i420_scaled_frame.v = i420_scaled_frame.u + y_size / 4;
    }

    if (nv12_frame.width != out_width || nv12_frame.height != out_height) {
        free(nv12_frame.data);
        nv12_frame.width = out_width;
        nv12_frame.height = out_height;
        nv12_frame.data = (uint8_t *) malloc(y_size * 3 / 2);
        nv12_frame.y = nv12_frame.data;
        nv12_frame.u = nv12_frame.y + y_size;
        nv12_frame.v = nv12_frame.u + y_size / 4;
    }

    x264_ctx.width = out_width;
    x264_ctx.height = out_height;
}

// For COLOR_FormatYUV420Planar
jbyteArray encoderNV21ToI420(JNIEnv *env, signed char *nv21_frame, int src_width,
                                    int src_height, unsigned char need_flip, int rotate_degree) {

    if (!nv21_to_i420(nv21_frame, src_width, src_height, need_flip, rotate_degree)) {
        return NULL;
    }

    int y_size = i420_scaled_frame.width * i420_scaled_frame.height;
    jbyteArray i420Frame = env->NewByteArray(y_size * 3 / 2);
    env->SetByteArrayRegion(i420Frame, 0, y_size * 3 / 2, (jbyte *) i420_scaled_frame.data);

    return i420Frame;
}

// For COLOR_FormatYUV420SemiPlanar
jbyteArray NV21ToNV12(JNIEnv* env, signed char *nv21_frame, int src_width,
                                    int src_height, unsigned char need_flip, int rotate_degree) {

    if (!nv21_to_i420(nv21_frame, src_width, src_height, need_flip, rotate_degree)) {
        return NULL;
    }

    int ret = ConvertFromI420(i420_scaled_frame.y, i420_scaled_frame.width,
                              i420_scaled_frame.u, i420_scaled_frame.width / 2,
                              i420_scaled_frame.v, i420_scaled_frame.width / 2,
                              nv12_frame.data, nv12_frame.width,
                              nv12_frame.width, nv12_frame.height,
                              FOURCC_NV12);
    if (ret < 0) {
        LOGE("ConvertFromI420 failure");
        return NULL;
    }

    int y_size = nv12_frame.width * nv12_frame.height;
    jbyteArray nv12Frame = env->NewByteArray(y_size * 3 / 2);
    env->SetByteArrayRegion(nv12Frame, 0, y_size * 3 / 2, (jbyte *) nv12_frame.data);

    return nv12Frame;
}

int encode_nals(const x264_nal_t *nals, int nnal) {
    int i;
    uint8_t *p = h264_es;

    /* Write the SEI as part of the first frame. */
    if (x264_ctx.sei_size > 0 && nnal > 0) {
        memcpy(p, x264_ctx.sei, x264_ctx.sei_size);
        p += x264_ctx.sei_size;
        x264_ctx.sei_size = 0;
    }

    for (i = 0; i < nnal; i++) {
        if (nals[i].i_type != NAL_SEI) {
            memcpy(p, nals[i].p_payload, nals[i].i_payload);
            p += nals[i].i_payload;
        }
    }

    return p - h264_es;
}

int x264_encode(struct YuvFrame *i420_frame, long pts) {
    int out_len, nnal;
    x264_nal_t *nal;
    x264_picture_t pic_out;
    int y_size = i420_frame->width * i420_frame->height;

    x264_ctx.picture.img.i_csp = X264_CSP_I420;
    x264_ctx.picture.img.i_plane = 3;
    x264_ctx.picture.img.plane[0] = i420_frame->y;
    x264_ctx.picture.img.i_stride[0] = i420_frame->width;
    x264_ctx.picture.img.plane[1] = i420_frame->u;
    x264_ctx.picture.img.i_stride[1] = i420_frame->width / 2;
    x264_ctx.picture.img.plane[2] = i420_frame->v;
    x264_ctx.picture.img.i_stride[2] = i420_frame->width / 2;
    x264_ctx.picture.i_pts = pts;
    x264_ctx.picture.i_type = X264_TYPE_AUTO;

    if (x264_encoder_encode(x264_ctx.encoder, &nal, &nnal, &x264_ctx.picture, &pic_out) < 0) {
        LOGE("Fail to encode in x264");
        return -1;
    }

    x264_ctx.pts = pic_out.i_pts;
    x264_ctx.dts = pic_out.i_dts;
    x264_ctx.is_key_frame = pic_out.i_type == X264_TYPE_IDR;

    return encode_nals(nal, nnal);
}

int NV21EncodeToH264(JNIEnv* env, jobject thiz, jbyteArray frame, jint src_width,
                                  jint src_height, jboolean need_flip, jint rotate_degree, jlong pts) {
    jbyte* nv21_frame = env->GetByteArrayElements(frame, NULL);

    if (!nv21_to_i420(nv21_frame, src_width, src_height, need_flip, rotate_degree)) {
        return JNI_ERR;
    }

    int es_len = x264_encode(&i420_scaled_frame, pts);
    if (es_len <= 0) {
        LOGE("Fail to encode nalu");
        return JNI_ERR;
    }

    jbyteArray outputFrame = env->NewByteArray(es_len);
    env->SetByteArrayRegion(outputFrame, 0, es_len, (jbyte *) h264_es);

    jclass clz = env->GetObjectClass(thiz);
    jmethodID mid = env->GetMethodID(clz, "onH264EncodedData", "([BIZ)V");
    env->CallVoidMethod(thiz, mid, outputFrame, x264_ctx.pts, x264_ctx.is_key_frame);

    env->ReleaseByteArrayElements(frame, nv21_frame, JNI_ABORT);
    return JNI_OK;
}

void closeH264Encoder() {
    int nnal;
    x264_nal_t *nal;
    x264_picture_t pic_out;

    if (x264_ctx.encoder != NULL) {
        while(x264_encoder_delayed_frames(x264_ctx.encoder)) {
            x264_encoder_encode(x264_ctx.encoder, &nal, &nnal, NULL, &pic_out);
        }
        x264_encoder_close(x264_ctx.encoder);
        x264_ctx.encoder = NULL;
    }
}

jboolean openH264Encoder() {
    // Presetting
    x264_param_default_preset(&x264_ctx.params, "veryfast", "zerolatency");

    // Resolution
    x264_ctx.params.i_width = x264_ctx.width;
    x264_ctx.params.i_height = x264_ctx.height;

    // Default setting i_rc_method as X264_RC_CRF which is better than X264_RC_ABR
    //x264_ctx.params.rc.i_bitrate = x264_ctx.bitrate;  // kbps
    //x264_ctx.params.rc.i_rc_method = X264_RC_ABR;

    // fps
    x264_ctx.params.i_fps_num = x264_ctx.fps;
    x264_ctx.params.i_fps_den = 1;

    x264_ctx.params.b_sliced_threads = false;

    // gop
    x264_ctx.params.i_keyint_max = x264_ctx.gop;

    if (x264_param_apply_profile(&x264_ctx.params, "baseline" ) < 0) {
        LOGE("Fail to apply profile");
        return JNI_FALSE;
    }

    x264_ctx.encoder = x264_encoder_open(&x264_ctx.params);
    if (x264_ctx.encoder == NULL) {
        LOGE("Fail to open x264 encoder!");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

jboolean open_aac_encoder(int channels, int sample_rate, int bitrate) {
    aac_ctx.channel = channels;
    aac_ctx.sample_rate = sample_rate;
    aac_ctx.bit_rate = bitrate;
    if (aacEncOpen(&aac_ctx.aac_handle, 0, channels) != AACENC_OK) {
        return JNI_FALSE;
    }
    if (aacEncoder_SetParam(aac_ctx.aac_handle, AACENC_AOT, 2) != AACENC_OK) {
        return JNI_FALSE;
    }
    if (aacEncoder_SetParam(aac_ctx.aac_handle, AACENC_SAMPLERATE, sample_rate) != AACENC_OK) {
        return JNI_FALSE;
    }
    if (aacEncoder_SetParam(aac_ctx.aac_handle, AACENC_CHANNELMODE, channels == 1 ? MODE_1 : MODE_2) != AACENC_OK) {
        return JNI_FALSE;
    }
    if (aacEncoder_SetParam(aac_ctx.aac_handle, AACENC_CHANNELORDER, 1) != AACENC_OK) {
        return JNI_FALSE;
    }
    if (aacEncoder_SetParam(aac_ctx.aac_handle, AACENC_BITRATE, bitrate) != AACENC_OK) {
        return JNI_FALSE;
    }
    if (aacEncoder_SetParam(aac_ctx.aac_handle, AACENC_TRANSMUX, 2) != AACENC_OK) {
        return JNI_FALSE;
    }
    if (aacEncEncode(aac_ctx.aac_handle, NULL, NULL, NULL, NULL) != AACENC_OK) {
        return JNI_FALSE;
    }
    if (aacEncInfo(aac_ctx.aac_handle, &aac_ctx.info) != AACENC_OK) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

int encoder_pcm_to_aac(JNIEnv *env, jobject object, signed char *pcm, int pcm_length) {
    uint8_t aac_buf[pcm_length];
    AACENC_BufDesc in_buf = { 0 };
    AACENC_BufDesc out_buf = { 0 };
    AACENC_InArgs in_args = { 0 };
    AACENC_OutArgs out_args = { 0 };
    int in_buffer_identifier = IN_AUDIO_DATA;
    int in_buffer_size = 2 * aac_ctx.channel * pcm_length;
    int in_buffer_element_size = 2;
    void *in_ptr = pcm;

    in_args.numInSamples = aac_ctx.channel * pcm_length;

    in_buf.numBufs = 1;
    in_buf.bufs = &in_ptr;
    in_buf.bufferIdentifiers = &in_buffer_identifier;
    in_buf.bufSizes = &in_buffer_size;
    in_buf.bufElSizes = &in_buffer_element_size;

    int out_buffer_identifier = OUT_BITSTREAM_DATA;
    int out_buffer_size = pcm_length;
    int out_buffer_element_size = 1;
    void *out_ptr = aac_buf;

    out_buf.bufs = &out_ptr;
    out_buf.numBufs = 1;
    out_buf.bufSizes = &out_buffer_size;
    out_buf.bufElSizes = &out_buffer_element_size;
    out_buf.bufferIdentifiers = &out_buffer_identifier;
    if (aacEncEncode(aac_ctx.aac_handle, &in_buf, &out_buf, &in_args, &out_args) != AACENC_OK) {
        return -1;
    }
    if (out_args.numOutBytes == 0) {
        return -2;
    }
    jbyteArray outputFrame = env->NewByteArray(out_args.numOutBytes);
    env->SetByteArrayRegion(outputFrame, 0, out_args.numOutBytes, (jbyte *) aac_buf);

    jclass clz = env->GetObjectClass(object);
    jmethodID mid = env->GetMethodID(clz, "onAacEncodeData", "([B)V");
    env->CallVoidMethod(object, mid, outputFrame);
    return 0;
}

void close_aac_encoder() {
    if (aac_ctx.aac_handle) {
        aacEncClose(&aac_ctx.aac_handle);
    }
}