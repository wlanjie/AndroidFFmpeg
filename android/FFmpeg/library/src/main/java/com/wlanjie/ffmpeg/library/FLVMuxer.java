package com.wlanjie.ffmpeg.library;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import net.ossrs.yasea.rtmp.DefaultRtmpPublisher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by wlanjie on 2016/10/10.
 */

class FLVMuxer {
    private final static int VIDEO_TRACK = 100;
    private final static int AUDIO_TRACK = 101;
    private final FLV flv = new FLV();
    private final Object frameLock = new Object();

    private boolean needToFindKeyFrame = true;
    private FLVFrame videoSequenceHeader;
    private FLVFrame audioSequenceHeader;
    private ConcurrentLinkedQueue<FLVFrame> frameCache = new ConcurrentLinkedQueue<>();
    private boolean connected;
    private boolean sequenceHeaderOk;
    private final DefaultRtmpPublisher publish;
    private Thread worker;

    FLVMuxer(DefaultRtmpPublisher.EventHandler handler) {
        publish = new DefaultRtmpPublisher(handler);
    }

    int addTrack(MediaFormat format) {
        if (format.getString(MediaFormat.KEY_MIME).contentEquals("video/avc")) {
            flv.setVideoTrack(format);
            return VIDEO_TRACK;
        } else {
            flv.setAudioFormat(format);
            return AUDIO_TRACK;
        }
    }

    boolean connect(String url) {
       if (!connected) {
           try {
               if (publish.connect(url)) {
                   connected = publish.publish("live");
               }
           } catch (IOException e) {
               e.printStackTrace();
           }
       }
        return connected;
    }

    void disconnect() {
        publish.closeStream();
        publish.shutdown();
        videoSequenceHeader = null;
        audioSequenceHeader = null;
        connected = false;
    }

    void sendFLVTag(FLVFrame frame) {
        if (!connected || frame == null) {
            return;
        }
        //TODO
        if (frame.isVideo()) {
            publish.publishVideoData(frame.flvTag.array(), frame.dts);
        } else if (frame.isAudio()) {
            publish.publishAudioData(frame.flvTag.array(), frame.dts);
        }
        if (frame.isKeyFrame()) {
            Log.i("rtmp", String.format("worker: send frame type=%d, dts=%d, size=%dB",
                    frame.type, frame.dts, frame.flvTag.array().length));
        }
    }

    void start(final String url) {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                if (!connect(url)) {
                    return;
                }
                while (!Thread.interrupted()) {
                    while (!frameCache.isEmpty()) {
                        FLVFrame frame = frameCache.poll();
                        try {

                            if (frame.isSequenceHeader()) {
                                if (frame.isVideo()) {
                                    videoSequenceHeader = frame;
                                    sendFLVTag(videoSequenceHeader);
                                } else if (frame.isAudio()) {
                                    audioSequenceHeader = frame;
                                    sendFLVTag(audioSequenceHeader);
                                }
                            } else {
                                if (frame.isVideo() && videoSequenceHeader != null) {
                                    sendFLVTag(frame);
                                } else if (frame.isAudio() && audioSequenceHeader != null) {
                                    sendFLVTag(frame);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        //waiting for next frame
                        synchronized (frameLock) {
                            try {
                                frameLock.wait(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                worker.interrupt();
                            }
                        }
                    }
                }
            }
        });
        worker.start();
    }

    public void stop() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                disconnect();
            }
        });
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                worker.interrupt();
            }
            frameCache.clear();
            worker = null;
        }
        flv.reset();
        needToFindKeyFrame = true;
    }

    void setVideoResolution(int width, int height) {
        publish.setVideoResolution(width, height);
    }

    void writeSampleData(int trackIndex, ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
        if (bufferInfo.offset > 0) {
            Log.w("rtmp", "encoded frame = " + bufferInfo.size + " offset = " + bufferInfo.offset + " pts = " + bufferInfo.presentationTimeUs / 1000);
        }
        if (VIDEO_TRACK == trackIndex) {
            flv.writeVideoSample(data, bufferInfo);
        } else {
            flv.writeAudioSample(data, bufferInfo);
        }
    }

    /**
     * print the size of bytes in bb
     * @param bb the bytes to print.
     * @param size the total size of bytes to print.
     */
    public static void srsPrintBytes(String tag, ByteBuffer bb, int size) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int bytes_in_line = 16;
        int max = bb.remaining();
        for (i = 0; i < size && i < max; i++) {
            sb.append(String.format("0x%s ", Integer.toHexString(bb.get(i) & 0xFF)));
            if (((i + 1) % bytes_in_line) == 0) {
                Log.i(tag, String.format("%03d-%03d: %s", i / bytes_in_line * bytes_in_line, i, sb.toString()));
                sb = new StringBuilder();
            }
        }
        if (sb.length() > 0) {
            Log.i(tag, String.format("%03d-%03d: %s", size / bytes_in_line * bytes_in_line, i - 1, sb.toString()));
        }
    }

    AtomicInteger getVideoFrameCacheNumber() {
        return publish.getVideoFrameCacheNumber();
    }

    private class CodecVideoAVCType {
        //set to the max value to reserved, for array map
        final static int Reserved = 3;
        final static int SequenceHeader = 0;
        final static int NALU = 1;
        final static int SequenceHeaderEOF = 2;
    }

    // E.4.3.1 VIDEODATA
    // Frame Type UB [4]
    // Type of video frame. The following values are defined:
    //     1 = key frame (for AVC, a seekable frame)
    //     2 = inter frame (for AVC, a non-seekable frame)
    //     3 = disposable inter frame (H.263 only)
    //     4 = generated key frame (reserved for server use only)
    //     5 = video info/command frame
    private class CodecVideoAVCFrame {
        //set to the zero to reserved, for array map
        final static int Reserved = 0;
        final static int Reserved1 = 6;

        final static int KeyFrame = 1;
        final static int InterFrame = 2;
        final static int DisposableInterFrame = 3;
        final static int GeneratedKeyFrame = 4;
        final static int VideoInfoFrame = 5;
    }

    private class CodecFLVTag {
        //set to the zero to reserved, for array map
        final static int Reserved = 0;
        // 8 = audio
        final static int Audio = 8;
        // 9 = video
        final static int Video = 9;
        //18 = script data
        final static int Script = 18;
    }

    private class FLVFrameBytes {
        public ByteBuffer data;
        public int size;
    }

    private class FLVFrame {
        //the tag bytes
        ByteBuffer flvTag;
        //the codec type for audio/aac and video/avc for instance
        int avcAACType;
        //the frame type, keyframe or not
        int frameType;
        //the tag type, audio video or data
        int type;
        //the dts in ms, tbn is 1000
        int dts;

        boolean isKeyFrame() {
            return type == CodecFLVTag.Video && frameType == CodecVideoAVCFrame.KeyFrame;
        }

        boolean isSequenceHeader() {
            return avcAACType == 0;
        }

        boolean isVideo() {
            return type == CodecFLVTag.Video;
        }

        boolean isAudio() {
            return type == CodecFLVTag.Audio;
        }
    }

    /**
     * the aac object type, for RTMP sequence header
     * for AudioSpecificConfig, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 33
     * for audioObjectType, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
     */
    private class AacObjectType {
        final static int Reserved = 0;

        // Table 1.1 – Audio Object Type definition
        // @see @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
        public final static int AacMain = 1;
        public final static int AacLC = 2;
        public final static int AacSSR = 3;

        // AAC HE = LC+SBR
        public final static int AacHE = 5;
        // AAC HEv2 = LC+SBR+PS
        public final static int AacHEV2 = 29;
    }

    private class AacProfile {
        final static int Reserved = 3;

        // @see 7.1 Profiles, aac-iso-13818-7.pdf, page 40
        final static int Main = 0;
        final static int LC = 1;
        final static int SSR = 2;
    }

    /**
     * the FLV/RTMP supported audio sample rate.
     * Sampling rate. The following values are defined:
     * 0 = 5.5 kHz = 5512 Hz
     * 1 = 11 kHz = 11025 Hz
     * 2 = 22 kHz = 22050 Hz
     * 3 = 44 kHz = 44100 Hz
     */
    private class CodecAudioSampleRate {
        // set to the max value to reserved, for array map
        final static int Reserved = 4;
        final static int R5512 = 0;
        final static int R11025 = 1;
        final static int R22050 = 2;
        final static int R44100 = 3;
    }

    /**
     * the search result for annexb
     */
    private class AnnexbSearch {
        int nbStartCode = 0;
        boolean match = false;
    }

    private class Utils {
        AnnexbSearch avcStartWithAnnexb(ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
            AnnexbSearch annexbSearch = new AnnexbSearch();
            annexbSearch.match = false;
            int pos = data.position();
            while (pos < bufferInfo.size - 3) {
                //not match
                if (data.get(pos) != 0x00 || data.get(pos + 1) != 0x00) {
                    break;
                }
                // match N[00] 00 00 01, where N>=0
                if (data.get(pos + 2) == 0x01) {
                    annexbSearch.match = true;
                    annexbSearch.nbStartCode = pos + 3 - data.position();
                    break;
                }
                pos++;
            }
            return annexbSearch;
        }

        boolean aacStartWithAdts(ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
            int pos = data.position();
            if (bufferInfo.size - pos < 2) {
                return false;
            }

            //matched 12bits 0xFFF
            //we must cast the 0xff
            if (data.get(pos) != (byte) 0xff || (byte) (data.get(pos + 1) & 0xf0) != (byte) 0xf0)
                return false;
            return true;
        }
    }


    /**
     * Table 7-1 – NAL unit type codes, syntax element categories, and NAL unit type classes
     * H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
     */
    class AvcNaluType
    {
        // Unspecified
        public final static int Reserved = 0;

        // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int NonIDR = 1;
        // Coded slice data partition A slice_data_partition_a_layer_rbsp( )
        public final static int DataPartitionA = 2;
        // Coded slice data partition B slice_data_partition_b_layer_rbsp( )
        public final static int DataPartitionB = 3;
        // Coded slice data partition C slice_data_partition_c_layer_rbsp( )
        public final static int DataPartitionC = 4;
        // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int IDR = 5;
        // Supplemental enhancement information (SEI) sei_rbsp( )
        public final static int SEI = 6;
        // Sequence parameter set seq_parameter_set_rbsp( )
        public final static int SPS = 7;
        // Picture parameter set pic_parameter_set_rbsp( )
        public final static int PPS = 8;
        // Access unit delimiter access_unit_delimiter_rbsp( )
        public final static int AccessUnitDelimiter = 9;
        // End of sequence end_of_seq_rbsp( )
        public final static int EOSequence = 10;
        // End of stream end_of_stream_rbsp( )
        public final static int EOStream = 11;
        // Filler data filler_data_rbsp( )
        public final static int FilterData = 12;
        // Sequence parameter set extension seq_parameter_set_extension_rbsp( )
        public final static int SPSExt = 13;
        // Prefix NAL unit prefix_nal_unit_rbsp( )
        public final static int PrefixNALU = 14;
        // Subset sequence parameter set subset_seq_parameter_set_rbsp( )
        public final static int SubsetSPS = 15;
        // Coded slice of an auxiliary coded picture without partitioning slice_layer_without_partitioning_rbsp( )
        public final static int LayerWithoutPartition = 19;
        // Coded slice extension slice_layer_extension_rbsp( )
        public final static int CodedSliceExt = 20;
    }

    // E.4.3.1 VIDEODATA
    // CodecID UB [4]
    // Codec Identifier. The following values are defined:
    //     2 = Sorenson H.263
    //     3 = Screen video
    //     4 = On2 VP6
    //     5 = On2 VP6 with alpha channel
    //     6 = Screen video version 2
    //     7 = AVC
    class CodecVideo
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved                = 0;
        public final static int Reserved1                = 1;
        public final static int Reserved2                = 9;

        // for user to disable video, for example, use pure audio hls.
        public final static int Disabled                = 8;

        public final static int SorensonH263             = 2;
        public final static int ScreenVideo             = 3;
        public final static int On2VP6                 = 4;
        public final static int On2VP6WithAlphaChannel = 5;
        public final static int ScreenVideoVersion2     = 6;
        public final static int AVC                     = 7;
    }

    private class RawH264Stream {
        private Utils utils;
        RawH264Stream() {
            utils = new Utils();
        }

        boolean isSps(FLVFrameBytes frame) {
            if (frame.size < 1) {
                return false;
            }
            return (frame.data.get(0) & 0x1f) == AvcNaluType.SPS;
        }

        boolean isPps(FLVFrameBytes frame) {
            if (frame.size < 1) {
                return false;
            }
            return (frame.data.get(0) & 0x1f) == AvcNaluType.PPS;
        }

        FLVFrameBytes muxIpbFrame(FLVFrameBytes frame) {
            FLVFrameBytes naluHeader = new FLVFrameBytes();
            naluHeader.size = 4;
            naluHeader.data = ByteBuffer.allocate(naluHeader.size);

            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size
            int NAL_unit_length = frame.size;

            // mux the avc NALU in "ISO Base Media File Format"
            // from H.264-AVC-ISO_IEC_14496-15.pdf, page 20
            // NALUnitLength
            naluHeader.data.putInt(NAL_unit_length);

            // reset the buffer.
            naluHeader.data.rewind();
            return naluHeader;
        }

        void muxSequenceHeader(ByteBuffer sps, ByteBuffer pps, int dts, int pts, ArrayList<FLVFrameBytes> frames) {
            // 5bytes sps/pps header:
            //      configurationVersion, AVCProfileIndication, profile_compatibility,
            //      AVCLevelIndication, lengthSizeMinusOne
            // 3bytes size of sps:
            //      numOfSequenceParameterSets, sequenceParameterSetLength(2B)
            // Nbytes of sps.
            //      sequenceParameterSetNALUnit
            // 3bytes size of pps:
            //      numOfPictureParameterSets, pictureParameterSetLength
            // Nbytes of pps:
            //      pictureParameterSetNALUnit

            // decode the SPS:
            // @see: 7.3.2.1.1, H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 62
            FLVFrameBytes hdr = new FLVFrameBytes();
            hdr.size = 5;
            hdr.data = ByteBuffer.allocate(hdr.size);

            // @see: Annex A Profiles and levels, H.264-AVC-ISO_IEC_14496-10.pdf, page 205
            //      Baseline profile profile_idc is 66(0x42).
            //      Main profile profile_idc is 77(0x4d).
            //      Extended profile profile_idc is 88(0x58).
            byte profileIdc = sps.get(1);
            //u_int8_t constraint_set = frame[2];
            byte levelIdc = sps.get(3);

            // generate the sps/pps header
            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // configurationVersion
            hdr.data.put((byte) 0x01);
            //AVCProfileIndication
            hdr.data.put(profileIdc);
            //profile_compatibility
            hdr.data.put((byte) 0x00);
            //AVCLevelIndication
            hdr.data.put((levelIdc));
            // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size,
            // so we always set it to 0x03.
            hdr.data.put((byte)0x03);

            // reset the buffer
            hdr.data.rewind();
            frames.add(hdr);

            //sps
            FLVFrameBytes spsHdr = new FLVFrameBytes();
            spsHdr.size = 3;
            spsHdr.data = ByteBuffer.allocate(spsHdr.size);
            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // numOfSequenceParameterSets, always 1
            spsHdr.data.put((byte) 0x01);
            //sequenceParameterSetLength
            spsHdr.data.putShort((short) sps.array().length);

            spsHdr.data.rewind();
            frames.add(spsHdr);

            //sequenceParameterSetNALUnit
            FLVFrameBytes spsBB = new FLVFrameBytes();
            spsBB.size = sps.array().length;
            spsBB.data = sps.duplicate();
            frames.add(spsBB);

            //pps
            FLVFrameBytes ppsHdr = new FLVFrameBytes();
            ppsHdr.size = 3;
            ppsHdr.data = ByteBuffer.allocate(ppsHdr.size);

            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // numOfPictureParameterSets, always 1
            ppsHdr.data.put((byte) 0x01);
            //pictureParameterSetLength
            ppsHdr.data.putShort((short) pps.array().length);

            ppsHdr.data.rewind();
            frames.add(ppsHdr);

            FLVFrameBytes ppsBB = new FLVFrameBytes();
            ppsBB.size = pps.array().length;
            ppsBB.data = pps.duplicate();
            frames.add(ppsBB);
        }

        FLVFrameBytes muxAVC2FLV(ArrayList<FLVFrameBytes> frames, int frameType, int avcPacketType, int dts, int pts) {
            // for h264 in RTMP video payload, there is 5bytes header:
            //      1bytes, FrameType | CodecID
            //      1bytes, AVCPacketType
            //      3bytes, CompositionTime, the cts.
            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            FLVFrameBytes flvTag = new FLVFrameBytes();
            flvTag.size = 5;
            for (int i = 0; i < frames.size(); i++) {
                FLVFrameBytes frame = frames.get(i);
                flvTag.size += frame.size;
            }
            flvTag.data = ByteBuffer.allocate(flvTag.size);

            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            // Frame Type, Type of video frame.
            // CodecID, Codec Identifier.
            // set the rtmp header
            flvTag.data.put((byte) ((frameType << 4) | CodecVideo.AVC));
            //AVCPacketType
            flvTag.data.put((byte) avcPacketType);
            //CompositionTime
            // pts = dts + cts or
            // cts = pts - dts;
            //where cts is the header in rtmp video packet payload header
            int cts = pts - dts;
            flvTag.data.put((byte) (cts >> 16));
            flvTag.data.put((byte) (dts >> 8));
            flvTag.data.put((byte) cts);

            //h.264 raw data
            for (int i = 0; i < frames.size(); i++) {
                FLVFrameBytes frame = frames.get(i);
                byte[] frameBytes = new byte[frame.size];
                frame.data.get(frameBytes);
                flvTag.data.put(frameBytes);
            }

            //reset the buffer
            flvTag.data.rewind();
            return flvTag;
        }

        FLVFrameBytes annexbDemux(ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
            FLVFrameBytes frame = new FLVFrameBytes();
            while (data.position() < bufferInfo.size) {
                // each frame must prefixed by annexb format.
                // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
                AnnexbSearch search = utils.avcStartWithAnnexb(data, bufferInfo);
                if (!search.match || search.nbStartCode < 3) {
                    FLVMuxer.srsPrintBytes("rtmp", data, 16);
                    throw new IllegalArgumentException("annexb not match for " + bufferInfo.size + " position " + data.position());
                }

                //the start codes
                ByteBuffer buffer = data.slice();
                for (int i = 0; i < search.nbStartCode; i++) {
                    data.get();
                }

                //find out the frame size
                frame.data = data.slice();
                int position = data.position();
                while (data.position() < bufferInfo.size) {
                    AnnexbSearch bsc = utils.avcStartWithAnnexb(data, bufferInfo);
                    if (bsc.match) {
                        break;
                    }
                    data.get();
                }
                frame.size = data.position() - position;
                if (data.position() < bufferInfo.size) {
                    FLVMuxer.srsPrintBytes("rtmp", buffer, 16);
                    FLVMuxer.srsPrintBytes("rtmp", data.slice(), 16);
                }
                break;
            }
            return frame;
        }
    }

    private class FLV {

        int audioChannel;
        int audioSampleRate;
        RawH264Stream avc;
        ByteBuffer h264Sps;
        ByteBuffer h264Pps;

        boolean h264SpsChanged;
        boolean h264PpsChanged;
        boolean h264SpsPpsSent;

        private byte[] aacSpecificConfig;

        FLV() {
            avc = new RawH264Stream();
        }

        void setVideoTrack(MediaFormat format) {

        }

        void setAudioFormat(MediaFormat format) {
            audioChannel = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        }

        void writeAudioSample(final ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
            int pts = (int) (bufferInfo.presentationTimeUs / 1000);
            byte[] frame = new byte[bufferInfo.size + 2];
            byte aacPacketType = 1; // 1 = AAC raw
            if (aacSpecificConfig == null) {
                frame = new byte[4];

                // @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf
                // AudioSpecificConfig (), page 33
                // 1.6.2.1 AudioSpecificConfig
                // audioObjectType; 5 bslbf
                byte ch = (byte)(data.get(0) & 0xf8);
                byte samplingFrequencyIndex = 0x04;
                if (audioSampleRate == CodecAudioSampleRate.R22050) {
                    samplingFrequencyIndex = 0x07;
                } else if (audioSampleRate == CodecAudioSampleRate.R11025) {
                    samplingFrequencyIndex = 0x0a;
                }
                ch |= (samplingFrequencyIndex >> 1) & 0x07;
                frame[2] = ch;
                ch = (byte)((samplingFrequencyIndex << 7) & 0x80);

                //7bits left
                //channelConfiguration 4 bslbf
                byte channelConfiguration = 1;
                if (audioChannel == 2) {
                    channelConfiguration = 2;
                }
                ch |= (channelConfiguration << 3) & 0x78;
                //3 bits left

                // GASpecificConfig(), page 451
                // 4.4.1 Decoder configuration (GASpecificConfig)
                // frameLengthFlag; 1 bslbf
                // dependsOnCoreCoder; 1 bslbf
                // extensionFlag; 1 bslbf
                frame[3] = ch;
                aacSpecificConfig = frame;
                aacPacketType = 0;
            } else {
                data.get(frame, 2, frame.length - 2);
            }

            byte soundFormat = 10; // AAC
            byte soundType = 0; // = Mono sound
            if (audioChannel == 2) {
                soundType = 1; // 1 = Stereo sound
            }
            byte soundSize = 1; // 1 = 16-bit samples
            byte soundRate = 3; // 44100, 22050, 11025
            if (audioSampleRate == 22050) {
                soundRate = 2;
            } else if (audioSampleRate == 11025) {
                soundRate = 1;
            }
            // for audio frame, there is 1 or 2 bytes header:
            //      1bytes, SoundFormat|SoundRate|SoundSize|SoundType
            //      1bytes, AACPacketType for SoundFormat == 10, 0 is sequence header.
            byte audioHeader = (byte) (soundType & 0x01);
            audioHeader |= (soundSize << 1) & 0x02;
            audioHeader |= (soundRate << 2) & 0x0c;
            audioHeader |= (soundFormat << 4) & 0xf0;
            frame[0] = audioHeader;
            frame[1] = aacPacketType;
            FLVFrameBytes tag = new FLVFrameBytes();
            tag.data = ByteBuffer.wrap(frame);
            tag.size = frame.length;
            rtmpWritePacket(CodecFLVTag.Audio, pts, 0, aacPacketType, tag);
        }

        void writeVideoSample(final ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
            int pts = (int) (bufferInfo.presentationTimeUs / 1000);
            ArrayList<FLVFrameBytes> frameBytes = new ArrayList<>();
            int frameType = CodecVideoAVCFrame.InterFrame;

            //send each frame.
            while (data.position() < bufferInfo.size) {
                FLVFrameBytes frame = avc.annexbDemux(data, bufferInfo);

                // 5bits, 7.3.1 NAL unit syntax,
                // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
                //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
                int nalUnitType = frame.data.get(0) & 0x1f;
                if (nalUnitType == AvcNaluType.SPS || nalUnitType == AvcNaluType.PPS) {
                    Log.i("rtmp", "annexb demux " + bufferInfo.size + " pts = " + pts + " frame = " + frame.size + " nalu = " + nalUnitType);
                }

                //for IDR frame, the frame is keyframe
                if (nalUnitType == AvcNaluType.IDR) {
                    frameType = CodecVideoAVCFrame.KeyFrame;
                }

                // ignore the nalu type aud(9)
                if (nalUnitType == AvcNaluType.AccessUnitDelimiter) {
                    continue;
                }
                //for sps
                if (avc.isSps(frame)) {
                    if (!frame.data.equals(h264Sps)) {
                        byte[] sps = new byte[frame.size];
                        frame.data.get(sps);
                        h264SpsChanged = true;
                        h264Sps = ByteBuffer.wrap(sps);
                    }
                    continue;
                }
                //for pps
                if (avc.isPps(frame)) {
                    if (!frame.data.equals(h264Pps)) {
                        byte[] pps = new byte[frame.size];
                        frame.data.get(pps);
                        h264PpsChanged = true;
                        h264Pps = ByteBuffer.wrap(pps);
                    }
                    continue;
                }

                // ibp frame
                FLVFrameBytes naluHeader = avc.muxIpbFrame(frame);
                frameBytes.add(naluHeader);
                frameBytes.add(frame);
            }
            writeH264SpsPps(pts, pts);
            writeH264IpbFrame(frameBytes, frameType, pts, pts);
        }

        void writeH264SpsPps(int dts, int pts) {
            // when sps or pps changed, update the sequence header,
            // for the pps maybe not changed while sps changed.
            // so, we must check when each video ts message frame parsed.
            if (h264SpsPpsSent && !h264SpsChanged && !h264PpsChanged) {
                return;
            }

            // when not got sps/pps wait
            if (h264Pps == null || h264Sps == null) {
                return;
            }

            //h264 raw to h264 packet
            ArrayList<FLVFrameBytes> frames = new ArrayList<>();
            avc.muxSequenceHeader(h264Sps, h264Pps, dts, pts, frames);

            // h264 packet to flv packet
            int frameType = CodecVideoAVCFrame.KeyFrame;
            int avcPacketType = CodecVideoAVCType.SequenceHeader;
            FLVFrameBytes flvTag = avc.muxAVC2FLV(frames, frameType, avcPacketType, dts, pts);

            //the timestamp in rtmp message header is dts.
            rtmpWritePacket(CodecFLVTag.Video, dts, frameType, avcPacketType, flvTag);

            //reset sps and pps
            h264SpsChanged = false;
            h264PpsChanged = false;
            h264SpsPpsSent = true;
        }

        void writeH264IpbFrame(ArrayList<FLVFrameBytes> ibps, int frameType, int dts, int pts) {
            //when sps or pps not sent, ignore the packet
            if (!h264SpsPpsSent) {
                return;
            }
            int avcPacketType = CodecVideoAVCType.NALU;
            FLVFrameBytes flvTag = avc.muxAVC2FLV(ibps, frameType, avcPacketType, dts, pts);

            // the timestamp in rtmp message header is dts
            rtmpWritePacket(CodecFLVTag.Video, dts, frameType, avcPacketType, flvTag);
        }

        void rtmpWritePacket(int type, int dts, int frameType, int avcAACType, FLVFrameBytes tag) {
            FLVFrame frame = new FLVFrame();
            frame.flvTag = ByteBuffer.allocate(tag.size);
            frame.flvTag.put(tag.data.array());
            frame.type = type;
            frame.dts = dts;
            frame.frameType = frameType;
            frame.avcAACType = avcAACType;

            if (frame.isVideo()) {
                if (needToFindKeyFrame) {
                    if (frame.isKeyFrame()) {
                        needToFindKeyFrame = false;
                        flvFrameCacheAdd(frame);
                    }
                } else {
                    flvFrameCacheAdd(frame);
                }
            } else if (frame.isAudio()) {
                flvFrameCacheAdd(frame);
            }
        }

        private void flvFrameCacheAdd(FLVFrame frame) {
            frameCache.add(frame);
            if (frame.isVideo()) {
                getVideoFrameCacheNumber().incrementAndGet();
            }
            synchronized (frameLock) {
                frameLock.notifyAll();
            }
        }

        public void reset() {
            h264SpsChanged = false;
            h264PpsChanged = false;
            h264SpsPpsSent = false;
            aacSpecificConfig = null;
        }
    }
}
