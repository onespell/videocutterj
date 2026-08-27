package net.scriptorium.videocutter.media;

import net.scriptorium.videocutter.FrameSize;
import net.scriptorium.videocutter.L10n;
import net.scriptorium.videocutter.MediaStream;
import net.scriptorium.videocutter.Settings;
import net.scriptorium.videocutter.TimeUtil;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avformat.av_find_best_stream;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_get;
import static org.bytedeco.ffmpeg.global.avutil.av_q2d;

public final class Analysis {

	public static MediaInfo inspect(final Path file) throws IOException {
		final String format = formatOf(file);
		try (final FFmpegFrameGrabber grabber = openGrabber(file)) {
			final int width = Math.max(grabber.getImageWidth(), 1);
			final int height = Math.max(grabber.getImageHeight(), 1);
			final String ratio = aspectRatio(width, height);
			final List<FrameSize> sizes = buildSizes(width, height, ratio);
			final int duration = durationMillis(grabber);
			final Streams streams = mediaStreams(grabber);
			final MediaStream video = streams.video().isEmpty() ? null : streams.video().get(0);
			final List<Integer> keyFrames = keyFrames(file);
			return new MediaInfo(format, duration, width, height, sizes, keyFrames, video, streams.audio());
		} catch (final FrameGrabber.Exception e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	public static FFmpegFrameGrabber openGrabber(final Path file) throws FrameGrabber.Exception {
		final FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.toFile());
		grabber.setOption("analyzeduration", "100000000");
		grabber.setOption("probesize", "100000000");
		grabber.setVideoOption("threads", Integer.toString(Math.max(1, Settings.instance().mediaThreads())));
		grabber.setAudioOption("threads", "1");
		grabber.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24);
		grabber.start();
		return grabber;
	}

	static String formatOf(final Path file) {
		final String name = file.getFileName().toString();
		final int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return "";
		}
		return name.substring(dot + 1).toUpperCase(Locale.ROOT);
	}

	private static String aspectRatio(final int width, final int height) {
		final int g = TimeUtil.gcd(width, height);
		return (width / g) + ":" + (height / g);
	}

	private static List<FrameSize> buildSizes(final int width, final int height, final String ratio) {
		final List<FrameSize> result = new ArrayList<>();
		result.add(FrameSize.of(width, height));
		for (final FrameSize alternative : Settings.aspectRatios().getOrDefault(ratio, List.of())) {
			if (width > alternative.widthValue()) {
				result.add(alternative);
			}
		}
		for (final FrameSize alternative : Settings.ratioWildcards()) {
			if (alternative.isWildcardWidth()) {
				if (height > alternative.heightValue()) {
					result.add(alternative);
				}
			} else if (width > alternative.widthValue()) {
				result.add(alternative);
			}
		}
		return result;
	}

	static int durationMillis(final FFmpegFrameGrabber grabber) {
		final long micros = grabber.getLengthInTime();
		if (micros <= 0) {
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE, micros / 1000L);
	}

	private static Streams mediaStreams(final FFmpegFrameGrabber grabber) {
		final List<MediaStream> video = new ArrayList<>();
		final List<MediaStream> audio = new ArrayList<>();
		final AVFormatContext oc = grabber.getFormatContext();
		if (oc != null && !oc.isNull()) {
			final int nb = oc.nb_streams();
			for (int i = 0; i < nb; i++) {
				final AVStream st = oc.streams(i);
				if (st == null || st.isNull() || st.codecpar() == null) {
					continue;
				}
				final int type = st.codecpar().codec_type();
				final String caption = streamCaption(i, type, st.metadata());
				if (type == AVMEDIA_TYPE_VIDEO) {
					video.add(MediaStream.video(Integer.toString(i), caption));
				} else if (type == AVMEDIA_TYPE_AUDIO) {
					audio.add(MediaStream.audio(Integer.toString(i), caption));
				}
			}
		}
		if (video.isEmpty() && grabber.getImageWidth() > 0) {
			video.add(MediaStream.video("0", "video 0"));
		}
		if (audio.isEmpty() && grabber.getAudioChannels() > 0) {
			audio.add(MediaStream.audio("1", "audio 1"));
		}
		return new Streams(video, audio);
	}

	private static String streamCaption(final int id, final int type, final AVDictionary metadata) {
		final StringBuilder result = new StringBuilder();
		if (type == AVMEDIA_TYPE_AUDIO) {
			result.append("audio ");
		} else if (type == AVMEDIA_TYPE_VIDEO) {
			result.append("video ");
		}
		result.append(id);
		final String lang = dict(metadata, "language");
		if (lang != null && !"und".equals(lang) && !lang.isBlank()) {
			result.append(' ').append(lang);
		}
		final String title = dict(metadata, "title");
		if (title != null && !title.isBlank()) {
			result.append(' ').append(title);
		}
		return result.toString();
	}

	private static String dict(final AVDictionary metadata, final String key) {
		if (metadata == null || metadata.isNull()) {
			return null;
		}
		final AVDictionaryEntry entry = av_dict_get(metadata, key, null, 0);
		if (entry == null || entry.isNull() || entry.value() == null) {
			return null;
		}
		return entry.value().getString();
	}

	static List<Integer> keyFrames(final Path file) throws IOException {
		final List<Integer> pts = readKeyFrames(file, true);
		if (!pts.isEmpty()) {
			return pts;
		}
		return readKeyFrames(file, false);
	}

	private static List<Integer> readKeyFrames(final Path file, final boolean preferPts) throws IOException {
		final AVFormatContext fmt = new AVFormatContext(null);
		final AVPacket packet = new AVPacket();
		final List<Integer> result = new ArrayList<>();
		boolean opened = false;
		try {
			if (avformat_open_input(fmt, file.toString(), null, null) < 0) {
				throw new IOException("cannot open " + file);
			}
			opened = true;
			if (avformat_find_stream_info(fmt, (AVDictionary) null) < 0) {
				throw new IOException("cannot find stream info: " + file);
			}
			final int video = av_find_best_stream(fmt, AVMEDIA_TYPE_VIDEO, -1, -1, (org.bytedeco.ffmpeg.avcodec.AVCodec) null, 0);
			if (video < 0) {
				return List.of();
			}
			final AVStream stream = fmt.streams(video);
			while (av_read_frame(fmt, packet) >= 0) {
				try {
					if (packet.stream_index() != video) {
						continue;
					}
					if ((packet.flags() & AV_PKT_FLAG_KEY) == 0) {
						continue;
					}
					long ts = preferPts ? packet.pts() : packet.dts();
					if (ts == AV_NOPTS_VALUE) {
						ts = preferPts ? packet.dts() : packet.pts();
					}
					if (ts == AV_NOPTS_VALUE || ts < 0) {
						continue;
					}
					final double seconds = ts * av_q2d(stream.time_base());
					result.add(TimeUtil.toMillis(seconds));
				} finally {
					av_packet_unref(packet);
				}
			}
		} finally {
			packet.close();
			if (opened) {
				avformat_close_input(fmt);
			}
		}
		return TimeUtil.normalizeKeyFrames(result);
	}

	public static String noSoundCaption() {
		return L10n.t("noSound");
	}

	private Analysis() {
		//
	}
}
