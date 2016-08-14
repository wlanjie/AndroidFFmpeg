//
//  ffplay.c
//  ffplay
//
//  Created by wlanjie on 16/6/27.
//  Copyright © 2016年 com.wlanjie.ffplay. All rights reserved.
//

#include <pthread.h>
#include "ffplay.h"
#include "log.h"

int64_t global_video_pkt_pts = AV_NOPTS_VALUE;

int frame_queue_init(FrameQueue *f, PacketQueue *q, int max_size) {
    memset(f, 0, sizeof(FrameQueue));
    if (!(f->mutex = SDL_CreateMutex())) {
        LOGE("frame_queue_init SDL_CreateMutex error: %s", SDL_GetError());
        return AVERROR(ENOMEM);
    }
    if (!(f->cond = SDL_CreateCond())) {
        LOGE("frame_queue_init SDL_CreateCond error: %s", SDL_GetError());
        return AVERROR(ENOMEM);
    }
    f->pktq = q;
    f->max_size = max_size;
    for (int i = 0; i < f->max_size; ++i) {
        if (!(f->queue[i].frame = av_frame_alloc())) {
            LOGE("av_frame_alloc error");
            return AVERROR(ENOMEM);
        }
    }
    return 0;
}

int packet_queue_init(PacketQueue *q) {
    memset(q, 0, sizeof(PacketQueue));
    q->mutex = SDL_CreateMutex();
    if (!q->mutex) {
        LOGE("packet_queue)init SDL_CreateMutex error: %s", SDL_GetError());
        return AVERROR(ENOMEM);
    }
    q->cond = SDL_CreateCond();
    if (!q->cond) {
        LOGE("packet_queue_init SDL_CreateCond error: %s", SDL_GetError());
        return AVERROR(ENOMEM);
    }
    return 0;
}

int packet_queue_put(PacketQueue *q, AVPacket *pkt) {
//    LOGE("packet_queue_put = %d", q->nb_packets);
    if (q->abort_request)
        return -1;
    AVPacketList *pkt_list = av_mallocz(sizeof(AVPacketList));
    if (!pkt_list)
        return AVERROR(ENOMEM);
    pkt_list->pkt = *pkt;
    pkt_list->next = NULL;
    if (pkt == &flush_pkt) {
        q->serial++;
    }
    SDL_LockMutex(q->mutex);
    if (!q->last_pkt)
        q->first_pkt = pkt_list;
    else
        q->last_pkt->next = pkt_list;
    q->last_pkt = pkt_list;
    q->nb_packets++;
    q->size += pkt_list->pkt.size;
    SDL_CondSignal(q->cond);
    SDL_UnlockMutex(q->mutex);
    return 0;
}

int packet_queue_get(PacketQueue *q, AVPacket *pkt, int block) {
//    LOGE("packet_queue_get = %d", q->nb_packets);
    AVPacketList *pkt_list;
    int ret = 0;
    SDL_LockMutex(q->mutex);
    while (1) {
        if (q->abort_request)
            break;

        pkt_list = q->first_pkt;
        if (pkt_list) {
            q->first_pkt = pkt_list->next;
            if (!q->first_pkt)
                q->last_pkt = NULL;
            q->nb_packets--;
            q->size -= pkt_list->pkt.size;
            *pkt = pkt_list->pkt;
            av_free(pkt_list);
            ret = 1;
            break;
        } else if (!block) {
            ret = 0;
            break;
        } else {
            SDL_CondWait(q->cond, q->mutex);
        }
    }
    SDL_UnlockMutex(q->mutex);
    return ret;
}

void packet_queue_start(PacketQueue *q) {
    SDL_LockMutex(q->mutex);
    q->abort_request = 0;
    packet_queue_put(q, &flush_pkt);
    SDL_UnlockMutex(q->mutex);
}

Frame *frame_queue_peek_writable(FrameQueue *f) {
    SDL_LockMutex(f->mutex);
    while (f->size >= f->max_size && !f->pktq->abort_request) {
        SDL_CondWait(f->cond, f->mutex);
    }
    SDL_UnlockMutex(f->mutex);
    if (f->pktq->abort_request)
        return NULL;
    return &f->queue[f->windex];
}

void frame_queue_push(FrameQueue *f) {
    if (++f->windex == f->max_size) {
        f->windex = 0;
    }
    SDL_LockMutex(f->mutex);
    f->size++;
    SDL_CondSignal(f->cond);
    SDL_UnlockMutex(f->mutex);
}

Frame *frame_queue_peek_readable(FrameQueue *f) {
    if (f->pktq->abort_request)
        return NULL;
    SDL_LockMutex(f->mutex);
    while (f->size - f->rindex_shown <= 0 && !f->pktq->abort_request) {
        SDL_CondWait(f->cond, f->mutex);
    }
    SDL_UnlockMutex(f->mutex);
    return &f->queue[(f->rindex + f->rindex_shown) % f->max_size];
}

void frame_queue_unref_item(Frame *frame) {
    av_frame_unref(frame->frame);
}

void frame_queue_next(FrameQueue *f) {
    if (f->keep_last && !f->rindex_shown) {
        f->rindex_shown = 1;
        return;
    }
    frame_queue_unref_item(&f->queue[f->rindex]);
    if (++f->rindex == f->max_size) {
        f->rindex = 0;
    }
    SDL_LockMutex(f->mutex);
    f->size--;
    SDL_CondSignal(f->cond);
    SDL_UnlockMutex(f->mutex);
}

void init_clock(Clock *c, int *serial) {
    c->speed = 1.0;
    c->paused = 0;
    c->queue_serial = serial;
    double time = av_gettime_relative() / AV_TIME_BASE;
    c->pts = NAN;
    c->last_update = time;
    c->pts_drift = c->pts - time;
    c->serial = *serial;
}

void stream_component_close(VideoState *is, int stream_index) {
    AVFormatContext *ic = is->ic;
    AVCodecContext *avctx;

    if (stream_index < 0 || stream_index >= ic->nb_streams)
        return;
    avctx = ic->streams[stream_index]->codec;

    switch (avctx->codec_type) {
        case AVMEDIA_TYPE_AUDIO:
//            decoder_abort(&is->auddec, &is->sampq);
            SDL_CloseAudio();
//            decoder_destroy(&is->auddec);
            swr_free(&is->swr_ctx);
            av_freep(&is->audio_buf1);
            is->audio_buf1_size = 0;
//            is->audio_buf = NULL;

            if (is->rdft) {
//                av_rdft_end(is->rdft);
                av_freep(&is->rdft_data);
                is->rdft = NULL;
                is->rdft_bits = 0;
            }
            break;
        case AVMEDIA_TYPE_VIDEO:
//            decoder_abort(&is->viddec, &is->pictq);
//            decoder_destroy(&is->viddec);
            break;
        default:
            break;
    }

    ic->streams[stream_index]->discard = AVDISCARD_ALL;
    avcodec_close(avctx);
    avformat_close_input(&is->ic);
    avformat_free_context(is->ic);
    switch (avctx->codec_type) {
        case AVMEDIA_TYPE_AUDIO:
            is->audio_st = NULL;
            is->audio_stream = -1;
            break;
        case AVMEDIA_TYPE_VIDEO:
            is->video_st = NULL;
            is->video_stream = -1;
            break;
        case AVMEDIA_TYPE_SUBTITLE:
            is->subtitle_st = NULL;
            is->subtitle_stream = -1;
            break;
        default:
            break;
    }
}

void stream_close(VideoState *is) {
    /* XXX: use a special url_shutdown call to abort parse cleanly */
    is->abort_request = 1;
    SDL_WaitThread(is->read_tid, NULL);

    /* close each stream */
    if (is->audio_stream >= 0)
        stream_component_close(is, is->audio_stream);
    if (is->video_stream >= 0)
        stream_component_close(is, is->video_stream);
    if (is->subtitle_stream >= 0)
        stream_component_close(is, is->subtitle_stream);

    avformat_close_input(&is->ic);

//    packet_queue_destroy(&is->videoq);
//    packet_queue_destroy(&is->audioq);
//    packet_queue_destroy(&is->subtitleq);
//
//    /* free all pictures */
//    frame_queue_destory(&is->pictq);
//    frame_queue_destory(&is->sampq);
//    frame_queue_destory(&is->subpq);
    SDL_DestroyCond(is->continue_read_thread);
    sws_freeContext(is->img_convert_ctx);
    sws_freeContext(is->sub_convert_ctx);
    av_free(is->filename);
    if (is->vis_texture)
        SDL_DestroyTexture(is->vis_texture);
    if (is->sub_texture)
        SDL_DestroyTexture(is->sub_texture);
    av_free(is);
}

void decoder_init(Decoder *d, AVCodecContext *avctx, PacketQueue *queue) {
    memset(d, 0, sizeof(Decoder));
    d->avctx = avctx;
    d->queue = queue;
}

double get_video_clock(VideoState *is) {
    double delta = (av_gettime() - is->video_current_pts_time) / AV_TIME_BASE;
    return is->video_current_pts + delta;
}

double get_audio_clock(VideoState *is) {
    double pts = is->audclk.pts;
    int hw_buf_size = is->audio_buf_size - is->audio_buf_index;
    int bytes_per_sec = 0;
    if (is->audio_st) {
        bytes_per_sec = is->audio_st->codec->sample_rate * (is->audio_st->codec->channels * 2);
    }
    if (bytes_per_sec) {
        pts -= (double) hw_buf_size / bytes_per_sec;
    }
    return pts;
}

double get_external_clock(VideoState *is) {
    return av_gettime() / AV_TIME_BASE;
}

double get_master_clock(VideoState *is) {
    if (is->av_sync_type == AV_SYNC_VIDEO_MASTER) {
        return get_video_clock(is);
    } else if (is->av_sync_type == AV_SYNC_AUDIO_MASTER) {
        return get_audio_clock(is);
    } else {
        return get_external_clock(is);
    }
}

double synchronize_video(VideoState *is, AVFrame *frame, double pts) {
    double frame_delay;
    if (pts != NAN) {
        is->vidclk.pts = pts;
        LOGE("pts = %lf", pts);
    } else {
        pts = is->vidclk.pts;
    }
    frame_delay = av_q2d(is->video_st->codec->time_base);
    //计算重复帧
    frame_delay += frame->repeat_pict * (frame_delay * 0.5);
    is->vidclk.pts += frame_delay;
    return pts;
}

int synchronize_audio(VideoState *is, short *samples, int sample_size, double pts) {
    int n = is->audio_st->codec->channels * 2;
    if (is->av_sync_type != AV_SYNC_AUDIO_MASTER) {
        double ref_clock = get_master_clock(is);
        double diff = get_audio_clock(is) - ref_clock;
        if (diff < AV_NOSYNC_THRESHOLD) {
            is->audio_diff_cum = diff + is->audio_diff_avg_count * is->audio_diff_cum;
            if (is->audio_diff_avg_count < AUDIO_DIFF_AVG_NB) {
                is->audio_diff_avg_count++;
            } else {
                double avg_diff = is->audio_diff_cum * (1.0 - is->audio_diff_avg_coef);
                if (fabs(avg_diff) >= is->audio_diff_threshold) {
                    double wanted_size = sample_size + ((int) (diff * is->audio_st->codec->sample_rate) * n);
                    double min_size = sample_size * ((100 - SAMPLE_CORRECTION_PERCENT_MAX) / 100);
                    double max_size = sample_size * ((100 + SAMPLE_CORRECTION_PERCENT_MAX) / 100);
                    if (wanted_size < min_size) {
                        wanted_size = min_size;
                    } else if (wanted_size > max_size) {
                        wanted_size = max_size;
                    }
                    if (wanted_size < sample_size) {
                        /* remove samples */
                        sample_size = wanted_size;
                    } else if (wanted_size > sample_size) {
                        /* add samples by copying final samples */
                        int nb = (sample_size - wanted_size);
                        uint8_t *samples_end = (uint8_t *) samples + sample_size - n;
                        uint8_t *q = samples_end + n;
                        while (nb > 0) {
                            memcpy(q, samples_end, n);
                            q += n;
                            nb -= n;
                        }
                        sample_size = wanted_size;
                    }
                }
            }
        }
    } else {
        is->audio_diff_avg_count = 0;
        is->audio_diff_cum = 0;
    }
    return sample_size;
}

int decode_frame_audio(VideoState *is, AVFrame *frame) {
    av_opt_set_int(is->swr_ctx, "in_channel_layout", frame->channel_layout, 0);
    av_opt_set_int(is->swr_ctx, "out_channel_layout", frame->channel_layout, 0);
    av_opt_set_int(is->swr_ctx, "in_sample_rate", frame->sample_rate, 0);
    av_opt_set_int(is->swr_ctx, "out_sample_rate", frame->sample_rate, 0);
    av_opt_set_int(is->swr_ctx, "in_sample_fmt", frame->format, 0);
    av_opt_set_int(is->swr_ctx, "out_sample_fmt", AV_SAMPLE_FMT_S16, 0);
//    int64_t channel_layout = (frame->channel_layout && av_frame_get_channels(frame) == av_get_channel_layout_nb_channels(frame->channel_layout)) ?
//                             frame->channel_layout : av_get_default_channel_layout(av_frame_get_channels(frame));
//
//    int wanted_nb_sample
//    if (frame->format != is->audio_src.fmt || channel_layout != is->audio_src.channel_layout ||
//            frame->sample_rate != is->audio_src.freq && !is->swr_ctx) {
//        swr_free(&is->swr_ctx);
//        is->swr_ctx = swr_alloc_set_opts(NULL, is->audio_tgt.channel_layout, is->audio_tgt.fmt, is->audio_tgt.freq,
//                                        channel_layout, frame->format, frame->sample_rate, 0, NULL);
//        if (!is->swr_ctx || swr_init(is->swr_ctx) < 0) {
//            swr_free(&is->swr_ctx);
//            return -1;
//        }
//        is->audio_src.channel_layout = channel_layout;
//        is->audio_src.channels = av_frame_get_channels(frame);
//        is->audio_src.freq = frame->sample_rate;
//        is->audio_src.fmt = frame->format;
//    }
//    if (is->swr_ctx) {
//        const uint8_t **in = (const uint8_t **) frame->extended_data;
//        uint8_t **out = &is->audio_buf1;
//        int out_count =
//    }




    int64_t channel_layout = (frame->channel_layout && av_frame_get_channels(frame) == av_get_channel_layout_nb_channels(frame->channel_layout)) ?
                             frame->channel_layout : av_get_default_channel_layout(av_frame_get_channels(frame));
    if (!is->swr_ctx) {
        is->swr_ctx = swr_alloc_set_opts(NULL, is->audio_tgt.channel_layout, is->audio_tgt.fmt, is->audio_tgt.freq,
                                        channel_layout, frame->format, frame->sample_rate, 0, NULL);
    }
    if (swr_init(is->swr_ctx) < 0) {
        return -1;
    }
    int channels = av_get_channel_layout_nb_channels(frame->channel_layout);
    uint8_t **src_data = NULL, **dest_data = NULL;
    int src_linesize, dest_linesize;
    int ret = av_samples_alloc_array_and_samples(&src_data, &src_linesize, channels, frame->nb_samples, frame->format, 0);
    if (ret < 0) {
        return ret;
    }
    int64_t max_dest_nb_sample = av_rescale_rnd(frame->nb_samples, frame->sample_rate, frame->sample_rate, AV_ROUND_UP);
    int64_t dest_nb_channels = av_get_channel_layout_nb_channels(frame->channel_layout);
    ret = av_samples_alloc_array_and_samples(&dest_data, &dest_linesize, dest_nb_channels, max_dest_nb_sample, AV_SAMPLE_FMT_S16, 0);
    if (ret < 0) {
        return ret;
    }
    int64_t dest_nb_samples = av_rescale_rnd(swr_get_delay(is->swr_ctx, frame->sample_rate) + frame->nb_samples, frame->sample_rate,
                                        frame->sample_rate, AV_ROUND_UP);
    ret = swr_convert(is->swr_ctx, dest_data, dest_nb_samples, (const uint8_t **) frame->data, frame->nb_samples);
    if (ret < 0) {
        return ret;
    }
    int dest_buf_size = av_samples_get_buffer_size(&dest_linesize, dest_nb_channels, ret, AV_SAMPLE_FMT_S16, 1);
    if (dest_buf_size < 0) {
        return -1;
    }
    memcpy(is->audio_buf, dest_data[0], dest_buf_size);
    if (src_data) {
        av_freep(&src_data[0]);
    }
    av_freep(&src_data);
    if (dest_data) {
        av_freep(&dest_data[0]);
    }
    av_freep(&dest_data);
    return dest_buf_size;
}

//int audio_decode_frame(VideoState *is, double *pts_ptr) {
//    static AVPacket pkt;
//    static AVFrame frame;
//
//    int len1, data_size = 0;
//    double pts;
//    while (1) {
//        while(is->audio_pkt_size > 0) {
//            int got_frame = 0;
//            len1 = avcodec_decode_audio4(is->auddec.avctx, &frame, &got_frame, &pkt);
//            if(len1 < 0) {
//                /* if error, skip frame */
//                is->audio_pkt_size = 0;
//                break;
//            }
//
//            if (got_frame) {
//                if (frame.format != AV_SAMPLE_FMT_S16) {
//                    data_size = decode_frame_audio(is, &frame);
//                } else {
//                    data_size = av_samples_get_buffer_size(NULL, is->auddec.avctx->channels,
//                                                           frame.nb_samples,
//                                                           is->auddec.avctx->sample_fmt, 1);
//                    memcpy(is->audio_buf, frame.data[0], data_size);
//                }
//            }
//            is->audio_pkt_data += len1;
//            is->audio_pkt_size -= len1;
//            if(data_size <= 0) {
//                /* No data yet, get more frames */
//                continue;
//            }
//
//            pts = is->audclk.pts;
//            *pts_ptr = pts;
//            int n = 2 * is->audio_st->codec->channels;
//            is->audclk.pts += (double) data_size / (double) (n * is->audio_st->codec->sample_rate);
//            /* We have data, return it and come back for more later */
//            return data_size;
//        }
//        if(pkt.data)
//            av_free_packet(&pkt);
//
//        if(is->abort_request) {
//            return -1;
//        }
//
//        if(packet_queue_get(&is->audioq, &pkt, 1) < 0) {
//            return -1;
//        }
////        if (pkt.data == flush_pkt.data) {
////            avcodec_flush_buffers(is->audio_st->codec);
////            continue;
////        }
//        is->audio_pkt_data = pkt.data;
//        is->audio_pkt_size = pkt.size;
//        if (pkt.pts != AV_NOPTS_VALUE) {
//            is->audclk.pts = av_q2d(is->audio_st->time_base) * pkt.pts;
//        }
//    }
//    return 0;
//}

int audio_decode_frame(VideoState *is) {
    Frame *f = frame_queue_peek_readable(&is->sampq);
    if (!f)
        return -1;
    frame_queue_next(&is->sampq);
    return 0;
}

void sdl_audio_callback(void *opaque, Uint8 *stream, int len) {
    int audio_size, len1;
    double pts;
    VideoState *is = opaque;
    while (len > 0) {
        if (is->audio_buf_index >= is->audio_buf_size) {
            /* we have already sent all our data get more */
//            audio_size = audio_decode_frame(opaque, &pts);

            audio_size = audio_decode_frame(is);
            if (audio_size < 0) {
                is->audio_buf_size = 1024;
                memset(is->audio_buf, 0, is->audio_buf_size);
            } else {
                audio_size = synchronize_audio(is, (int16_t *) is->audio_buf, audio_size, pts);
                is->audio_buf_size = audio_size;
            }
            is->audio_buf_index = 0;
        }
        len1 = is->audio_buf_size - is->audio_buf_index;
        if (len1 > len)
            len1 = len;
        memcpy(stream, (uint8_t *) is->audio_buf + is->audio_buf_index, len1);
        len -= len1;
        stream += len1;
        is->audio_buf_index += len1;
    }
}

int audio_open(VideoState *is, AVCodecContext *avctx) {
    int ret = 0;
    SDL_AudioSpec wanted_spec, spec;
    wanted_spec.freq = avctx->sample_rate;
    wanted_spec.format = AUDIO_S16SYS;
    wanted_spec.channels = (Uint8) avctx->channels;
    wanted_spec.silence = 0;
    wanted_spec.samples = SDL_AUDIO_MIN_BUFFER_SIZE;
    wanted_spec.callback = sdl_audio_callback;
    wanted_spec.userdata = is;
    if ((ret = SDL_OpenAudio(&wanted_spec, &spec) < 0)) {
        LOGE("SDL_OpenAudio error: %s", SDL_GetError());
        return ret;
    }
    is->audio_tgt.fmt = AV_SAMPLE_FMT_S16;
    is->audio_tgt.freq = spec.freq;
    is->audio_tgt.channel_layout = spec.channels;
    is->audio_tgt.channel_layout = av_get_default_channel_layout(spec.channels);
    is->audio_tgt.frame_size = av_samples_get_buffer_size(NULL, spec.channels, 1, AV_SAMPLE_FMT_S16, 1);
    is->audio_tgt.bytes_per_sec = av_samples_get_buffer_size(NULL, spec.channels, spec.freq, AV_SAMPLE_FMT_S16, 1);
    SDL_PauseAudio(0);
    return ret;
}

int decoder_start(Decoder *d, int (*fn)(void *), void *arg) {
    packet_queue_start(d->queue);
    d->decoder_tid = SDL_CreateThread(fn, "decoder", arg);
    if (!d->decoder_tid) {
        av_log(NULL, AV_LOG_ERROR, "SDL_CreateThread(): %s\n", SDL_GetError());
        return AVERROR(ENOMEM);
    }
    return 0;
}

int alloc_picture(void *opaque) {
    VideoState *is = opaque;
    if (!window) {
        Uint32 flags = SDL_WINDOW_SHOWN | SDL_WINDOW_RESIZABLE;
        window = SDL_CreateWindow("", SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED, 640, 480, flags);
        SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY, "linear");
        if (window) {
            renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC);
            if (renderer) {
                SDL_RendererInfo info;
                if (!SDL_GetRendererInfo(renderer, &info)) {
                    LOGE("SDL_GetRenderInfo");
                }
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }
    Frame *frame = &is->pictq.queue[is->pictq.windex];
    Uint32 format;
    int access, w, h;
    Uint32 sdl_format;
    if (frame->format == AV_PIX_FMT_YUV420P) {
        sdl_format = SDL_PIXELFORMAT_YV12;
    } else {
        sdl_format = SDL_PIXELFORMAT_ARGB8888;
    }
    if (SDL_QueryTexture(frame->bmp, &format, &access, &w, &h) < 0 || frame->width != w || frame->height != h || frame->format != format) {
        SDL_DestroyTexture(frame->bmp);
//        frame->bmp = SDL_CreateTexture(renderer, sdl_format, SDL_TEXTUREACCESS_STREAMING, frame->width, frame->height);
        frame->bmp = SDL_CreateTexture(renderer, sdl_format, SDL_TEXTUREACCESS_STREAMING, 640, 480);
        if (!frame->bmp) {
            LOGE("SDL_CreateTexture error: %s", SDL_GetError());
            return -1;
        }
        if (SDL_SetTextureBlendMode(frame->bmp, SDL_BLENDMODE_NONE) < 0) {
            LOGE("SDL_SetTextureBlendMode error: %s", SDL_GetError());
            return -1;
        }
    }
    SDL_LockMutex(is->pictq.mutex);
    frame->allocated = 1;
    SDL_CondSignal(is->pictq.cond);
    SDL_UnlockMutex(is->pictq.mutex);
    return 0;
}

int queue_picture(VideoState *is, AVFrame *frame, double pts) {
    int ret = 0;
    SDL_LockMutex(is->pictq.mutex);
    while (is->pictq.size >= FRAME_QUEUE_SIZE && !is->videoq.abort_request) {
        SDL_CondWait(is->pictq.cond, is->pictq.mutex);
    }
    SDL_UnlockMutex(is->pictq.mutex);
    if (is->videoq.abort_request)
        return -1;
    Frame *f = &is->pictq.queue[is->pictq.windex];
    f->uploaded = 0;
    if (!f->bmp || f->width != is->video_st->codec->width || f->height != is->video_st->codec->height) {
        SDL_Event event;
        f->allocated = 0;
        f->width = frame->width;
        f->height = frame->height;
        f->format = frame->format;
        event.type = FF_ALLOC_EVENT;
        event.user.data1 = is;
        SDL_PushEvent(&event);

        SDL_LockMutex(is->pictq.mutex);
        while (!f->allocated && !is->videoq.abort_request) {
            SDL_CondWait(is->pictq.cond, is->pictq.mutex);
        }
        SDL_UnlockMutex(is->pictq.mutex);
        if (is->videoq.abort_request) {
            return -1;
        }
    }
    if (f->bmp) {
        f->pts = pts;
        av_frame_move_ref(f->frame, frame);
        if (++is->pictq.windex == FRAME_QUEUE_SIZE) {
            is->pictq.windex = 0;
        }
        SDL_LockMutex(is->pictq.mutex);
        is->pictq.size++;
        SDL_UnlockMutex(is->pictq.mutex);
    }
    return ret;
}

int video_thread(void *opaque) {
    int ret = 0;
    VideoState *is = opaque;
    AVPacket packet;
    AVFrame *frame = av_frame_alloc();
    int got_frame;
    double pts = NAN;
    while (1) {
        if (packet_queue_get(&is->videoq, &packet, 1) < 0) {
            break;
        }
        global_video_pkt_pts = packet.pts;
        ret = avcodec_decode_video2(is->viddec.avctx, frame, &got_frame, &packet);
        if (ret < 0) {
            LOGE("avcodec_decode_video2 error: %s", av_err2str(ret));
            break;
        }
        /**
         * 你可能已经注意到我们使用int64来表示PTS。
         * 这是因为PTS是以整型来保存的。这个值是一个时间戳相当于时间的度量，
         * 用来以流的time_base为单位进行时间度量。
         * 例如，如果一个流是24帧每秒，值为42的PTS表示这一帧应该排在第42个帧的位置。
         * 如果我们每秒有24帧（当然这值也许并不完全正确）。
         * 我们可以通过除以帧率来把这个值转化为秒。
         * 流中的time_base值以1/framerate表示（对于固定帧率来说），
         * 所以为得到以秒为单位的PTS，我们需要乘以time_base
         */
        if (packet.dts != AV_NOPTS_VALUE) {
            pts = packet.dts;
        }
        pts = av_q2d(is->video_st->time_base);
        if (got_frame) {
            pts = synchronize_video(is, frame, pts);
            ret = queue_picture(is, frame, pts);
            if (ret < 0) {
                break;
            }
        }
        av_free_packet(&packet);
    }
    av_frame_free(&frame);
    return ret;
}

void video_image_display(VideoState *is) {
    Frame *f = &is->pictq.queue[is->pictq.windex];
    if (!f->uploaded) {
        switch (f->format) {
            case AV_PIX_FMT_YUV420P:
                SDL_UpdateYUVTexture(f->bmp, NULL, f->frame->data[0], f->frame->linesize[0],
                            f->frame->data[1], f->frame->linesize[1], f->frame->data[2], f->frame->linesize[2]);
                break;
            case AV_PIX_FMT_BGRA:
                SDL_UpdateTexture(f->bmp, NULL, f->frame->data[0], f->frame->linesize[0]);
                break;
            default:
                break;
        }
        f->uploaded = 1;
    }
    SDL_Rect rect;
    rect.x = 0;
    rect.y = 0;
    rect.w = 720;
    rect.h = 1280;
    SDL_RenderCopy(renderer, f->bmp, NULL, &rect);
//    av_frame_unref(f->frame);
}

void video_display(VideoState *is) {
    if (!window || !renderer)
        return;
    SDL_SetRenderDrawColor(renderer, 0, 0, 0, 255);
    SDL_RenderClear(renderer);
    if (is->video_st) {
        video_image_display(is);
    }
    SDL_RenderPresent(renderer);
}

int decoder_decode_frame(Decoder *d, AVFrame *frame) {
    int got_frame = 0;
    AVPacket packet;
    if (d->queue->abort_request)
        return -1;
    if (packet_queue_get(d->queue, &packet, 1) < 0) {
        return -1;
    }
    switch (d->avctx->codec_type) {
        case AVMEDIA_TYPE_VIDEO:
            avcodec_decode_video2(d->avctx, frame, &got_frame, &packet);
            if (got_frame) {
                frame->pts = frame->pkt_dts;
            }
            break;
        case AVMEDIA_TYPE_AUDIO:
            avcodec_decode_audio4(d->avctx, frame, &got_frame, &packet);
            if (got_frame) {
                AVRational tb = (AVRational) { 1, frame->sample_rate };
                if (frame->pts != AV_NOPTS_VALUE) {
                    frame->pts = av_rescale_q(frame->pts, d->avctx->time_base, tb);
                } else if (frame->pkt_pts != AV_NOPTS_VALUE) {
                    frame->pts = av_rescale_q(frame->pts, av_codec_get_pkt_timebase(d->avctx), tb);
                } else if (d->next_pts != AV_NOPTS_VALUE) {
                    frame->pts = av_rescale_q(d->next_pts, d->next_pts_tb, tb);
                }
                if (frame->pts != AV_NOPTS_VALUE) {
                    d->next_pts = frame->pts + frame->nb_samples;
                    d->next_pts_tb = tb;
                }
            }
            break;
        default:
            break;
    }
    return got_frame;
}

int audio_thread(void *opaque) {
    VideoState *is = opaque;
    int ret = 0;
    AVFrame *frame = av_frame_alloc();
    while (1) {
        if (is->audioq.abort_request) {
            av_frame_free(&frame);
            break;
        }
        int got_frame = decoder_decode_frame(&is->auddec, frame);
        if (got_frame < 0) {
            av_frame_free(&frame);
            break;
        }
        if (got_frame) {
            Frame *f = frame_queue_peek_writable(&is->sampq);
            if (f == NULL) {
                av_frame_free(&frame);
                break;
            }
            f->pts = (frame->pts == AV_NOPTS_VALUE) ? NAN : frame->pts *
                                                            av_q2d(is->auddec.avctx->time_base);
            f->pos = av_frame_get_pkt_pos(frame);
            f->serial = is->auddec.pkt_serial;
            f->duration = av_q2d((AVRational) {frame->nb_samples, frame->sample_rate});
            av_frame_move_ref(f->frame, frame);
            frame_queue_push(&is->sampq);
//        if (is->audioq.serial != is->auddec.pkt_serial)
//            break;
        }
    }
    return ret;
}

int stream_component_open(VideoState *is, int stream_index) {
    int ret = 0;
    if (stream_index < 0 || stream_index >= is->ic->nb_streams) {
        return -1;
    }
    AVCodecContext *avctx = avcodec_alloc_context3(NULL);
    if (!avctx) {
        return AVERROR(ENOMEM);
    }
    ret = avcodec_parameters_to_context(avctx, is->ic->streams[stream_index]->codecpar);
    if (ret < 0) {
        LOGE("avcodec_copy_context error: %s", av_err2str(ret));
        return ret;
    }
    av_codec_set_pkt_timebase(avctx, is->ic->streams[stream_index]->time_base);
    AVCodec *codec = avcodec_find_decoder(avctx->codec_id);
    if (!codec) {
        avcodec_close(avctx);
        avcodec_free_context(&avctx);
        return AVERROR(ENOMEM);
    }
    AVDictionary *opts = NULL;
    av_dict_set(&opts, "threads", "auto", 0);
    if ((ret = avcodec_open2(avctx, codec, &opts)) < 0) {
        avcodec_close(avctx);
        avcodec_free_context(&avctx);
        LOGE("avcodec_open2 error: %s", av_err2str(ret));
        return ret;
    }
    switch (avctx->codec_type) {
        case AVMEDIA_TYPE_VIDEO:
            is->video_st = is->ic->streams[stream_index];
            is->video_stream = stream_index;
            decoder_init(&is->viddec, avctx, &is->videoq);
            if ((ret = decoder_start(&is->viddec, video_thread, is)) < 0) {
                avcodec_close(avctx);
                av_dict_free(&opts);
                return ret;
            }
            break;
        case AVMEDIA_TYPE_AUDIO:
            is->frame_timer = (double) av_gettime() / AV_TIME_BASE;
            is->frame_last_delay = NAN;
            is->video_current_pts_time = av_gettime();

            is->audio_st = is->ic->streams[stream_index];
            is->audio_buf_size = 0;
            is->audio_buf_index = 0;
            is->audio_stream = stream_index;
            ret = audio_open(is, avctx);
            is->audio_hw_buf_size = ret;
            is->audio_diff_avg_coef = exp(log((0.01 / AUDIO_DIFF_AVG_NB)));
            is->audio_diff_avg_count = 0;
            is->audio_src = is->audio_tgt;
            is->audio_diff_threshold = (is->audio_hw_buf_size) / is->audio_tgt.bytes_per_sec;
            if (ret < 0) {
                avcodec_close(avctx);
                av_dict_free(&opts);
                return ret;
            }
            decoder_init(&is->auddec, avctx, &is->audioq);
            if (decoder_start(&is->auddec, audio_thread, is) < 0) {
                avcodec_close(avctx);
                avcodec_free_context(&avctx);
                return AVERROR(ENOMEM);
            }
            SDL_PauseAudio(0);
            break;
        default:
            break;
    }

    return ret;
}

int decode_interrupt_cb(void *ctx) {
    VideoState *is = ctx;
    return is->abort_request;
}

int read_thread(void *arg) {
    int ret = 0;
    VideoState *is = arg;
    is->audio_stream = -1;
    is->video_stream = -1;
    int st_index[AVMEDIA_TYPE_NB];
    memset(st_index, -1, sizeof(st_index));
    AVFormatContext *ic = avformat_alloc_context();
    if (!ic) {
        return AVERROR(ENOMEM);
    }
    ic->interrupt_callback.callback = decode_interrupt_cb;
    ic->interrupt_callback.opaque = is;
    ret = avformat_open_input(&ic, is->filename, NULL, NULL);
    if (ret < 0) {
        LOGE("avformat_open_input error:%s", av_err2str(ret));
        stream_close(is);
        return ret;
    }
    is->ic = ic;
    if ((ret = avformat_find_stream_info(ic, NULL)) < 0) {
        LOGE("avformat_find_stream_info error: %s", av_err2str(ret));
        stream_close(is);
        return ret;
    }
    av_dump_format(ic, 0, is->filename, 0);
    for (int i = 0; i < ic->nb_streams; ++i) {
        AVStream *st = ic->streams[i];
        if (wanted_stream_spec[st->codecpar->codec_type] && st_index[i] == -1) {
            if (avformat_match_stream_specifier(ic, st, wanted_stream_spec[st->codecpar->codec_type]) > 0) {
                st_index[st->codecpar->codec_type] = i;
            }
        }
    }
    for (int i = 0; i < AVMEDIA_TYPE_NB; ++i) {
        if (wanted_stream_spec[i] && st_index[i] == -1) {
            LOGE("Stream specifier %s does not match any %s stream\n", wanted_stream_spec[i], av_get_media_type_string(i));
            st_index[i] = INT_MAX;
        }
    }
    st_index[AVMEDIA_TYPE_VIDEO] = av_find_best_stream(ic, AVMEDIA_TYPE_VIDEO, st_index[AVMEDIA_TYPE_VIDEO], -1, NULL, 0);
    st_index[AVMEDIA_TYPE_AUDIO] = av_find_best_stream(ic, AVMEDIA_TYPE_AUDIO, st_index[AVMEDIA_TYPE_AUDIO],
                                                       st_index[AVMEDIA_TYPE_VIDEO], NULL, 0);
    st_index[AVMEDIA_TYPE_SUBTITLE] = av_find_best_stream(ic, AVMEDIA_TYPE_SUBTITLE, st_index[AVMEDIA_TYPE_SUBTITLE],
                                                          (st_index[AVMEDIA_TYPE_AUDIO] > 0 ? st_index[AVMEDIA_TYPE_AUDIO] : st_index[AVMEDIA_TYPE_VIDEO]),
                                                          NULL, 0);
    is->show_mode = SHOW_MODE_NONE;
    if (st_index[AVMEDIA_TYPE_VIDEO] >= 0) {
        ret = stream_component_open(is, st_index[AVMEDIA_TYPE_VIDEO]);
        if (ret < 0) {
            LOGE("stream_component_open video error");
            return ret;
        }
    }
    if (st_index[AVMEDIA_TYPE_AUDIO] >= 0) {
        ret = stream_component_open(is, st_index[AVMEDIA_TYPE_AUDIO]);
        if (ret < 0) {
            LOGE("stream_component_open audio error");
            return ret;
        }
    }
    LOGE("read_thread");
    if (is->show_mode == SHOW_MODE_NONE) {
        is->show_mode = SHOW_MODE_VIDEO;
    }
    if (is->video_stream < 0 && is->audio_stream < 0) {
        stream_close(is);
        return -1;
    }
    AVPacket packet;
    av_init_packet(&packet);
    packet.data = NULL;
    packet.size = 0;
    while (av_read_frame(is->ic, &packet) >= 0) {
        if (ic->pb && ic->pb->error)
            break;
        if (is->audio_stream == packet.stream_index) {
            packet_queue_put(&is->audioq, &packet);
        } else if (is->video_stream == packet.stream_index) {
            packet_queue_put(&is->videoq, &packet);
        } else {
            av_packet_unref(&packet);
        }
    }
    return ret;
}

VideoState *stream_open(const char *file_name) {
    int ret = 0;
    VideoState *is = av_mallocz(sizeof(VideoState));
    if (!is) {
        return NULL;
    }

    is->filename = av_strdup(file_name);
    ret = frame_queue_init(&is->pictq, &is->videoq, VIDEO_PICTURE_QUEUE_SIZE);
    if (ret < 0) {
        for (int i = 0; i < is->pictq.max_size; ++i) {
            av_frame_free(&is->pictq.queue[i].frame);
        }
        return NULL;
    }
    ret = frame_queue_init(&is->sampq, &is->audioq, SAMPLE_QUEUE_SIZE);
    if (ret < 0) {
        for (int i = 0; i < is->pictq.max_size; ++i) {
            av_frame_free(&is->pictq.queue[i].frame);
        }
        return NULL;
    }
    if (packet_queue_init(&is->videoq) < 0 || packet_queue_init(&is->audioq) < 0) {
        return NULL;
    }
    init_clock(&is->vidclk, &is->videoq.serial);
    init_clock(&is->audclk, &is->audioq.serial);

    is->av_sync_type = AV_SYNC_AUDIO_MASTER;
    is->audio_volume = SDL_MIX_MAXVOLUME;
    is->read_tid = SDL_CreateThread(read_thread, "read_thread", is);
    if (!is->read_tid) {
        stream_close(is);
        LOGE("read_thread SDL_CreateThread: error", SDL_GetError());
        return NULL;
    }
    return is;
}

void refresh_loop_wait_event(VideoState *is, SDL_Event *event) {
    if (is->video_st) {
        if (is->pictq.size > 0) {
//            av_usleep(AV_TIME_BASE);
            video_display(is);
            if (++is->pictq.windex == FRAME_QUEUE_SIZE) {
                is->pictq.windex = 0;
            }
            SDL_LockMutex(is->pictq.mutex);
            is->pictq.size--;
            SDL_CondSignal(is->pictq.cond);
            SDL_UnlockMutex(is->pictq.mutex);
        } else {
            av_usleep(80);
        }
    }
}

Uint32 refresh_time_callback(Uint32 interval, void *opaque) {
    SDL_Event event;
    event.type = FF_REFRESH_EVENT;
    event.user.data1 = opaque;
    SDL_PushEvent(&event);
    return 0;
}

void schedule_refresh(VideoState *is, int delay) {
    SDL_AddTimer((Uint32) delay, refresh_time_callback, is);
}

void video_refresh_timer(VideoState *is) {
    if (is->video_st) {
        if (is->videoq.size == 0) {
            schedule_refresh(is, 1);
        } else {
            Frame *f = &is->pictq.queue[is->pictq.rindex];
            is->video_current_pts = f->pts;
            is->video_current_pts_time = av_gettime();
            double delay = f->pts - is->frame_last_pts;
            if (delay <= 0 || delay >= 1.0) {
                /* if incorrect delay, use previous one */
                delay = is->frame_last_delay;
            }
            /* save for next time*/
            is->frame_last_delay = delay;
            is->frame_last_pts = f->pts;

            if (is->av_sync_type != AV_SYNC_VIDEO_MASTER) {
                double ref_clock = get_master_clock(is);
                double diff = f->pts - ref_clock;
                /* skip or repeat the frame, take delay into account*/
                double sync_threshold = (delay > AV_SYNC_THRESHOLD_MIN) ? delay : AV_SYNC_THRESHOLD_MIN;
                if (fabs(diff) < AV_NOSYNC_THRESHOLD) {
                    if (diff <= -sync_threshold) {
                        delay = 0;
                    } else if (diff >= sync_threshold) {
                        delay = 2 * delay;
                    }
                }
            }

            is->frame_timer += delay;
            /* computer the REAL delay */
            double actual_delay = is->frame_timer - (av_gettime() / AV_TIME_BASE);
            if (actual_delay < 0.010) {
                actual_delay = 0.010;
            }
            schedule_refresh(is, (int) (actual_delay * 1000 + 0.5));
            /* show the picture */
            video_display(is);
            /* update queue for next picture */
            if (++is->pictq.windex == FRAME_QUEUE_SIZE) {
                is->pictq.windex = 0;
            }
            SDL_LockMutex(is->pictq.mutex);
            is->pictq.size--;
            SDL_CondSignal(is->pictq.cond);
            SDL_UnlockMutex(is->pictq.mutex);
        }
    } else {
        schedule_refresh(is, 100);
    }
}

void event_loop(VideoState *is) {
    SDL_Event event;
    schedule_refresh(is, 1);
    while (1) {
        if (is->videoq.abort_request) {
            break;
        }
//        double remaining_time = 0.0;
//        SDL_PumpEvents();
//        while (!SDL_PeepEvents(&event, 1, SDL_GETEVENT, SDL_FIRSTEVENT, SDL_LASTEVENT)) {
//            if (remaining_time > 0.0) {
//                av_usleep(remaining_time * AV_TIME_BASE);
//            }
//            remaining_time = REFRESH_RATE;
//            refresh_loop_wait_event(is, &event);
//            SDL_PumpEvents();
//        }
        SDL_WaitEvent(&event);
        switch (event.type) {
            case FF_ALLOC_EVENT:
                alloc_picture(event.user.data1);
                break;
            case FF_REFRESH_EVENT:
                video_refresh_timer(is);
                break;
            default:
                break;
        }

    }
}

int lockmgr(void **mtx, enum AVLockOp op) {
    switch (op) {
        case AV_LOCK_CREATE:
            *mtx = SDL_CreateMutex();
            if (!*mtx) {
                av_log(NULL, AV_LOG_FATAL, "SDL_CreateMutex(): %s\n", SDL_GetError());
                return 1;
            }
            return 0;
        case AV_LOCK_OBTAIN:
            return !!SDL_LockMutex(*mtx);

        case AV_LOCK_RELEASE:
            return !!SDL_UnlockMutex(*mtx);

        case AV_LOCK_DESTROY:
            SDL_DestroyMutex(*mtx);
            return 0;
    }
    return 1;
}

int init_ffplay(const char *filename) {

    SDL_Init(SDL_INIT_VIDEO | SDL_INIT_AUDIO | SDL_INIT_TIMER);

    SDL_EventState(SDL_SYSWMEVENT, SDL_IGNORE);
    SDL_EventState(SDL_USEREVENT, SDL_IGNORE);

    if (av_lockmgr_register(lockmgr)) {
        av_log(NULL, AV_LOG_FATAL, "Failed to initialize VideoState!\n");
        return AVERROR(ENOMEM);
    }

    av_init_packet(&flush_pkt);
    flush_pkt.data = (uint8_t *)&flush_pkt;

    VideoState *is = stream_open(filename);
    if (is == NULL) {
        stream_close(is);
        return AVERROR(ENOMEM);
    }

    event_loop(is);
    return 0;
}
