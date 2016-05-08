//
// Created by wlanjie on 16/5/3.
//
#include "openfile.h"
#include "ffmpeg.h"
#include "log.h"

void log_callback(void *ptr, int level, const char *fmt, va_list vl) {
    FILE *fp = fopen("/sdcard/Download/av_log.txt", "a+");
    if (fp) {
        vfprintf(fp, fmt, vl);
        fflush(fp);
        fclose(fp);
    }
}

int open_input_file(const char *input_path) {
    int ret = 0;
    av_register_all();
    avfilter_register_all();
    av_log_set_level(AV_LOG_ERROR);
    av_log_set_callback(log_callback);
    AVFormatContext *ic = avformat_alloc_context();
    ret = avformat_open_input(&ic, input_path, NULL, NULL);
    if (ret < 0) {
        LOGE("open input file error %s file path %s", av_err2str(ret), input_path);
        av_err2str(ret);
        return ret;
    }
    ret = avformat_find_stream_info(ic, NULL);
    if (ret < 0) {
        av_err2str(ret);
        return ret;
    }
    for (int i = 0; i < ic->nb_streams; ++i) {
        AVStream *st = ic->streams[i];
        GROW_ARRAY(input_streams, nb_input_streams);
        InputStream *ist = av_mallocz(sizeof(*ist));
        ist->st = st;
        ist->dec = avcodec_find_decoder(st->codec->codec_id);
        ist->dec_ctx = avcodec_alloc_context3(ist->dec);
        ret = avcodec_copy_context(ist->dec_ctx, st->codec);
        if (ret < 0) {
            av_err2str(ret);
            return ret;
        }
        input_streams[nb_input_streams - 1] = ist;
    }
    input_file = av_mallocz(sizeof(*input_file));
    input_file->ic = ic;
    return ret;
}


OutputStream *new_output_stream(AVFormatContext *oc, enum AVMediaType type, const char *codec_name, int source_index) {
    AVStream *st = avformat_new_stream(oc, NULL);
    if (!st) {
        return NULL;
    }
    OutputStream *ost = av_mallocz(sizeof(*ost));
    GROW_ARRAY(output_streams, nb_output_streams);
    ost->source_index = source_index;
    AVCodec *enc = avcodec_find_encoder_by_name(codec_name);
    if (!enc) {
        av_log(NULL, AV_LOG_ERROR, "Can't found by encoder name %s", codec_name);
        return NULL;
    }
    AVCodecContext *enc_ctx = avcodec_alloc_context3(enc);
    ost->enc_ctx = enc_ctx;
    ost->enc = enc;
    ost->st = st;
    ost->st->codec->codec_type = type;
    ost->enc_ctx->codec_type = type;
    output_streams[nb_output_streams - 1] = ost;
    if (oc->oformat->flags & AVFMT_GLOBALHEADER) {
        ost->enc_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }
    return ost;
}


int open_output_file(const char *output_path, int new_width, int new_height) {
    int ret = 0;
    AVFormatContext *oc = avformat_alloc_context();
    ret = avformat_alloc_output_context2(&oc, NULL, NULL, output_path);
    if (ret < 0) {
        av_err2str(ret);
        return ret;
    }
    output_file = av_mallocz(sizeof(*output_file));
    output_file->oc = oc;
    for (int i = 0; i < nb_input_streams; ++i) {
        InputStream *ist = input_streams[i];
        switch (ist->st->codec->codec_type) {
            case AVMEDIA_TYPE_VIDEO:
                if (av_guess_codec(oc->oformat, NULL, output_path, NULL, AVMEDIA_TYPE_VIDEO) != AV_CODEC_ID_NONE) {
                    OutputStream *ost = new_output_stream(oc, AVMEDIA_TYPE_VIDEO, "libx264", i);
                    if (ost == NULL) {
                        return AVERROR(ENOMEM);
                    }
                    if (new_width > 0 && new_height > 0) {
                        char video_size[10];
                        snprintf(video_size, sizeof(video_size), "%dx%d", new_width, new_height);
                        ret = av_parse_video_size(&ost->enc_ctx->width, &ost->enc_ctx->height,
                                                  video_size);
                        if (ret < 0) {
                            av_free(video_size);
                            av_err2str(ret);
                            return ret;
                        }
                    }
                    ost->st->sample_aspect_ratio = ost->enc_ctx->sample_aspect_ratio;
                    ost->avfilter = "null";
                }
                break;
            case AVMEDIA_TYPE_AUDIO:
                if (av_guess_codec(oc->oformat, NULL, output_path, NULL, AVMEDIA_TYPE_AUDIO) != AV_CODEC_ID_NONE) {
                    OutputStream *ost = new_output_stream(oc, AVMEDIA_TYPE_AUDIO, "aac", i);
                    if (ost == NULL) {
                        return AVERROR(ENOMEM);
                    }
                    ost->avfilter = "anull";
                }
                break;
            default:
                break;
        }
    }
    if (!(oc->oformat->flags & AVFMT_NOFILE)) {
        ret = avio_open2(&oc->pb, output_path, AVIO_FLAG_WRITE, NULL, NULL);
        if (ret < 0) {
            av_err2str(ret);
            return ret;
        }
    }
    return ret;
}


void release() {
    if (input_file) {
        avformat_close_input(&input_file->ic);
        avformat_free_context(input_file->ic);
        av_free(input_file);
    }
    if (output_file) {
        avformat_free_context(output_file->oc);
        av_free(output_file);
    }

    for (int i = 0; i < nb_input_streams; ++i) {
        InputStream *ist = input_streams[i];
        if (ist) {
            avcodec_close(ist->dec_ctx);
            avcodec_free_context(&ist->dec_ctx);
        }
    }

    for (int i = 0; i < nb_output_streams; ++i) {
        OutputStream *ost = output_streams[i];
        if (ost) {
            avcodec_close(ost->enc_ctx);
            avcodec_free_context(&ost->enc_ctx);
            av_free(&ost->avfilter);
        }
    }
}

