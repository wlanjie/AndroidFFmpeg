//
//  ffplay.c
//  ffplay
//
//  Created by wlanjie on 16/6/27.
//  Copyright © 2016年 com.wlanjie.ffplay. All rights reserved.
//

#include "ffplay.h"
#include "log.h"
#define MAX_AUDIO_FRAME_SIZE 192000

int frame_queue_init(FrameQueue *f, PacketQueue *q) {
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
    f->max_size = FRAME_QUEUE_SIZE;
    for (int i = 0; i < f->max_size; ++i) {
        if (!(f->queue[i].frame = av_frame_alloc())) {
            LOGE("av_frame_alloc error");
            return AVERROR(ENOMEM);
        }
    }
    return 0;
}

void packet_queue_init(PacketQueue *q) {
    memset(q, 0, sizeof(PacketQueue));
    q->mutex = SDL_CreateMutex();
    q->cond = SDL_CreateCond();
}

int packet_queue_put(PacketQueue *q, AVPacket *pkt) {
//    LOGE("packet_queue_put = %d", q->nb_packets);
    AVPacketList *pkt_list;
    if (av_dup_packet(pkt) < 0) {
        return -1;
    }
    pkt_list = av_mallocz(sizeof(AVPacketList));
    if (!pkt_list)
        return AVERROR(ENOMEM);
    pkt_list->pkt = *pkt;
    pkt_list->next = NULL;
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
            is->audio_buf = NULL;

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

int audio_decode_frame(VideoState *is, uint8_t *audio_buf, int buf_size) {
    static AVPacket pkt;
    static uint8_t *audio_pkt_data = NULL;
    static int audio_pkt_size = 0;
    static AVFrame frame;

    int len1, data_size = 0;

    while (1) {
        while(audio_pkt_size > 0) {
            int got_frame = 0;
            len1 = avcodec_decode_audio4(is->auddec.avctx, &frame, &got_frame, &pkt);
            if(len1 < 0) {
                /* if error, skip frame */
                audio_pkt_size = 0;
                break;
            }
            audio_pkt_data += len1;
            audio_pkt_size -= len1;
            if (got_frame) {
                data_size = av_samples_get_buffer_size ( NULL, is->auddec.avctx->channels, frame.nb_samples, is->auddec.avctx->sample_fmt, 1 );
                memcpy(audio_buf, frame.data[0], data_size);
            }
            if(data_size <= 0) {
                /* No data yet, get more frames */
                continue;
            }
            /* We have data, return it and come back for more later */
            return data_size;
        }
        if(pkt.data)
            av_free_packet(&pkt);

        if(is->abort_request) {
            return -1;
        }

        if(packet_queue_get(&is->audioq, &pkt, 1) < 0) {
            return -1;
        }
        audio_pkt_data = pkt.data;
        audio_pkt_size = pkt.size;
    }
    return 0;
}

void sdl_audio_callback(void *opaque, Uint8 *stream, int len) {
    int audio_size, len1;
    static unsigned int audio_buf_size = 0;
    static unsigned int audio_buf_index = 0;
    uint8_t audio_buf[MAX_AUDIO_FRAME_SIZE * 3 / 2];
    while (len > 0) {
        if (audio_buf_index >= audio_buf_size) {
            audio_size = audio_decode_frame(opaque, audio_buf, sizeof(audio_buf));
            if (audio_size < 0) {
                audio_buf_size = 1024;
                memset(audio_buf, 0, audio_buf_size);
            } else {
                audio_buf_size = audio_size;
            }
            audio_buf_index = 0;
        }
        len1 = audio_buf_size - audio_buf_index;
        if (len1 > len)
            len1 = len;
        memcpy(stream, (uint8_t *) audio_buf + audio_buf_index, len1);
        len -= len1;
        stream += len1;
        audio_buf_index += len1;
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
        frame->bmp = SDL_CreateTexture(renderer, sdl_format, SDL_TEXTUREACCESS_STREAMING, frame->width, frame->height);
        if (!frame->bmp) {
            LOGE("SDL_CreateTexture error: %s", SDL_GetError());
            return -1;
        }
        if (SDL_SetTextureBlendMode(frame->bmp, SDL_BLENDMODE_NONE) < 0) {
            LOGE("SDL_SetTextureBlendMode error: %s", SDL_GetError());
            return -1;
        }
    }
    LOGE("alloc_picture lock");
    SDL_LockMutex(is->pictq.mutex);
    frame->allocated = 1;
    SDL_CondSignal(is->pictq.cond);
    SDL_UnlockMutex(is->pictq.mutex);
    LOGE("alloc_picture unlock");
    return 0;
}

int queue_picture(VideoState *is, AVFrame *frame) {
    int ret = 0;
    LOGE("queue_picture wait lock");
    SDL_LockMutex(is->pictq.mutex);
    while (is->pictq.size >= FRAME_QUEUE_SIZE && !is->videoq.abort_request) {
        SDL_CondWait(is->pictq.cond, is->pictq.mutex);
    }
    SDL_UnlockMutex(is->pictq.mutex);
    LOGE("queue_picture wait unlock");
    if (is->videoq.abort_request)
        return -1;
    Frame *f = &is->pictq.queue[is->pictq.windex];
    if (!f->bmp || f->width != is->video_st->codec->width || f->height != is->video_st->codec->height) {
        SDL_Event event;
        f->allocated = 0;
        f->width = frame->width;
        f->height = frame->height;
        f->format = frame->format;
        event.type = FF_ALLOC_EVENT;
        event.user.data1 = is;
        SDL_PushEvent(&event);

        LOGE("queue_picture get wait lock");
        SDL_LockMutex(is->pictq.mutex);
        while (!f->allocated && !is->videoq.abort_request) {
            SDL_CondWait(is->pictq.cond, is->pictq.mutex);
        }
        SDL_UnlockMutex(is->pictq.mutex);
        LOGE("queue_picture get wait unlock");
        if (is->videoq.abort_request) {
            return -1;
        }
    }
    if (f->bmp) {
        if (++is->pictq.windex == FRAME_QUEUE_SIZE) {
            is->pictq.windex = 0;
        }
        LOGE("queue_picture lock");
        int result = SDL_LockMutex(is->pictq.mutex);
        LOGE("queue_picture lock result = %d", result);
        is->pictq.size++;
        SDL_UnlockMutex(is->pictq.mutex);
        LOGE("queue_picture unlock");
    }
    return ret;
}

int video_thread(void *opaque) {
    int ret = 0;
    VideoState *is = opaque;
    AVPacket packet;
    AVFrame *frame = av_frame_alloc();
    int got_frame;
    while (1) {
        av_usleep(250000);
        if (packet_queue_get(&is->videoq, &packet, 1) < 0) {
            break;
        }
        ret = avcodec_decode_video2(is->viddec.avctx, frame, &got_frame, &packet);
        if (ret < 0) {
            LOGE("avcodec_decode_video2 error: %s", av_err2str(ret));
            break;
        }
        if (got_frame) {
            ret = queue_picture(is, frame);
            if (ret < 0) {
                return ret;
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

int stream_component_open(VideoState *is, int stream_index) {
    int ret = 0;
    if (stream_index < 0 || stream_index >= is->ic->nb_streams) {
        return -1;
    }
    AVCodecContext *avctx = avcodec_alloc_context3(NULL);
    if (!avctx) {
        return AVERROR(ENOMEM);
    }
    ret = avcodec_copy_context(avctx, is->ic->streams[stream_index]->codec);
    if (ret < 0) {
        LOGE("avcodec_copy_context error: %s", av_err2str(ret));
        return ret;
    }
    AVCodec *codec = avcodec_find_decoder(avctx->codec_id);
    if (!codec) {
        avcodec_close(avctx);
        return AVERROR(ENOMEM);
    }
    AVDictionary *opts = NULL;
    av_dict_set(&opts, "threads", "auto", 0);
    if ((ret = avcodec_open2(avctx, codec, &opts)) < 0) {
        avcodec_close(avctx);
        LOGE("avcodec_open2 error: %s", av_err2str(ret));
        return ret;
    }
    switch (avctx->codec_type) {
        case AVMEDIA_TYPE_VIDEO:
            is->video_st = is->ic->streams[stream_index];
            is->video_stream = stream_index;
            decoder_init(&is->viddec, avctx, &is->videoq);
            packet_queue_init(&is->videoq);
            if ((ret = decoder_start(&is->viddec, video_thread, is)) < 0) {
                avcodec_close(avctx);
                av_dict_free(&opts);
                return ret;
            }
            break;
        case AVMEDIA_TYPE_AUDIO:
            is->audio_st = is->ic->streams[stream_index];
            is->audio_buf_size = 0;
            is->audio_buf_index = 0;
            is->audio_stream = stream_index;
            decoder_init(&is->auddec, avctx, &is->audioq);
            packet_queue_init(&is->audioq);
            ret = audio_open(is, avctx);
            if (ret < 0) {
                avcodec_close(avctx);
                av_dict_free(&opts);
                return ret;
            }
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
    AVPacket packet;
    av_init_packet(&packet);
    packet.data = NULL;
    packet.size = 0;
    while (av_read_frame(is->ic, &packet) >= 0) {
        if (is->audio_stream == packet.stream_index) {
//            packet_queue_put(&is->audioq, &packet);
        } else if (is->video_stream == packet.stream_index) {
            packet_queue_put(&is->videoq, &packet);
        } else {
            av_free_packet(&packet);
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
    is->read_tid = SDL_CreateThread(read_thread, "read_thread", is);
    ret = frame_queue_init(&is->pictq, &is->videoq);
    if (ret < 0) {
        for (int i = 0; i < is->pictq.max_size; ++i) {
            av_frame_free(&is->pictq.queue[i].frame);
        }
        return NULL;
    }
    ret = frame_queue_init(&is->sampq, &is->audioq);
    if (ret < 0) {
        for (int i = 0; i < is->pictq.max_size; ++i) {
            av_frame_free(&is->pictq.queue[i].frame);
        }
        return NULL;
    }
    return is;
}

void refresh_loop_wait_event(VideoState *is, SDL_Event *event) {
    if (is->video_st) {
        if (is->pictq.size > 0) {
            video_display(is);
            if (++is->pictq.windex == FRAME_QUEUE_SIZE) {
                is->pictq.windex = 0;
            }
            LOGE("refresh_loop_wait lock");
            SDL_LockMutex(is->pictq.mutex);
            is->pictq.size--;
            SDL_CondSignal(is->pictq.cond);
            SDL_UnlockMutex(is->pictq.mutex);
            LOGE("refresh_loop_wait unlock");
        }
    }
}

void event_loop(VideoState *is) {
    SDL_Event event;
    while (1) {
        if (is->videoq.abort_request) {
            break;
        }
        refresh_loop_wait_event(is, &event);
        SDL_PumpEvents();
        while (!SDL_PeepEvents(&event, 1, SDL_GETEVENT, SDL_FIRSTEVENT, SDL_LASTEVENT)) {
            SDL_PumpEvents();
        }
        switch (event.type) {
            case SDL_KEYDOWN:
                switch (event.key.keysym.sym) {
                        
                    default:
                        break;
                }
                break;
                
            case FF_ALLOC_EVENT:
                alloc_picture(event.user.data1);
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

        return AVERROR(ENOMEM);
    }

    event_loop(is);
    return 0;
}
