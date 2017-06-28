package com.wlanjie.streaming;

import android.annotation.TargetApi;
import android.os.Build;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by winlin on 5/2/15.
 * Updated by leoma on 4/1/16.
 * to POST the h.264/avc annexb frame to SRS over RTMP.
 * @remark we must start a worker thread to send data to server.
 * @see android.media.MediaMuxer https://developer.android.com/reference/android/media/MediaMuxer.html
 *
 * Usage:
 *      muxer = new SrsRtmp("rtmp://ossrs.net/live/yasea");
 *      muxer.start();
 *
 *      MediaFormat aformat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, asampleTate, achannel);
 *      // setup the aformat for audio.
 *      atrack = muxer.addTrack(aformat);
 *
 *      MediaFormat vformat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, vsize.width, vsize.height);
 *      // setup the vformat for video.
 *      vtrack = muxer.addTrack(vformat);
 *
 *      // encode the video frame from camera by h.264 codec to es and bi,
 *      // where es is the h.264 ES(element stream).
 *      ByteBuffer es, MediaCodec.BufferInfo bi;
 *      muxer.writeSampleData(vtrack, es, bi);
 *
 *      // encode the audio frame from microphone by aac codec to es and bi,
 *      // where es is the aac ES(element stream).
 *      ByteBuffer es, MediaCodec.BufferInfo bi;
 *      muxer.writeSampleData(atrack, es, bi);
 *
 *      muxer.stop();
 *      muxer.release();
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
class FlvMuxer {

    private Thread worker;
    private final Object txFrameLock = new Object();

    private Flv flv = new Flv();
    private boolean needToFindKeyFrame = true;
    private FlvFrame videoSequenceHeader;
    private FlvFrame audioSequenceHeader;
    private ConcurrentLinkedQueue<FlvFrame> frameCache = new ConcurrentLinkedQueue<>();

    FlvMuxer() {
    }

    private void sendFlvTag(FlvFrame frame) throws IllegalStateException, IOException {
        if ( frame == null) {
            return;
        }

//        if (frame.isVideo()) {
//            encoder.writeVideo(frame.dts, frame.flvTag.array());
//        } else if (frame.isAudio()) {
//            encoder.writeAudio(frame.dts, frame.flvTag.array(), 44100, 2);
//        }
    }

    /**
     * start to the remote SRS for remux.
     */
    public void start() throws IOException {

        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    while (!frameCache.isEmpty()) {
                        FlvFrame frame = frameCache.poll();
                        try {
                            if (frame.isSequenceHeader()) {
                                if (frame.isVideo()) {
                                    videoSequenceHeader = frame;
                                    sendFlvTag(videoSequenceHeader);
                                } else if (frame.isAudio()) {
                                    audioSequenceHeader = frame;
                                    sendFlvTag(audioSequenceHeader);
                                }
                            } else {
                                if (frame.isVideo() && videoSequenceHeader != null) {
                                    sendFlvTag(frame);
                                } else if (frame.isAudio() && audioSequenceHeader != null) {
                                    sendFlvTag(frame);
                                }
                            }
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(worker, ioe);
                        }
                    }
                    // Waiting for next frame
                    synchronized (txFrameLock) {
                        try {
                            // isEmpty() may take some time, so we set timeout to detect next frame
                            txFrameLock.wait(500);
                        } catch (InterruptedException ie) {
                            worker.interrupt();
                        }
                    }
                }
            }
        });
        worker.start();
    }

    /**
     * stop the muxer, disconnect RTMP connection from SRS.
     */
    public void stop() {

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

    void writeVideo(ByteBuffer data, int dataSize, int pts) {
        flv.writeVideoSample(data, dataSize, pts);
    }

    void writeAudio(ByteBuffer data, int dataSize, int sampleRate, int channel, int pts) {
        flv.writeAudioSample(data, dataSize, sampleRate, channel, pts);
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
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;
        public final static int Reserved1 = 6;

        public final static int KeyFrame                     = 1;
        public final static int InterFrame                 = 2;
        public final static int DisposableInterFrame         = 3;
        public final static int GeneratedKeyFrame            = 4;
        public final static int VideoInfoFrame                = 5;
    }

    // AVCPacketType IF CodecID == 7 UI8
    // The following values are defined:
    //     0 = AVC sequence header
    //     1 = AVC NALU
    //     2 = AVC end of sequence (lower level NALU sequence ender is
    //         not required or supported)
    private class CodecVideoAVCType {
        // set to the max value to reserved, for array map.
        public final static int Reserved                    = 3;

        public final static int SequenceHeader                 = 0;
        public final static int NALU                         = 1;
        public final static int SequenceHeaderEOF             = 2;
    }

    /**
     * E.4.1 FLV Tag, page 75
     */
    private class CodecFlvTag {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;

        // 8 = audio
        public final static int Audio = 8;
        // 9 = video
        public final static int Video = 9;
        // 18 = script data
        public final static int Script = 18;
    };

    // E.4.3.1 VIDEODATA
    // CodecID UB [4]
    // Codec Identifier. The following values are defined:
    //     2 = Sorenson H.263
    //     3 = Screen video
    //     4 = On2 VP6
    //     5 = On2 VP6 with alpha channel
    //     6 = Screen video version 2
    //     7 = AVC
    private class CodecVideo {
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

    /**
     * the aac object type, for RTMP sequence header
     * for AudioSpecificConfig, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 33
     * for audioObjectType, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
     */
    private class AacObjectType {
        public final static int Reserved = 0;

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

    /**
     * the aac profile, for ADTS(HLS/TS)
     */
    private class AacProfile {
        public final static int Reserved = 3;

        // @see 7.1 Profiles, aac-iso-13818-7.pdf, page 40
        public final static int Main = 0;
        public final static int LC = 1;
        public final static int SSR = 2;
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
        // set to the max value to reserved, for array map.
        public final static int Reserved                 = 4;

        public final static int R5512                     = 0;
        public final static int R11025                    = 1;
        public final static int R22050                    = 2;
        public final static int R44100                    = 3;
    }

    /**
     * Table 7-1 – NAL unit type codes, syntax element categories, and NAL unit type classes
     * H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
     */
    private class AvcNaluType
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

    /**
     * utils functions from srs.
     */
    private class Utils {

        private AnnexbSearch avcStartsWithAnnexb(ByteBuffer bb, int dataSize) {
            AnnexbSearch as = new AnnexbSearch();
            as.match = false;

            int pos = bb.position();
            while (pos < dataSize - 3) {
                // not match.
                if (bb.get(pos) != 0x00 || bb.get(pos + 1) != 0x00) {
                    break;
                }

                // match N[00] 00 00 01, where N>=0
                if (bb.get(pos + 2) == 0x01) {
                    as.match = true;
                    as.nb_start_code = pos + 3 - bb.position();
                    break;
                }

                pos++;
            }

            return as;
        }

        public boolean aacStartsWithAdts(ByteBuffer bb, int dataSize) {
            int pos = bb.position();
            if (dataSize - pos < 2) {
                return false;
            }

            // matched 12bits 0xFFF,
            // @remark, we must cast the 0xff to char to compare.
            if (bb.get(pos) != (byte)0xff || (byte)(bb.get(pos + 1) & 0xf0) != (byte)0xf0) {
                return false;
            }

            return true;
        }
    }

    /**
     * the search result for annexb.
     */
    private class AnnexbSearch {
        public int nb_start_code = 0;
        public boolean match = false;
    }

    /**
     * the demuxed tag frame.
     */
    private class FlvFrameBytes {
        public ByteBuffer data;
        public int size;
    }

    /**
     * the muxed flv frame.
     */
    private class FlvFrame {
        // the tag bytes.
        private ByteBuffer flvTag;
        // the codec type for audio/aac and video/avc for instance.
        private int avcAacType;
        // the frame type, keyframe or not.
        private int frameType;
        // the tag type, audio, video or data.
        private int type;
        // the dts in ms, tbn is 1000.
        private int dts;

        private boolean isKeyFrame() {
            return isVideo() && frameType == CodecVideoAVCFrame.KeyFrame;
        }

        private boolean isSequenceHeader() {
            return avcAacType == 0;
        }

        private boolean isVideo() {
            return type == CodecFlvTag.Video;
        }

        private boolean isAudio() {
            return type == CodecFlvTag.Audio;
        }
    }

    /**
     * the raw h.264 stream, in annexb.
     */
    private class RawH264Stream {
        private Utils utils;

        private RawH264Stream() {
            utils = new Utils();
        }

        private boolean isSps(FlvFrameBytes frame) {
            if (frame.size < 1) {
                return false;
            }
            return (frame.data.get(0) & 0x1f) == AvcNaluType.SPS;
        }

        private boolean isPps(FlvFrameBytes frame) {
            if (frame.size < 1) {
                return false;
            }
            return (frame.data.get(0) & 0x1f) == AvcNaluType.PPS;
        }

        private FlvFrameBytes muxIbpFrame(FlvFrameBytes frame) {
            FlvFrameBytes naluHeader = new FlvFrameBytes();
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

        private void muxSequenceHeader(ByteBuffer sps, ByteBuffer pps, int dts, int pts, ArrayList<FlvFrameBytes> frames) {
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
            if (true) {
                // for h264 in RTMP video payload, there is 5bytes header:
                //      1bytes, FrameType | CodecID
                //      1bytes, AVCPacketType
                //      3bytes, CompositionTime, the cts.
                // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
                FlvFrameBytes hdr = new FlvFrameBytes();
                hdr.size = 5;
                hdr.data = ByteBuffer.allocate(hdr.size);

                // @see: Annex A Profiles and levels, H.264-AVC-ISO_IEC_14496-10.pdf, page 205
                //      Baseline profile profile_idc is 66(0x42).
                //      Main profile profile_idc is 77(0x4d).
                //      Extended profile profile_idc is 88(0x58).
                byte profile_idc = sps.get(1);
                //u_int8_t constraint_set = frame[2];
                byte level_idc = sps.get(3);

                // generate the sps/pps header
                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // configurationVersion
                hdr.data.put((byte)0x01);
                // AVCProfileIndication
                hdr.data.put(profile_idc);
                // profile_compatibility
                hdr.data.put((byte)0x00);
                // AVCLevelIndication
                hdr.data.put(level_idc);
                // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size,
                // so we always set it to 0x03.
                hdr.data.put((byte)0x03);

                // reset the buffer.
                hdr.data.rewind();
                frames.add(hdr);
            }

            // sps
            if (true) {
                FlvFrameBytes spsHdr = new FlvFrameBytes();
                spsHdr.size = 3;
                spsHdr.data = ByteBuffer.allocate(spsHdr.size);

                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // numOfSequenceParameterSets, always 1
                spsHdr.data.put((byte) 0x01);
                // sequenceParameterSetLength
                spsHdr.data.putShort((short) sps.array().length);

                spsHdr.data.rewind();
                frames.add(spsHdr);

                // sequenceParameterSetNALUnit
                FlvFrameBytes spsbb = new FlvFrameBytes();
                spsbb.size = sps.array().length;
                spsbb.data = sps.duplicate();
                frames.add(spsbb);
            }

            // pps
            if (true) {
                FlvFrameBytes ppsHdr = new FlvFrameBytes();
                ppsHdr.size = 3;
                ppsHdr.data = ByteBuffer.allocate(ppsHdr.size);

                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // numOfPictureParameterSets, always 1
                ppsHdr.data.put((byte) 0x01);
                // pictureParameterSetLength
                ppsHdr.data.putShort((short) pps.array().length);

                ppsHdr.data.rewind();
                frames.add(ppsHdr);

                // pictureParameterSetNALUnit
                FlvFrameBytes ppsbb = new FlvFrameBytes();
                ppsbb.size = pps.array().length;
                ppsbb.data = pps.duplicate();
                frames.add(ppsbb);
            }
        }

        private FlvFrameBytes muxAvc2flv(ArrayList<FlvFrameBytes> frames, int frame_type, int avc_packet_type, int dts, int pts) {
            FlvFrameBytes flvTag = new FlvFrameBytes();

            // for h264 in RTMP video payload, there is 5bytes header:
            //      1bytes, FrameType | CodecID
            //      1bytes, AVCPacketType
            //      3bytes, CompositionTime, the cts.
            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            flvTag.size = 5;
            for (int i = 0; i < frames.size(); i++) {
                FlvFrameBytes frame = frames.get(i);
                flvTag.size += frame.size;
            }

            flvTag.data = ByteBuffer.allocate(flvTag.size);

            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            // Frame Type, Type of video frame.
            // CodecID, Codec Identifier.
            // set the rtmp header
            flvTag.data.put((byte)((frame_type << 4) | CodecVideo.AVC));

            // AVCPacketType
            flvTag.data.put((byte)avc_packet_type);

            // CompositionTime
            // pts = dts + cts, or
            // cts = pts - dts.
            // where cts is the header in rtmp video packet payload header.
            int cts = pts - dts;
            flvTag.data.put((byte)(cts >> 16));
            flvTag.data.put((byte)(cts >> 8));
            flvTag.data.put((byte)cts);

            // h.264 raw data.
            for (int i = 0; i < frames.size(); i++) {
                FlvFrameBytes frame = frames.get(i);
                byte[] frame_bytes = new byte[frame.size];
                frame.data.get(frame_bytes);
                flvTag.data.put(frame_bytes);
            }

            // reset the buffer.
            flvTag.data.rewind();
            return flvTag;
        }

        private FlvFrameBytes annexbDemux(ByteBuffer bb, int dataSize) throws IllegalArgumentException {
            FlvFrameBytes tbb = new FlvFrameBytes();

            while (bb.position() < dataSize) {
                // each frame must prefixed by annexb format.
                // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
                AnnexbSearch tbbsc = utils.avcStartsWithAnnexb(bb, dataSize);
                if (!tbbsc.match || tbbsc.nb_start_code < 3) {
                    throw new IllegalArgumentException(String.format("annexb not match for %d, pos=%d", dataSize, bb.position()));
                }
                // the start codes.
                for (int i = 0; i < tbbsc.nb_start_code; i++) {
                    bb.get();
                }

                // find out the frame size.
                tbb.data = bb.slice();
                int pos = bb.position();
                while (bb.position() < dataSize) {
                    AnnexbSearch bsc = utils.avcStartsWithAnnexb(bb, dataSize);
                    if (bsc.match) {
                        break;
                    }
                    bb.get();
                }

                tbb.size = bb.position() - pos;
                break;
            }

            return tbb;
        }
    }

    private class RawAacStreamCodec {
        public byte protection_absent;
        // AacObjectType
        public int aac_object;
        public byte sampling_frequency_index;
        public byte channel_configuration;
        public short frame_length;

        public byte sound_format;
        public byte sound_rate;
        public byte sound_size;
        public byte sound_type;
        // 0 for sh; 1 for raw data.
        public byte aac_packet_type;

        public byte[] frame;
    }

    /**
     * remux the annexb to flv tags.
     */
    private class Flv {

        private RawH264Stream avc;
        private ByteBuffer h264Sps;
        private boolean h264SpsChanged;
        private ByteBuffer h264Pps;
        private boolean h264PpsChanged;
        private boolean h264SpsPpsSent;
        private byte[] aacSpecificConfig;

        private Flv() {
            avc = new RawH264Stream();
            reset();
        }

        private void reset() {
            h264SpsChanged = false;
            h264PpsChanged = false;
            h264SpsPpsSent = false;
            aacSpecificConfig = null;
        }

        private void writeAudioSample(final ByteBuffer data, int dataSize, int sampleRate, int channel, int pts) {

            byte[] frame = new byte[dataSize + 2];
            byte aacPacketType = 1; // 1 = AAC raw
            if (aacSpecificConfig == null) {
                frame = new byte[4];

                // @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf
                // AudioSpecificConfig (), page 33
                // 1.6.2.1 AudioSpecificConfig
                // audioObjectType; 5 bslbf
                byte ch = (byte)(data.get(0) & 0xf8);
                // 3bits left.

                // samplingFrequencyIndex; 4 bslbf
                byte samplingFrequencyIndex = 0x04;
                switch (sampleRate) {
                    case CodecAudioSampleRate.R22050:
                        samplingFrequencyIndex = 0x07;
                        break;
                    case CodecAudioSampleRate.R11025:
                        samplingFrequencyIndex = 0x0a;
                        break;
                    case CodecAudioSampleRate.R44100:
                        samplingFrequencyIndex = 0x04;
                }
                ch |= (samplingFrequencyIndex >> 1) & 0x07;
                frame[2] = ch;

                ch = (byte)((samplingFrequencyIndex << 7) & 0x80);
                // 7bits left.

                // channelConfiguration; 4 bslbf
                byte channelConfiguration = 1;
                if (sampleRate == 2) {
                    channelConfiguration = 2;
                }
                ch |= (channelConfiguration << 3) & 0x78;
                // 3bits left.

                // GASpecificConfig(), page 451
                // 4.4.1 Decoder configuration (GASpecificConfig)
                // frameLengthFlag; 1 bslbf
                // dependsOnCoreCoder; 1 bslbf
                // extensionFlag; 1 bslbf
                frame[3] = ch;

                aacSpecificConfig = frame;
                aacPacketType = 0; // 0 = AAC sequence header
            } else {
                data.get(frame, 2, frame.length - 2);
            }

            byte soundFormat = 10; // AAC
            byte soundType = 0; // 0 = Mono sound
            if (channel == 2) {
                soundType = 1; // 1 = Stereo sound
            }
            byte soundSize = 1; // 1 = 16-bit samples
            byte soundRate = 3; // 44100, 22050, 11025
            if (sampleRate == 22050) {
                soundRate = 2;
            } else if (sampleRate == 11025) {
                soundRate = 1;
            }

            // for audio frame, there is 1 or 2 bytes header:
            //      1bytes, SoundFormat|SoundRate|SoundSize|SoundType
            //      1bytes, AACPacketType for SoundFormat == 10, 0 is sequence header.
            byte audioHeader = (byte)(soundType & 0x01);
            audioHeader |= (soundSize << 1) & 0x02;
            audioHeader |= (soundRate << 2) & 0x0c;
            audioHeader |= (soundFormat << 4) & 0xf0;

            frame[0] = audioHeader;
            frame[1] = aacPacketType;

            FlvFrameBytes tag = new FlvFrameBytes();
            tag.data = ByteBuffer.wrap(frame);
            tag.size = frame.length;

            rtmpWritePacket(CodecFlvTag.Audio, pts, 0, aacPacketType, tag);
        }

        private void writeVideoSample(final ByteBuffer bb, int dataSize, int pts) throws IllegalArgumentException {

            ArrayList<FlvFrameBytes> ibps = new ArrayList<>();
            int frameType = CodecVideoAVCFrame.InterFrame;

            // send each frame.
            while (bb.position() < dataSize) {
                // 查找开始码,开始始一般为3个字节或者4个字节,3个字节为 00 00 01, 4个字节为 00 00 00 01
                // 查找到开始码之后,需要把开始码忽略掉
                FlvFrameBytes frame = avc.annexbDemux(bb, dataSize);

                // 5bits, 7.3.1 NAL unit syntax,
                // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
                // 7: SPS, 8: PPS, 5: I Frame, 1: P Frame
                int nalUnitType = (frame.data.get(0) & 0x1f);

                // for IDR frame, the frame is keyframe.
                if (nalUnitType == AvcNaluType.IDR) {
                    frameType = CodecVideoAVCFrame.KeyFrame;
                }

                // ignore the nalu type aud(9)
                if (nalUnitType == AvcNaluType.AccessUnitDelimiter) {
                    continue;
                }

                // for sps
                if (avc.isSps(frame)) {
                    if (!frame.data.equals(h264Sps)) {
                        byte[] sps = new byte[frame.size];
                        frame.data.get(sps);
                        h264SpsChanged = true;
                        h264Sps = ByteBuffer.wrap(sps);
                    }
                    continue;
                }

                // for pps
                if (avc.isPps(frame)) {
                    if (!frame.data.equals(h264Pps)) {
                        byte[] pps = new byte[frame.size];
                        frame.data.get(pps);
                        h264PpsChanged = true;
                        h264Pps = ByteBuffer.wrap(pps);
                    }
                    continue;
                }

                FlvFrameBytes bytes = new FlvFrameBytes();
                // 4 + frame.size 为这一帧数据加上 nal_unit_length 4个字节
                bytes.size = 4 + frame.size;

                ByteBuffer data = ByteBuffer.allocate(bytes.size);

                // 每一帧必须先设置unit_length告诉服务器本次数据的长度 4个字节
                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size
                int NAL_unit_length = frame.size;

                // mux the avc NALU in "ISO Base Media File Format"
                // from H.264-AVC-ISO_IEC_14496-15.pdf, page 20
                // NALUnitLength
                data.putInt(NAL_unit_length);

                byte[] frameArray = new byte[frame.size];
                frame.data.get(frameArray, 0, frame.size);
                data.put(frameArray);
                data.rewind();
                bytes.data = data;

                ibps.add(bytes);

            }
            writeH264SpsPps(pts, pts);

            writeH264IpbFrame(ibps, frameType, pts, pts);
        }

        private void writeH264SpsPps(int dts, int pts) {
            // when sps or pps changed, update the sequence header,
            // for the pps maybe not changed while sps changed.
            // so, we must check when each video ts message frame parsed.
            if (h264SpsPpsSent && !h264SpsChanged && !h264PpsChanged) {
                return;
            }

            // when not got sps/pps, wait.
            if (h264Pps == null || h264Sps == null) {
                return;
            }

            // h264 raw to h264 packet.
            ArrayList<FlvFrameBytes> frames = new ArrayList<>();
            avc.muxSequenceHeader(h264Sps, h264Pps, dts, pts, frames);

            // h264 packet to flv packet.
            int frameType = CodecVideoAVCFrame.KeyFrame;
            int avcPacketType = CodecVideoAVCType.SequenceHeader;
            FlvFrameBytes flvTag = avc.muxAvc2flv(frames, frameType, avcPacketType, dts, pts);

            // the timestamp in rtmp message header is dts.
            rtmpWritePacket(CodecFlvTag.Video, dts, frameType, avcPacketType, flvTag);

            // reset sps and pps.
            h264SpsChanged = false;
            h264PpsChanged = false;
            h264SpsPpsSent = true;
        }

        private void writeH264IpbFrame(ArrayList<FlvFrameBytes> ibps, int frame_type, int dts, int pts) {
            // when sps or pps not sent, ignore the packet.
            if (!h264SpsPpsSent) {
                return;
            }

            int avcPacketType = CodecVideoAVCType.NALU;
            FlvFrameBytes flvTag = avc.muxAvc2flv(ibps, frame_type, avcPacketType, dts, pts);

            // the timestamp in rtmp message header is dts.
            rtmpWritePacket(CodecFlvTag.Video, dts, frame_type, avcPacketType, flvTag);

        }

        private void rtmpWritePacket(int type, int dts, int frame_type, int avc_aac_type, FlvFrameBytes tag) {
            FlvFrame frame = new FlvFrame();
            frame.flvTag = ByteBuffer.allocate(tag.size);
            frame.flvTag.put(tag.data.array());
            frame.type = type;
            frame.dts = dts;
            frame.frameType = frame_type;
            frame.avcAacType = avc_aac_type;

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

        private void flvFrameCacheAdd(FlvFrame frame) {
            frameCache.add(frame);
            synchronized (txFrameLock) {
                txFrameLock.notifyAll();
            }
        }
    }
}
