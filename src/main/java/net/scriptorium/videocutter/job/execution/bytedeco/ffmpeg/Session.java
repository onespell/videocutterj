package net.scriptorium.videocutter.job.execution.bytedeco.ffmpeg;

import net.scriptorium.videocutter.FrameSize;
import net.scriptorium.videocutter.Settings;
import net.scriptorium.videocutter.job.ClipJob;
import net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVAudioFifo;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;

import java.nio.file.Path;
import java.util.Locale;

import static net.scriptorium.videocutter.job.JobUtil.throwIfInterrupted;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.audioCodecName;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_FLAG_GLOBAL_HEADER;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_rescale_ts;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_from_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet;
import static org.bytedeco.ffmpeg.global.avformat.AVFMT_GLOBALHEADER;
import static org.bytedeco.ffmpeg.global.avformat.AVFMT_NOFILE;
import static org.bytedeco.ffmpeg.global.avformat.AVIO_FLAG_WRITE;
import static org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD;
import static org.bytedeco.ffmpeg.global.avformat.av_guess_frame_rate;
import static org.bytedeco.ffmpeg.global.avformat.av_interleaved_write_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_seek_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_write_trailer;
import static org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_free_context;
import static org.bytedeco.ffmpeg.global.avformat.avformat_new_stream;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_write_header;
import static org.bytedeco.ffmpeg.global.avformat.avio_closep;
import static org.bytedeco.ffmpeg.global.avformat.avio_open;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PICTURE_TYPE_NONE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.AV_TIME_BASE;
import static org.bytedeco.ffmpeg.global.avutil.av_audio_fifo_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_audio_fifo_free;
import static org.bytedeco.ffmpeg.global.avutil.av_audio_fifo_read;
import static org.bytedeco.ffmpeg.global.avutil.av_audio_fifo_size;
import static org.bytedeco.ffmpeg.global.avutil.av_audio_fifo_write;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_check;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_compare;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_copy;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_uninit;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_free;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_unref;
import static org.bytedeco.ffmpeg.global.avutil.av_inv_q;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q;
import static org.bytedeco.ffmpeg.global.avutil.av_opt_set_chlayout;
import static org.bytedeco.ffmpeg.global.avutil.av_opt_set_int;
import static org.bytedeco.ffmpeg.global.avutil.av_opt_set_sample_fmt;
import static org.bytedeco.ffmpeg.global.avutil.av_samples_set_silence;
import static org.bytedeco.ffmpeg.global.swresample.swr_alloc;
import static org.bytedeco.ffmpeg.global.swresample.swr_convert;
import static org.bytedeco.ffmpeg.global.swresample.swr_free;
import static org.bytedeco.ffmpeg.global.swresample.swr_get_out_samples;
import static org.bytedeco.ffmpeg.global.swresample.swr_init;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;
import static org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN;

public class Session implements AutoCloseable {

	private static final Logger LOG = LogManager.getLogger(Session.class);

	/**
	 * MP4 fourcc {@code hvc1} for HEVC playback compatibility.
	 */
	private static final int CODEC_TAG_HVC1 = 0x31637668;

	private final AVFormatContext ifmt = new AVFormatContext(null);

	private AVFormatContext ofmt;

	private final AVPacket inPacket = new AVPacket();

	private final AVPacket outPacket = new AVPacket();

	private final AVRational timeBaseQ = new AVRational();

	private boolean inputOpened;

	private boolean headerWritten;

	private boolean pbOpened;

	private int videoInIdx = -1;

	private int audioInIdx = -1;

	private int videoOutIdx = -1;

	private int audioOutIdx = -1;

	private AVCodecContext videoDec;

	private AVCodecContext videoEnc;

	private AVCodecContext audioDec;

	private AVCodecContext audioEnc;

	private AVStream videoIn;

	private AVStream audioIn;

	private AVStream videoOut;

	private AVStream audioOut;

	private AVFrame decFrame;

	private AVFrame videoEncFrame;

	private AVFrame audioEncFrame;

	private AVFrame audioConvFrame;

	private SwsContext sws;

	private SwrContext swr;

	private final PointerPointer<SwrContext> swrPtr = new PointerPointer<>(1L);

	private AVAudioFifo audioFifo;

	private final long[] lastDts = {-1L, -1L};

	private final boolean[] hasLastDts = new boolean[2];

	private long lastVideoPts = -1L;

	private long audioPts;

	private long startUs;

	private long endUs;

	private boolean videoPastEnd;

	private boolean audioEncFlushed;

	private static void freeFrame(final AVFrame frame) {
		if (frame != null && !frame.isNull()) {
			av_frame_free(frame);
		}
	}

	private static void freeCodec(final AVCodecContext ctx) {
		if (ctx != null && !ctx.isNull()) {
			avcodec_free_context(ctx);
		}
	}

	private static int threadCount() {
		if (Settings.instance() == null) {
			return Math.max(1, Runtime.getRuntime().availableProcessors());
		}
		return Settings.instance().mediaThreads();
	}

	private int audioFrameSize() {
		return Math.max(audioEnc.frame_size(), 1024);
	}

	private static void ensureValidChannelLayout(
			final AVChannelLayout layout,
			final AVChannelLayout fallback,
			final int channelCount,
			final int streamIndex) {
		if (layout == null || layout.isNull()) {
			return;
		}
		if (av_channel_layout_check(layout) >= 0) {
			return;
		}
		final int channels = Math.max(channelCount, 1);
		LOG.warn("repairing invalid audio channel layout: stream={}, nb_channels={}", streamIndex, channels);
		av_channel_layout_uninit(layout);
		if (fallback != null && !fallback.isNull() && av_channel_layout_check(fallback) >= 0) {
			if (av_channel_layout_copy(layout, fallback) >= 0) {
				return;
			}
			av_channel_layout_uninit(layout);
		}
		av_channel_layout_default(layout, channels);
	}

	private boolean openSwrContext() {
		swr = swr_alloc();
		if (swr == null || swr.isNull()) {
			return false;
		}
		swrPtr.put(0, swr);
		final AVChannelLayout inLayout = new AVChannelLayout();
		final AVChannelLayout outLayout = new AVChannelLayout();
		try {
			inLayout.zero();
			outLayout.zero();
			if (av_channel_layout_copy(outLayout, audioEnc.ch_layout()) < 0
					|| av_channel_layout_copy(inLayout, audioDec.ch_layout()) < 0) {
				return false;
			}
			if (av_opt_set_chlayout(swr, "out_chlayout", outLayout, 0) < 0
					|| av_opt_set_chlayout(swr, "in_chlayout", inLayout, 0) < 0
					|| av_opt_set_sample_fmt(swr, "out_sample_fmt", audioEnc.sample_fmt(), 0) < 0
					|| av_opt_set_sample_fmt(swr, "in_sample_fmt", audioDec.sample_fmt(), 0) < 0
					|| av_opt_set_int(swr, "out_sample_rate", audioEnc.sample_rate(), 0) < 0
					|| av_opt_set_int(swr, "in_sample_rate", audioDec.sample_rate(), 0) < 0) {
				return false;
			}
			return true;
		} finally {
			av_channel_layout_uninit(inLayout);
			av_channel_layout_uninit(outLayout);
		}
	}

	private static int pickSampleFmt(final AVCodec codec) {
		final IntPointer fmts = codec.sample_fmts();
		if (fmts == null || fmts.isNull()) {
			return AV_SAMPLE_FMT_FLTP;
		}
		int first = -1;
		for (int i = 0; ; i++) {
			final int f = fmts.get(i);
			if (f == -1) {
				break;
			}
			if (first < 0) {
				first = f;
			}
			if (f == AV_SAMPLE_FMT_FLTP) {
				return f;
			}
		}
		return first >= 0 ? first : AV_SAMPLE_FMT_FLTP;
	}

	boolean run(
			final ClipJob job,
			final Path source,
			final Path resultFile,
			final long startUs,
			final long endUs) throws InterruptedException {
		this.startUs = startUs;
		this.endUs = endUs;
		timeBaseQ.num(1);
		timeBaseQ.den(AV_TIME_BASE);

		final boolean mute = job.audio() != null && job.audio().isNoSound();
		final Integer videoIdx = BytedecoUtil.parseStreamId(job.video() == null ? null : job.video().id());
		final Integer audioIdx = mute ? null : BytedecoUtil.parseStreamId(
				job.audio() == null ? null : job.audio().id());

		if (avformat_open_input(ifmt, source.toAbsolutePath().toString(), null, null) < 0) {
			return false;
		}
		inputOpened = true;
		if (avformat_find_stream_info(ifmt, (AVDictionary) null) < 0) {
			return false;
		}

		final int nb = ifmt.nb_streams();
		boolean haveVideo = false;
		boolean haveAudio = false;
		for (int i = 0; i < nb; i++) {
			final AVStream inStream = ifmt.streams(i);
			if (inStream == null || inStream.isNull() || inStream.codecpar() == null) {
				continue;
			}
			final int type = inStream.codecpar().codec_type();
			if (type == AVMEDIA_TYPE_VIDEO) {
				if (videoIdx != null && videoIdx != i) {
					continue;
				}
				if (videoIdx == null && haveVideo) {
					continue;
				}
				videoInIdx = i;
				videoIn = inStream;
				haveVideo = true;
			} else if (type == AVMEDIA_TYPE_AUDIO) {
				if (mute) {
					continue;
				}
				if (audioIdx != null && audioIdx != i) {
					continue;
				}
				if (audioIdx == null && haveAudio) {
					continue;
				}
				audioInIdx = i;
				audioIn = inStream;
				haveAudio = true;
			}
		}
		if (!haveVideo || videoIn == null) {
			return false;
		}

		ofmt = new AVFormatContext(null);
		if (avformat_alloc_output_context2(ofmt, null, BytedecoUtil.muxerName(job.format()),
				resultFile.toAbsolutePath().toString()) < 0) {
			return false;
		}

		decFrame = av_frame_alloc();
		if (decFrame == null || decFrame.isNull()) {
			return false;
		}

		if (!openVideo(job)) {
			LOG.warn("openVideo failed");
			return false;
		}
		if (haveAudio && !openAudio(job)) {
			LOG.warn("openAudio failed");
			return false;
		}

		if ((ofmt.oformat().flags() & AVFMT_NOFILE) == 0) {
			final AVIOContext pb = new AVIOContext(null);
			if (avio_open(pb, resultFile.toAbsolutePath().toString(), AVIO_FLAG_WRITE) < 0) {
				return false;
			}
			ofmt.pb(pb);
			pbOpened = true;
		}
		if (avformat_write_header(ofmt, (AVDictionary) null) < 0) {
			return false;
		}
		headerWritten = true;

		av_seek_frame(ifmt, -1, startUs, AVSEEK_FLAG_BACKWARD);

		while (av_read_frame(ifmt, inPacket) >= 0) {
			throwIfInterrupted();
			try {
				final int inIdx = inPacket.stream_index();
				if (inIdx == videoInIdx) {
					if (!decodeVideo(inPacket)) {
						return false;
					}
					if (videoPastEnd) {
						break;
					}
				} else if (inIdx == audioInIdx) {
					if (!decodeAudio(inPacket)) {
						return false;
					}
				}
			} finally {
				av_packet_unref(inPacket);
			}
		}

		if (!decodeVideo(null) || !flushVideoEncoder()) {
			return false;
		}
		if (audioEnc != null) {
			if (!decodeAudio(null) || !flushAudioFifo() || !flushAudioEncoder()) {
				return false;
			}
		}

		av_write_trailer(ofmt);
		headerWritten = false;
		return true;
	}

	private boolean openVideo(final ClipJob job) {
		final AVCodec decoder = avcodec_find_decoder(videoIn.codecpar().codec_id());
		if (decoder == null || decoder.isNull()) {
			return false;
		}
		videoDec = avcodec_alloc_context3(decoder);
		if (videoDec == null || videoDec.isNull()) {
			return false;
		}
		if (avcodec_parameters_to_context(videoDec, videoIn.codecpar()) < 0) {
			return false;
		}
		videoDec.pkt_timebase(videoIn.time_base());
		videoDec.thread_count(threadCount());
		if (avcodec_open2(videoDec, decoder, (AVDictionary) null) < 0) {
			return false;
		}

		final int srcW = Math.max(videoDec.width() > 0 ? videoDec.width() : videoIn.codecpar().width(), 1);
		final int srcH = Math.max(videoDec.height() > 0 ? videoDec.height() : videoIn.codecpar().height(), 1);
		final FrameSize size = job.size();
		int outW = size == null ? srcW : size.resolveWidth(srcW, srcH);
		int outH = size == null ? srcH : size.resolveHeight(srcW, srcH);
		outW = Math.max(2, outW & ~1);
		outH = Math.max(2, outH & ~1);

		final String fmt = job.format() == null ? "" : job.format().toLowerCase(Locale.ROOT);
		final String codecName = EncodeSettings.videoCodecName(fmt);
		final AVCodec encoder = avcodec_find_encoder_by_name(codecName);
		if (encoder == null || encoder.isNull()) {
			LOG.warn("video encoder not found: {}", codecName);
			return false;
		}
		videoEnc = avcodec_alloc_context3(encoder);
		if (videoEnc == null || videoEnc.isNull()) {
			return false;
		}
		videoEnc.codec_id(encoder.id());
		videoEnc.codec_type(AVMEDIA_TYPE_VIDEO);
		videoEnc.width(outW);
		videoEnc.height(outH);
		videoEnc.pix_fmt(AV_PIX_FMT_YUV420P);
		if (!"hevc".equals(fmt)) {
			videoEnc.thread_count(threadCount());
		}
		AVRational fps = av_guess_frame_rate(ifmt, videoIn, null);
		if (fps == null || fps.isNull() || fps.num() <= 0 || fps.den() <= 0) {
			fps = videoIn.avg_frame_rate();
		}
		if (fps == null || fps.isNull() || fps.num() <= 0 || fps.den() <= 0) {
			fps = new AVRational();
			fps.num(25);
			fps.den(1);
		}
		final AVRational timeBase = av_inv_q(fps);
		videoEnc.time_base(timeBase);
		videoEnc.framerate(fps);
		if (videoIn.codecpar().sample_aspect_ratio() != null
				&& videoIn.codecpar().sample_aspect_ratio().num() > 0) {
			videoEnc.sample_aspect_ratio(videoIn.codecpar().sample_aspect_ratio());
		}
		if ("wmv".equals(fmt)) {
			videoEnc.bit_rate(EncodeSettings.wmvVideoBitrate(outW, outH));
		} else if ("hevc".equals(fmt)) {
			videoEnc.bit_rate(0);
		}
		if ((ofmt.oformat().flags() & AVFMT_GLOBALHEADER) != 0) {
			videoEnc.flags(videoEnc.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
		}

		final AVDictionary options = new AVDictionary(null);
		EncodeSettings.applyVideoDictionaryOptions(fmt, videoIn.codecpar().bit_rate(), outW, outH, options);
		if (avcodec_open2(videoEnc, encoder, options) < 0) {
			LOG.warn("avcodec_open2 failed for {} {}x{} format={}", codecName, outW, outH, fmt);
			av_dict_free(options);
			return false;
		}
		av_dict_free(options);

		videoOut = avformat_new_stream(ofmt, null);
		if (videoOut == null || videoOut.isNull()) {
			return false;
		}
		if (avcodec_parameters_from_context(videoOut.codecpar(), videoEnc) < 0) {
			return false;
		}
		if ("hevc".equals(fmt)) {
			videoOut.codecpar().codec_tag(CODEC_TAG_HVC1);
		} else {
			videoOut.codecpar().codec_tag(0);
		}
		videoOut.time_base(videoEnc.time_base());
		videoOut.avg_frame_rate(fps);
		videoOutIdx = 0;

		videoEncFrame = av_frame_alloc();
		if (videoEncFrame == null || videoEncFrame.isNull()) {
			return false;
		}
		videoEncFrame.format(AV_PIX_FMT_YUV420P);
		videoEncFrame.width(outW);
		videoEncFrame.height(outH);
		if (av_frame_get_buffer(videoEncFrame, 32) < 0) {
			LOG.warn("openVideo: av_frame_get_buffer failed for {}x{}", outW, outH);
			return false;
		}
		return true;
	}

	private boolean openAudio(final ClipJob job) {
		final AVCodec decoder = avcodec_find_decoder(audioIn.codecpar().codec_id());
		if (decoder == null || decoder.isNull()) {
			LOG.warn("openAudio: decoder not found for codec_id={}", audioIn.codecpar().codec_id());
			return false;
		}
		audioDec = avcodec_alloc_context3(decoder);
		if (audioDec == null || audioDec.isNull()) {
			return false;
		}
		if (avcodec_parameters_to_context(audioDec, audioIn.codecpar()) < 0) {
			return false;
		}
		audioDec.pkt_timebase(audioIn.time_base());
		ensureValidChannelLayout(
				audioDec.ch_layout(),
				audioIn.codecpar().ch_layout(),
				Math.max(audioIn.codecpar().ch_layout().nb_channels(), 1),
				audioInIdx);
		if (avcodec_open2(audioDec, decoder, (AVDictionary) null) < 0) {
			LOG.warn("openAudio: avcodec_open2 decoder failed");
			return false;
		}

		final String fmt = job.format() == null ? "" : job.format().toLowerCase(Locale.ROOT);
		final String codecName = audioCodecName(fmt);
		final AVCodec encoder = avcodec_find_encoder_by_name(codecName);
		if (encoder == null || encoder.isNull()) {
			return false;
		}
		audioEnc = avcodec_alloc_context3(encoder);
		if (audioEnc == null || audioEnc.isNull()) {
			return false;
		}
		audioEnc.codec_id(encoder.id());
		audioEnc.codec_type(AVMEDIA_TYPE_AUDIO);
		final int sampleRate = audioDec.sample_rate() > 0 ? audioDec.sample_rate() : 48000;
		audioEnc.sample_rate(sampleRate);
		audioEnc.sample_fmt(pickSampleFmt(encoder));
		av_channel_layout_default(audioEnc.ch_layout(),
				Math.max(audioDec.ch_layout().nb_channels(), 1));
		ensureValidChannelLayout(
				audioEnc.ch_layout(),
				audioDec.ch_layout(),
				Math.max(audioDec.ch_layout().nb_channels(), 1),
				audioInIdx);
		audioEnc.bit_rate(EncodeSettings.AUDIO_BITRATE);
		final AVRational tb = new AVRational();
		tb.num(1);
		tb.den(sampleRate);
		audioEnc.time_base(tb);
		if ((ofmt.oformat().flags() & AVFMT_GLOBALHEADER) != 0) {
			audioEnc.flags(audioEnc.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
		}
		if (avcodec_open2(audioEnc, encoder, (AVDictionary) null) < 0) {
			LOG.warn("openAudio: avcodec_open2 encoder failed for {}", codecName);
			return false;
		}

		audioOut = avformat_new_stream(ofmt, null);
		if (audioOut == null || audioOut.isNull()) {
			return false;
		}
		if (avcodec_parameters_from_context(audioOut.codecpar(), audioEnc) < 0) {
			return false;
		}
		audioOut.codecpar().codec_tag(0);
		audioOut.time_base(audioEnc.time_base());
		audioOutIdx = videoOutIdx >= 0 ? 1 : 0;

		final boolean resample = audioDec.sample_fmt() != audioEnc.sample_fmt()
				|| audioDec.sample_rate() != audioEnc.sample_rate()
				|| av_channel_layout_compare(audioDec.ch_layout(), audioEnc.ch_layout()) != 0;
		if (resample) {
			if (!openSwrContext()) {
				LOG.warn("openAudio: openSwrContext failed");
				return false;
			}
			if (swr_init(swr) < 0) {
				return false;
			}
			audioConvFrame = av_frame_alloc();
			if (audioConvFrame == null || audioConvFrame.isNull()) {
				return false;
			}
		}

		final int frameSize = audioFrameSize();
		audioFifo = av_audio_fifo_alloc(audioEnc.sample_fmt(), audioEnc.ch_layout().nb_channels(), frameSize * 8);
		if (audioFifo == null || audioFifo.isNull()) {
			return false;
		}
		audioEncFrame = av_frame_alloc();
		if (audioEncFrame == null || audioEncFrame.isNull()) {
			return false;
		}
		audioEncFrame.nb_samples(frameSize);
		audioEncFrame.format(audioEnc.sample_fmt());
		audioEncFrame.ch_layout(audioEnc.ch_layout());
		audioEncFrame.sample_rate(audioEnc.sample_rate());
		if (av_frame_get_buffer(audioEncFrame, 0) < 0) {
			LOG.warn("openAudio: av_frame_get_buffer failed for audio enc frame");
			return false;
		}
		return true;
	}

	private boolean decodeVideo(final AVPacket packet) {
		if (videoDec == null) {
			return true;
		}
		if (avcodec_send_packet(videoDec, packet) < 0) {
			return packet == null;
		}
		while (true) {
			av_frame_unref(decFrame);
			final int ret = avcodec_receive_frame(videoDec, decFrame);
			if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
				return true;
			}
			if (ret < 0) {
				return false;
			}
			if (!encodeDecodedVideo()) {
				return false;
			}
			if (videoPastEnd) {
				return true;
			}
		}
	}

	private boolean encodeDecodedVideo() {
		if (decFrame.width() <= 0 || decFrame.height() <= 0) {
			return true;
		}
		long pts = decFrame.best_effort_timestamp();
		if (pts == AV_NOPTS_VALUE) {
			pts = decFrame.pts();
		}
		if (pts == AV_NOPTS_VALUE) {
			return true;
		}
		final long frameUs = av_rescale_q(pts, videoIn.time_base(), timeBaseQ);
		if (frameUs < startUs) {
			return true;
		}
		if (frameUs > endUs) {
			videoPastEnd = true;
			return true;
		}
		long outPts = av_rescale_q(frameUs - startUs, timeBaseQ, videoEnc.time_base());
		if (lastVideoPts >= 0 && outPts <= lastVideoPts) {
			outPts = lastVideoPts + 1;
		}
		lastVideoPts = outPts;

		final AVFrame toEncode;
		if (needsScale(decFrame)) {
			if (sws == null) {
				sws = sws_getContext(
						decFrame.width(), decFrame.height(), decFrame.format(),
						videoEnc.width(), videoEnc.height(), videoEnc.pix_fmt(),
						SWS_BILINEAR, null, null, (DoublePointer) null);
				if (sws == null || sws.isNull()) {
					return false;
				}
			}
			if (av_frame_make_writable(videoEncFrame) < 0) {
				return false;
			}
			sws_scale(sws, decFrame.data(), decFrame.linesize(), 0, decFrame.height(),
					videoEncFrame.data(), videoEncFrame.linesize());
			videoEncFrame.pts(outPts);
			videoEncFrame.pict_type(AV_PICTURE_TYPE_NONE);
			toEncode = videoEncFrame;
		} else {
			decFrame.pts(outPts);
			decFrame.pict_type(AV_PICTURE_TYPE_NONE);
			toEncode = decFrame;
		}
		return sendVideoFrame(toEncode);
	}

	private boolean needsScale(final AVFrame frame) {
		return frame.width() != videoEnc.width()
				|| frame.height() != videoEnc.height()
				|| frame.format() != videoEnc.pix_fmt();
	}

	private boolean sendVideoFrame(final AVFrame frame) {
		if (avcodec_send_frame(videoEnc, frame) < 0) {
			return false;
		}
		return drainVideoPackets();
	}

	private boolean flushVideoEncoder() {
		avcodec_send_frame(videoEnc, null);
		return drainVideoPackets();
	}

	private boolean drainVideoPackets() {
		while (true) {
			av_packet_unref(outPacket);
			final int ret = avcodec_receive_packet(videoEnc, outPacket);
			if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
				return true;
			}
			if (ret < 0) {
				return false;
			}
			if (!writePacket(outPacket, videoEnc, videoOut, videoOutIdx)) {
				return false;
			}
		}
	}

	private boolean decodeAudio(final AVPacket packet) {
		if (audioDec == null) {
			return true;
		}
		final int sendRet = avcodec_send_packet(audioDec, packet);
		if (sendRet < 0) {
			if (packet == null) {
				return true;
			}
			LOG.warn("avcodec_send_packet audio skipped: ret={}", sendRet);
			return true;
		}
		while (true) {
			av_frame_unref(decFrame);
			final int ret = avcodec_receive_frame(audioDec, decFrame);
			if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
				return true;
			}
			if (ret < 0) {
				return false;
			}
			if (!queueDecodedAudio()) {
				return false;
			}
		}
	}

	private boolean queueDecodedAudio() {
		if (decFrame.nb_samples() <= 0) {
			return true;
		}
		long pts = decFrame.best_effort_timestamp();
		if (pts == AV_NOPTS_VALUE) {
			pts = decFrame.pts();
		}
		if (pts != AV_NOPTS_VALUE) {
			final long frameUs = av_rescale_q(pts, audioIn.time_base(), timeBaseQ);
			if (frameUs < startUs || frameUs > endUs) {
				return true;
			}
		}
		final AVFrame src;
		if (swr != null) {
			final int outSamples = Math.max(swr_get_out_samples(swr, decFrame.nb_samples()), 1);
			av_frame_unref(audioConvFrame);
			audioConvFrame.nb_samples(outSamples);
			audioConvFrame.format(audioEnc.sample_fmt());
			audioConvFrame.ch_layout(audioEnc.ch_layout());
			audioConvFrame.sample_rate(audioEnc.sample_rate());
			if (av_frame_get_buffer(audioConvFrame, 0) < 0) {
				return false;
			}
			final int converted = swr_convert(swr, audioConvFrame.data(), outSamples,
					decFrame.data(), decFrame.nb_samples());
			if (converted < 0) {
				return false;
			}
			audioConvFrame.nb_samples(converted);
			src = audioConvFrame;
		} else {
			src = decFrame;
		}
		if (src.nb_samples() <= 0) {
			return true;
		}
		if (av_audio_fifo_write(audioFifo, src.data(), src.nb_samples()) < src.nb_samples()) {
			return false;
		}
		return drainAudioFifo(false);
	}

	private boolean drainAudioFifo(final boolean flush) {
		final int frameSize = audioFrameSize();
		while (av_audio_fifo_size(audioFifo) >= frameSize
				|| (flush && av_audio_fifo_size(audioFifo) > 0)) {
			final int available = av_audio_fifo_size(audioFifo);
			final int nb = Math.min(frameSize, available);
			final int encodeSamples = flush && nb < frameSize ? frameSize : nb;
			if (av_frame_make_writable(audioEncFrame) < 0) {
				return false;
			}
			audioEncFrame.nb_samples(encodeSamples);
			if (av_audio_fifo_read(audioFifo, audioEncFrame.data(), nb) < nb) {
				return false;
			}
			if (encodeSamples > nb) {
				if (av_samples_set_silence(audioEncFrame.data(), nb, encodeSamples - nb,
						audioEnc.ch_layout().nb_channels(), audioEnc.sample_fmt()) < 0) {
					return false;
				}
			}
			audioEncFrame.pts(audioPts);
			audioPts += nb;
			if (avcodec_send_frame(audioEnc, audioEncFrame) < 0) {
				return false;
			}
			if (!drainAudioPackets()) {
				return false;
			}
		}
		return true;
	}

	private boolean flushAudioFifo() {
		if (swr != null) {
			final int delayed = Math.max(swr_get_out_samples(swr, 0), 0);
			if (delayed > 0) {
				av_frame_unref(audioConvFrame);
				audioConvFrame.nb_samples(delayed);
				audioConvFrame.format(audioEnc.sample_fmt());
				audioConvFrame.ch_layout(audioEnc.ch_layout());
				audioConvFrame.sample_rate(audioEnc.sample_rate());
				if (av_frame_get_buffer(audioConvFrame, 0) < 0) {
					return false;
				}
				final int converted = swr_convert(swr, audioConvFrame.data(), delayed, null, 0);
				if (converted > 0) {
					audioConvFrame.nb_samples(converted);
					if (av_audio_fifo_write(audioFifo, audioConvFrame.data(), converted) < converted) {
						return false;
					}
				}
			}
		}
		return drainAudioFifo(true);
	}

	private boolean flushAudioEncoder() {
		avcodec_send_frame(audioEnc, null);
		while (true) {
			av_packet_unref(outPacket);
			final int ret = avcodec_receive_packet(audioEnc, outPacket);
			if (ret == AVERROR_EOF()) {
				audioEncFlushed = true;
				return true;
			}
			if (ret == AVERROR_EAGAIN()) {
				audioEncFlushed = true;
				return true;
			}
			if (ret < 0) {
				return false;
			}
			if (!writePacket(outPacket, audioEnc, audioOut, audioOutIdx)) {
				return false;
			}
		}
	}

	private boolean drainAudioPackets() {
		while (true) {
			av_packet_unref(outPacket);
			final int ret = avcodec_receive_packet(audioEnc, outPacket);
			if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
				return true;
			}
			if (ret < 0) {
				return false;
			}
			if (!writePacket(outPacket, audioEnc, audioOut, audioOutIdx)) {
				return false;
			}
		}
	}

	private void flushAudioEncoderBestEffort() {
		if (audioEnc == null || audioEnc.isNull() || audioEncFlushed || outPacket.isNull()) {
			return;
		}
		try {
			avcodec_send_frame(audioEnc, null);
			while (true) {
				av_packet_unref(outPacket);
				final int ret = avcodec_receive_packet(audioEnc, outPacket);
				if (ret == AVERROR_EOF() || ret == AVERROR_EAGAIN()) {
					audioEncFlushed = true;
					return;
				}
				if (ret < 0) {
					audioEncFlushed = true;
					return;
				}
				try {
					writePacket(outPacket, audioEnc, audioOut, audioOutIdx);
				} catch (final Exception ignored) {
					//
				}
			}
		} catch (final Exception ignored) {
			audioEncFlushed = true;
		}
	}

	private boolean writePacket(
			final AVPacket packet,
			final AVCodecContext enc,
			final AVStream outStream,
			final int outIdx) {
		av_packet_rescale_ts(packet, enc.time_base(), outStream.time_base());
		packet.stream_index(outIdx);
		packet.pos(-1);
		if (packet.dts() != AV_NOPTS_VALUE) {
			if (hasLastDts[outIdx] && packet.dts() <= lastDts[outIdx]) {
				final long fixed = lastDts[outIdx] + 1;
				if (packet.pts() != AV_NOPTS_VALUE && packet.pts() < fixed) {
					packet.pts(fixed);
				}
				packet.dts(fixed);
			}
			lastDts[outIdx] = packet.dts();
			hasLastDts[outIdx] = true;
		} else if (packet.pts() != AV_NOPTS_VALUE) {
			if (hasLastDts[outIdx] && packet.pts() <= lastDts[outIdx]) {
				packet.pts(lastDts[outIdx] + 1);
			}
			lastDts[outIdx] = packet.pts();
			hasLastDts[outIdx] = true;
		}
		return av_interleaved_write_frame(ofmt, packet) >= 0;
	}

	@Override
	public void close() {
		if (headerWritten && ofmt != null && !ofmt.isNull()) {
			if (audioEnc != null && !audioEnc.isNull()) {
				flushAudioEncoderBestEffort();
			}
			try {
				av_write_trailer(ofmt);
			} catch (final Exception ignored) {
				//
			}
			headerWritten = false;
		}
		timeBaseQ.deallocate();
		if (audioFifo != null && !audioFifo.isNull()) {
			av_audio_fifo_free(audioFifo);
		}
		if (swr != null && !swr.isNull()) {
			swr_free(swrPtr);
		}
		swrPtr.put(0, null);
		swrPtr.deallocate();
		if (sws != null && !sws.isNull()) {
			sws_freeContext(sws);
		}
		freeFrame(decFrame);
		freeFrame(videoEncFrame);
		freeFrame(audioEncFrame);
		freeFrame(audioConvFrame);
		freeCodec(videoDec);
		freeCodec(videoEnc);
		freeCodec(audioDec);
		freeCodec(audioEnc);
		if (ofmt != null && !ofmt.isNull()) {
			if (pbOpened && ofmt.pb() != null && !ofmt.pb().isNull()) {
				avio_closep(ofmt.pb());
			}
			avformat_free_context(ofmt);
		}
		if (inputOpened) {
			avformat_close_input(ifmt);
		}
		inPacket.close();
		outPacket.close();
	}
}
