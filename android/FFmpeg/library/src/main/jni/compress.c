//
// Created by wlanjie on 16/4/26.
//

#include "compress.h"
#include "filter.h"

int get_buffer(AVCodecContext *s, AVFrame *frame, int flags) {
    return avcodec_default_get_buffer2(s, frame, flags);
}

int transcode_init() {
    int ret = 0;
    for (int i = 0; i < nb_output_streams; ++i) {
        InputStream *ist = input_streams[i];
        OutputStream *ost = output_streams[i];
        ost->st->discard = ist->st->discard;
        ost->enc_ctx->bits_per_raw_sample = ist->dec_ctx->bits_per_raw_sample;
        ost->enc_ctx->chroma_sample_location = ist->dec_ctx->chroma_sample_location;
        if (!ost->filter && (ost->enc_ctx->codec_type == AVMEDIA_TYPE_VIDEO || ost->enc_ctx->codec_type == AVMEDIA_TYPE_AUDIO)) {
            FilterGraph *graph = init_filtergraph(ist, ost);
            if ((ret = configure_filtergraph(graph)) < 0) {
                return ret;
            }
        }
        AVRational frame_rate = { 0, 0 };
        if (ost->enc_ctx->codec_type == AVMEDIA_TYPE_VIDEO) {
            frame_rate = av_buffersink_get_frame_rate(ost->filter->filter);
            if (!frame_rate.num) {
                frame_rate = (AVRational) { 25, 1 };
            }
        }
        /**
         * 设置音频和视频的参数
         * 视频包括:宽,高,帧率,这里设置的是1秒25帧,25fps,
         * 音频包括:声道数量,采样率,声道类别(左右声道),
         */
        switch (ost->enc_ctx->codec_type) {
            case AVMEDIA_TYPE_VIDEO:
                ost->enc_ctx->pix_fmt = (enum AVPixelFormat) ost->filter->filter->inputs[0]->format;
                ost->enc_ctx->width = ost->filter->filter->inputs[0]->w;
                ost->enc_ctx->height = ost->filter->filter->inputs[0]->h;
                ost->enc_ctx->time_base = av_inv_q(frame_rate);
                ost->st->avg_frame_rate = frame_rate;
                break;
            case AVMEDIA_TYPE_AUDIO:
                ost->enc_ctx->sample_fmt = (enum AVSampleFormat) ost->filter->filter->inputs[0]->format;
                ost->enc_ctx->sample_rate = ost->filter->filter->inputs[0]->sample_rate;
                ost->enc_ctx->channels = avfilter_link_get_channels(ost->filter->filter->inputs[0]);
                ost->enc_ctx->channel_layout = ost->filter->filter->inputs[0]->channel_layout;
                ost->enc_ctx->time_base = (AVRational) { 1, ost->enc_ctx->sample_rate };
                break;
            default:
                break;
        }
    }
    AVDictionary *decoder_opts = NULL;
    av_dict_set(&decoder_opts, "threads", "auto", 0);
    //打开输入的编码器
    for (int i = 0; i < nb_input_streams; ++i) {
        InputStream *ist = input_streams[i];
        ist->dec_ctx->opaque = ist;
        ist->dec_ctx->get_buffer2 = get_buffer;
        if ((ret = avcodec_open2(ist->dec_ctx, ist->dec, &decoder_opts)) < 0) {
            av_err2str(ret);
            return ret;
        }
    }
    //打开输出的编码器
    AVDictionary *encoder_opts = NULL;
    av_dict_set(&encoder_opts, "threads", "auto", 0);
    for (int i = 0; i < nb_output_streams; ++i) {
        OutputStream *ost = output_streams[i];
        if ((ret = avcodec_open2(ost->enc_ctx, ost->enc, &encoder_opts)) < 0) {
            av_err2str(ret);
            return ret;
        }
        if ((ret = avcodec_copy_context(ost->st->codec, ost->enc_ctx)) < 0) {
            av_err2str(ret);
            return ret;
        }
        ost->st->time_base = av_add_q(ost->enc_ctx->time_base, (AVRational) { 0, 1 });
        ost->st->codec->codec = ost->enc_ctx->codec;
    }
    //写文件头
    if ((ret = avformat_write_header(output_file->oc, NULL)) < 0) {
        av_err2str(ret);
        return ret;
    }
    return ret;
}

int need_output() {
    for (int i = 0; i < nb_output_streams; ++i) {
        OutputStream *ost = output_streams[i];
        if (ost->finished) {
            continue;
        }
        return 1;
    }
    return 0;
}

int decode_video(InputStream *ist, AVPacket *pkt, int *got_output) {
    int ret = 0;
    AVFrame *frame = av_frame_alloc();
    ret = avcodec_decode_video2(ist->dec_ctx, frame, got_output, pkt);
    if (!*got_output || ret < 0) {
        return ret;
    }
    pkt->size = 0;
    if (ist->st->sample_aspect_ratio.num) {
        frame->sample_aspect_ratio = ist->st->sample_aspect_ratio;
    }
    ret = av_buffersrc_add_frame_flags(ist->filter->filter, frame, AV_BUFFERSRC_FLAG_PUSH);
    if (ret != AVERROR_EOF && ret < 0) {
        return ret;
    }
    av_frame_unref(frame);
    return ret;
}

int decode_audio(InputStream *ist, AVPacket *pkt, int *got_output) {
    int ret = 0;
    AVFrame *frame = av_frame_alloc();
    ret = avcodec_decode_audio4(ist->dec_ctx, frame, got_output, pkt);
    if (!*got_output || ret < 0) {
        return ret;
    }
    ret = av_buffersrc_add_frame_flags(ist->filter->filter, frame, AV_BUFFERSRC_FLAG_PUSH);
    if (ret == AVERROR_EOF) {
        ret = 0;
    } else if (ret < 0) {
        return ret;
    }
    av_frame_unref(frame);
    return ret;
}


int process_input_packet(InputStream *ist, AVPacket *pkt) {
    int ret = 0;
    AVPacket avpkt;
    if (!pkt) {
        av_init_packet(&avpkt);
        avpkt.size = 0;
        avpkt.data = NULL;
    } else {
        avpkt = *pkt;
    }
    int got_output = 0;
    AVFrame *frame = av_frame_alloc();
    switch (ist->dec_ctx->codec_type) {
        case AVMEDIA_TYPE_VIDEO:
            ret = decode_video(ist, &avpkt, &got_output);
            break;
        case AVMEDIA_TYPE_AUDIO:
            ret = decode_audio(ist, &avpkt, &got_output);
            break;
        default:
            break;
    }
    av_frame_unref(frame);
    if (ret < 0) {
        av_err2str(ret);
        return ret;
    }
    if (!got_output && avpkt.size > 0) {
        return AVERROR_UNKNOWN;
    }
    if (!pkt && !got_output) {
        ret = av_buffersrc_add_frame(ist->filter->filter, NULL);
        if (ret < 0) {
            av_err2str(ret);
            return ret;
        }
    }
    return got_output;
}

int do_video_out(OutputStream *ost, AVFrame *next_picture) {
    int ret = 0;
    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.size = 0;
    pkt.data = NULL;
    next_picture->pts = ost->sync_opts;
    next_picture->quality = ost->enc_ctx->global_quality;
    int got_output;
    ret = avcodec_encode_video2(ost->enc_ctx, &pkt, next_picture, &got_output);
    if (ret < 0) {
        av_frame_unref(next_picture);
        av_packet_unref(&pkt);
        av_err2str(ret);
        return ret;
    }
    if (got_output) {
        if (pkt.pts == AV_NOPTS_VALUE && !(ost->enc_ctx->codec->capabilities & AV_CODEC_CAP_DELAY)) {
            pkt.pts= ost->sync_opts;
        }
        av_packet_rescale_ts(&pkt, ost->enc_ctx->time_base, ost->st->time_base);
        pkt.stream_index = ost->source_index;
        ret = av_interleaved_write_frame(output_file->oc, &pkt);
        if (ret < 0) {
            av_packet_unref(&pkt);
            av_frame_unref(next_picture);
            av_err2str(ret);
            return ret;
        }
        av_packet_unref(&pkt);
    }
    ost->sync_opts++;
    av_frame_unref(next_picture);
    return ret;
}

int do_audio_out(OutputStream *ost, AVFrame *next_picture) {
    int ret = 0;
    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.size = 0;
    pkt.data = NULL;
    int got_output;
    next_picture->pts = ost->sync_opts;
    ost->sync_opts = (uint64_t) (next_picture->pts + next_picture->nb_samples);
    ret = avcodec_encode_audio2(ost->enc_ctx, &pkt, next_picture, &got_output);
    if (ret < 0) {
        av_packet_unref(&pkt);
        av_frame_unref(next_picture);
        av_err2str(ret);
        return ret;
    }
    if (got_output) {
        av_packet_rescale_ts(&pkt, ost->enc_ctx->time_base, ost->st->time_base);
        pkt.stream_index = ost->source_index;
        ret = av_interleaved_write_frame(output_file->oc, &pkt);
        if (ret < 0) {
            av_err2str(ret);
            av_packet_unref(&pkt);
            av_frame_unref(next_picture);
            return ret;
        }
    }
    av_frame_unref(next_picture);
    return ret;
}

int reap_filters() {
    int ret = 0;
    for (int i = 0; i < nb_output_streams; ++i) {
        OutputStream *ost = output_streams[i];
        AVFrame *frame = av_frame_alloc();
        while (1) {
            ret = av_buffersink_get_frame_flags(ost->filter->filter, frame, AV_BUFFERSINK_FLAG_NO_REQUEST);
            if (ret < 0) {
                av_err2str(ret);
                break;
            }
            if (ost->finished) {
                av_frame_unref(frame);
                continue;
            }
            switch (ost->filter->filter->inputs[0]->type) {
                case AVMEDIA_TYPE_VIDEO:
                    ost->enc_ctx->sample_aspect_ratio = frame->sample_aspect_ratio;
                    ret = do_video_out(ost, frame);
                    if (ret < 0) {
                        av_frame_unref(frame);
                        return ret;
                    }
                    break;
                case AVMEDIA_TYPE_AUDIO:
                    if (!(ost->enc_ctx->codec->capabilities & AV_CODEC_CAP_PARAM_CHANGE) &&
                        ost->enc_ctx->channels != av_frame_get_channels(frame)) {
                        break;
                    }
                    ret = do_audio_out(ost, frame);
                    if (ret < 0) {
                        av_frame_unref(frame);
                        return ret;
                    }
                    break;
                default:
                    break;
            }
        }
        av_frame_unref(frame);
    }
    return 0;
}

int flush_encoders() {
    int ret = 0;
    for (int i = 0; i < nb_output_streams; ++i) {
        OutputStream *ost = output_streams[i];
        if (ost->enc_ctx->codec_type == AVMEDIA_TYPE_AUDIO && ost->enc_ctx->frame_size <= 1) {
            continue;
        }
        int stop_encoding;
        while (1) {
            int (*encode) (AVCodecContext*, AVPacket*, const AVFrame*, int*) = NULL;
            switch (ost->enc_ctx->codec_type) {
                case AVMEDIA_TYPE_VIDEO:
                    encode = avcodec_encode_video2;
                    break;
                case AVMEDIA_TYPE_AUDIO:
                    encode = avcodec_encode_audio2;
                    break;
                default:
                    break;
            }
            if (encode) {
                AVPacket pkt;
                av_init_packet(&pkt);
                pkt.size = 0;
                pkt.data = NULL;
                int got_output;
                ret = encode(ost->enc_ctx, &pkt, NULL, &got_output);
                if (ret < 0) {
                    av_packet_unref(&pkt);
                    av_err2str(ret);
                    return ret;
                }
                if (!got_output) {
                    av_packet_unref(&pkt);
                    stop_encoding = 1;
                    break;
                }
                av_packet_rescale_ts(&pkt, ost->enc_ctx->time_base, ost->st->time_base);
                pkt.stream_index = ost->source_index;
                ret = av_interleaved_write_frame(output_file->oc, &pkt);
                if (ret < 0) {
                    av_err2str(ret);
                    av_packet_unref(&pkt);
                    return ret;
                }
                av_packet_unref(&pkt);
            }
            if (stop_encoding) {
                break;
            }
        }
    }
    return ret;
}

int transcode() {
    int ret = 0;
    if (transcode_init() < 0) {
        return ret;
    }
    OutputStream *ost = NULL;
    //循环读取输入文件中的数据
    while (need_output()) {
        for (int i = 0; i < nb_output_streams; i++) {
            if (!output_streams[i]->finished) {
                ost = output_streams[i];
                break;
            }
        }
        if (ost->filter) {
            ret = avfilter_graph_request_oldest(ost->filter->graph->graph);
            if (ret == AVERROR_EOF) {
                //读取到文件未尾,继续下次循环
                ost->finished = 1;
                continue;
            } else if (ret != AVERROR(EAGAIN)) {
                //暂时没有没数据,继续下次循环
                continue;
            }
        }
        AVPacket pkt;
        ret = av_read_frame(input_file->ic, &pkt);
        if (ret < 0) {
            if (ret == AVERROR(EAGAIN)) {
                continue;
            }
            for (int i = 0; i < nb_input_streams; ++i) {
                InputStream *ist = input_streams[i];
                ret = process_input_packet(ist, NULL);
                if (ret > 0) {
                    reap_filters();
                    break;
                }
            }
            continue;
        }
        av_pkt_dump_log2(NULL, AV_LOG_ERROR, &pkt, 0, input_file->ic->streams[pkt.stream_index]);
        process_input_packet(input_streams[pkt.stream_index], &pkt);
        av_packet_unref(&pkt);
        reap_filters();
    }
    //把缓冲中剩余的数据写到输出文件中
    for (int i = 0; i < nb_input_streams; ++i) {
        process_input_packet(input_streams[i], NULL);
    }
    flush_encoders();
    //写文件尾(有的不需要)
    ret = av_write_trailer(output_file->oc);
    if (ret < 0) {
        av_err2str(ret);
        return ret;
    }
    return ret;
}