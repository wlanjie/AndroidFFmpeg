//
// Created by wlanjie on 16/8/22.
//

#include <libavutil/imgutils.h>
#include "recorder.h"
#include "log.h"

#define CHANNEL_LAYOUT 1
#define SAMPLE_RATE 44100

int recorder_init(const char *url) {
    recorder = av_mallocz(sizeof(Recorder));
    AVOutputFormat *outputFormat = av_guess_format("flv", url, NULL);
    if (!outputFormat) {
        return AVERROR(ENOMEM);
    }
    recorder->oc = avformat_alloc_context();
    av_strlcpy(recorder->oc->filename, url ? url : "", sizeof(recorder->oc->filename));
    recorder->oc->oformat = outputFormat;
    recorder->oc->oformat->video_codec = AV_CODEC_ID_FLV1;

    recorder->video_codec = avcodec_find_encoder(AV_CODEC_ID_H264);

    AVRational frame_rate = av_d2q(25, 1001000);
    const AVRational *support_framerates = recorder->video_codec->supported_framerates;
    if (support_framerates) {
        int idx = av_find_nearest_q_idx(frame_rate, support_framerates);
        frame_rate = support_framerates[idx];
    }
    recorder->video_st = avformat_new_stream(recorder->oc, recorder->video_codec);
    if (!recorder->video_st) {
        return AVERROR(ENOMEM);
    }
    AVCodecContext *avctx = avcodec_alloc_context3(recorder->video_codec);
    avctx->codec_id = recorder->video_codec->id;
    avctx->codec_type = AVMEDIA_TYPE_VIDEO;
//    avctx->bit_rate = 20;
    avctx->width = 1920;
    avctx->height = 1080;

    recorder->video_st->time_base = av_inv_q(frame_rate);
    avctx->time_base = av_inv_q(frame_rate);
    avctx->gop_size = 20;
//    avctx->flags = avctx->flags | AV_CODEC_FLAG_QSCALE;
//    avctx->global_quality = FF_QP2LAMBDA * 2;

    if (avctx->codec_id == AV_CODEC_ID_RAWVIDEO || avctx->codec_id == AV_CODEC_ID_PNG ||
            avctx->codec_id == AV_CODEC_ID_HUFFYUV || avctx->codec_id == AV_CODEC_ID_FFV1) {
        avctx->pix_fmt = AV_PIX_FMT_RGB32;
    } else {
        avctx->pix_fmt = AV_PIX_FMT_YUV420P;
    }
    if (avctx->codec_id == AV_CODEC_ID_MPEG2VIDEO) {
        avctx->max_b_frames = 2;
    } else if (avctx->codec_id == AV_CODEC_ID_MPEG1VIDEO) {
        avctx->mb_decision = 2;
    } else if (avctx->codec_id == AV_CODEC_ID_H264) {
        avctx->profile = FF_PROFILE_H264_CONSTRAINED_BASELINE;
    }

    if (recorder->oc->oformat->flags & AVFMT_GLOBALHEADER) {
        avctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }

    if (recorder->video_codec->capabilities & AV_CODEC_CAP_EXPERIMENTAL) {
        avctx->strict_std_compliance = FF_COMPLIANCE_EXPERIMENTAL;
    }
    AVCodecParameters *parameters = avcodec_parameters_alloc();
    avcodec_parameters_from_context(parameters, avctx);
    recorder->video_st->codecpar = parameters;
    recorder->avctx = avctx;

    // add audio output stream
    //如果format是flv, mp4, 3gp,音频格式为aac
    //如果format是avi, 音频格式是AV_CODEC_ID_PCM_S16LE
    recorder->oc->oformat->audio_codec = AV_CODEC_ID_AAC;
    recorder->audio_codec = avcodec_find_encoder(AV_CODEC_ID_AAC);
    recorder->audio_st = avformat_new_stream(recorder->oc, recorder->audio_codec);
    recorder->audio_st->codec->codec_id = AV_CODEC_ID_AAC;
    recorder->audio_st->codec->codec_type = AVMEDIA_TYPE_AUDIO;

    recorder->audio_st->codec->bit_rate = 64000;
    recorder->audio_st->codec->sample_rate = SAMPLE_RATE;
    recorder->audio_st->codec->channels = CHANNEL_LAYOUT;
    recorder->audio_st->codec->channel_layout = av_get_default_channel_layout(recorder->audio_st->codec->channels);
    recorder->audio_st->codec->sample_fmt = AV_SAMPLE_FMT_FLTP;
    recorder->audio_st->time_base = (AVRational) { 1, SAMPLE_RATE };
    recorder->audio_st->codec->time_base = (AVRational) { 1, SAMPLE_RATE };
    switch (recorder->audio_st->codec->sample_fmt) {
        case AV_SAMPLE_FMT_U8:
        case AV_SAMPLE_FMT_U8P:
            recorder->audio_st->codec->bits_per_raw_sample = 8;
            break;
        case AV_SAMPLE_FMT_S16:
        case AV_SAMPLE_FMT_S16P:
            recorder->audio_st->codec->bits_per_raw_sample = 16;
            break;
        case AV_SAMPLE_FMT_S32:
        case AV_SAMPLE_FMT_S32P:
            recorder->audio_st->codec->bits_per_raw_sample = 32;
            break;
        case AV_SAMPLE_FMT_FLT:
        case AV_SAMPLE_FMT_FLTP:
            recorder->audio_st->codec->bits_per_raw_sample = 32;
            break;
        case AV_SAMPLE_FMT_DBL:
        case AV_SAMPLE_FMT_DBLP:
            recorder->audio_st->codec->bits_per_raw_sample = 64;
            break;
        default:
            break;
    }
//    recorder->audio_st->codec->flags = recorder->audio_st->codec->flags | AV_CODEC_FLAG_QSCALE;
//    recorder->audio_st->codec->global_quality = FF_QP2LAMBDA * 2;
    if (recorder->oc->oformat->flags & AVFMT_GLOBALHEADER) {
        recorder->audio_st->codec->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }
    if (recorder->audio_codec->capabilities & AV_CODEC_CAP_EXPERIMENTAL) {
        recorder->audio_st->codec->strict_std_compliance = FF_COMPLIANCE_EXPERIMENTAL;
    }
    recorder->aactx = recorder->audio_st->codec;
    av_dump_format(recorder->oc, 0, url, 1);

//    AVDictionary *opts;
//    av_dict_set(&opts, "crf", "2", 0);
    int ret = avcodec_open2(recorder->avctx, recorder->video_codec, NULL);
    if (ret < 0) {
        LOGE("recorder avcodec_open2 error: %s", av_err2str(ret));
        return ret;
    }
    recorder->video_outbuf_size = (int) fmax(256 * 1024, 8 * recorder->avctx->width * recorder->avctx->height);
    recorder->video_outbuf = av_malloc(recorder->video_outbuf_size * sizeof(uint8_t));
    recorder->video_frame = av_frame_alloc();
    if (!recorder->video_frame) {
        return AVERROR(ENOMEM);
    }
    recorder->video_frame->pts = 0;
    int size = av_image_get_buffer_size(recorder->avctx->pix_fmt, recorder->avctx->width, recorder->avctx->height, 1);
//    recorder->picture_buf = av_malloc(size);

//    AVDictionary *opts;
//    av_dict_set(&opts, "crf", "2", 0);
    ret = avcodec_open2(recorder->audio_st->codec, recorder->audio_codec, NULL);
    if (ret < 0) {
        LOGE("recorder avcodec_open2 error: %s", av_err2str(ret));
        return ret;
    }

    recorder->audio_outbuf_size = 256 * 1024;
    recorder->audio_outbuf = av_malloc(recorder->audio_outbuf_size * sizeof(uint8_t));
    int planar = av_sample_fmt_is_planar(recorder->audio_st->codec->sample_fmt ? recorder->audio_st->codec->channels : 1);
    int data_size = av_samples_get_buffer_size(NULL, recorder->audio_st->codec->channels, recorder->audio_st->codec->frame_size,
                recorder->audio_st->codec->sample_fmt, 1) /  1;//planar;
    recorder->audio_frame = av_frame_alloc();
    if (!recorder->audio_frame) {
        return AVERROR(ENOMEM);
    }
    recorder->audio_frame->pts = 0;

    if (!(recorder->oc->oformat->flags & AVFMT_NOFILE)) {
        ret = avio_open2(&recorder->oc->pb, url, AVIO_FLAG_WRITE, NULL, NULL);
        if (ret < 0) {
            LOGE("avio_open2 error: %s", av_err2str(ret));
            return ret;
        }
    }
    av_dump_format(recorder->oc, 0, url, 1);
    ret = avformat_write_header(recorder->oc, NULL);
    if (ret < 0) {
        LOGE("avformat_write_header error: %s", av_err2str(ret));
        return ret;
    }
    return 0;
}

int record_samples(const uint8_t **in) {
    int ret = 0;
    if (!recorder->swr_context) {
        recorder->swr_context = swr_alloc_set_opts(recorder->swr_context, recorder->aactx->channel_layout, recorder->aactx->sample_fmt,
            recorder->aactx->sample_rate, av_get_default_channel_layout(recorder->aactx->channels), AV_SAMPLE_FMT_S16, recorder->aactx->sample_rate, 0, NULL);
        if (!recorder->swr_context || swr_init(recorder->swr_context) < 0) {
            swr_free(&recorder->swr_context);
            return -1;
        }
    }
    if (recorder->swr_context) {
        int input_count = 0;
        uint8_t **out = &recorder->audio_outbuf;
        int output_count = 0;
        int out_size = av_samples_get_buffer_size(NULL, recorder->aactx->channels, output_count, recorder->aactx->sample_fmt, 0);
        int outputDepth = av_get_bytes_per_sample(recorder->aactx->sample_fmt);
        void *samples_in_ptr = av_malloc(AV_NUM_DATA_POINTERS);
        void *samples_out_ptr = av_malloc(AV_NUM_DATA_POINTERS);

        ret = swr_convert(recorder->swr_context, out, 2048, in, 2048);
        if (ret < 0) {
            av_log(NULL, AV_LOG_ERROR, "swr_convert error: %s\n", av_err2str(ret));
            return ret;
        }
        recorder->audio_frame->nb_samples = recorder->aactx->frame_size;
        ret = avcodec_fill_audio_frame(recorder->audio_frame, recorder->aactx->channels, recorder->aactx->sample_fmt,
                            recorder->audio_outbuf, 2048, 0);
        if (ret < 0) {
            av_log(NULL, AV_LOG_ERROR, "avcodec_fill_audio_frame error: %s\n", av_err2str(ret));
            return ret;
        }
    }
    return ret;
}
