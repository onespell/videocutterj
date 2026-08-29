package net.scriptorium.videocutter.job.execution.bytedeco;

import org.bytedeco.ffmpeg.avutil.AVDictionary;

import java.util.Locale;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;

/**
 * Shared encode parameters for bytedeco native ({@code Session}) and JavaCV ({@code JavacvUtil}) transcode paths.
 */
public final class EncodeSettings {

	public static final int CRF_VP9 = 30;

	public static final int CRF_H264_FALLBACK = 21;

	public static final int CRF_HEVC_FALLBACK = 26;

	public static final int AUDIO_BITRATE = 64_000;

	public static final String PRESET = "medium";

	public static String videoCodecName(final String format) {
		final String fmt = normalizeFormat(format);
		return switch (fmt) {
			case "webm" -> "libvpx-vp9";
			case "wmv" -> "wmv2";
			case "hevc" -> "libx265";
			default -> "libx264";
		};
	}

	public static String audioCodecName(final String format) {
		return "wmv".equals(normalizeFormat(format)) ? "wmav2" : "aac";
	}

	public static String normalizeFormat(final String format) {
		return format == null ? "" : format.toLowerCase(Locale.ROOT);
	}

	public static String containerFormat(final String format) {
		final String fmt = normalizeFormat(format);
		return "hevc".equals(fmt) ? "mp4" : fmt;
	}

	public static String outputExtension(final String format) {
		return "hevc".equals(normalizeFormat(format)) ? "mp4" : normalizeFormat(format);
	}

	public static int referenceBitrate(final int width, final int height) {
		final long pixels = (long) width * height;
		if (pixels <= 640L * 360) {
			return 1_000_000;
		}
		if (pixels <= 1280L * 720) {
			return 2_500_000;
		}
		if (pixels <= 1920L * 1080) {
			return 5_000_000;
		}
		return 8_000_000;
	}

	public static int adaptiveH264Crf(final long sourceBitrate, final int outWidth, final int outHeight) {
		if (sourceBitrate <= 0) {
			return CRF_H264_FALLBACK;
		}
		final double ratio = (double) sourceBitrate / referenceBitrate(outWidth, outHeight);
		if (ratio <= 1.0) {
			return 20;
		}
		if (ratio <= 2.0) {
			return 22;
		}
		return 23;
	}

	public static int adaptiveHevcCrf(final long sourceBitrate, final int outWidth, final int outHeight) {
		if (sourceBitrate <= 0) {
			return CRF_HEVC_FALLBACK;
		}
		final double ratio = (double) sourceBitrate / referenceBitrate(outWidth, outHeight);
		if (ratio <= 1.0) {
			return 24;
		}
		if (ratio <= 2.0) {
			return 26;
		}
		return 28;
	}

	public static String x265Params(final long sourceBitrate, final int outWidth, final int outHeight) {
		return "crf=" + adaptiveHevcCrf(sourceBitrate, outWidth, outHeight);
	}

	public static int wmvVideoBitrate(final int width, final int height) {
		final long pixels = (long) width * height;
		if (pixels <= 640L * 360) {
			return 1_000_000;
		}
		if (pixels <= 1280L * 720) {
			return 2_000_000;
		}
		if (pixels <= 1920L * 1080) {
			return 3_500_000;
		}
		return 5_000_000;
	}

	public static void applyVideoDictionaryOptions(
			final String format,
			final long sourceBitrate,
			final int outWidth,
			final int outHeight,
			final AVDictionary options) {
		final String fmt = normalizeFormat(format);
		if ("webm".equals(fmt)) {
			av_dict_set(options, "crf", Integer.toString(CRF_VP9), 0);
			av_dict_set(options, "deadline", "good", 0);
			av_dict_set(options, "cpu-used", "2", 0);
		} else if ("hevc".equals(fmt)) {
			av_dict_set(options, "preset", PRESET, 0);
			av_dict_set(options, "x265-params", x265Params(sourceBitrate, outWidth, outHeight), 0);
		} else if (!"wmv".equals(fmt)) {
			av_dict_set(options, "crf", Integer.toString(adaptiveH264Crf(sourceBitrate, outWidth, outHeight)), 0);
			av_dict_set(options, "preset", PRESET, 0);
		}
	}

	public static void applyJavacvVideoOptions(
			final String format,
			final long sourceBitrate,
			final int outWidth,
			final int outHeight,
			final VideoOptionSetter options) throws Exception {
		final String fmt = normalizeFormat(format);
		if ("hevc".equals(fmt)) {
			options.setVideoCodec(AV_CODEC_ID_HEVC);
			options.setVideoBitrate(0);
			options.setVideoOption("preset", PRESET);
			options.setVideoOption("x265-params", x265Params(sourceBitrate, outWidth, outHeight));
		} else {
			options.setVideoCodecName(videoCodecName(fmt));
		}
		if ("webm".equals(fmt)) {
			options.setVideoOption("crf", Integer.toString(CRF_VP9));
			options.setVideoOption("deadline", "good");
			options.setVideoOption("cpu-used", "2");
		} else if ("wmv".equals(fmt)) {
			options.setVideoBitrate(wmvVideoBitrate(outWidth, outHeight));
		} else if ("hevc".equals(fmt)) {
			// x265-params and codec set above
		} else {
			options.setVideoOption("crf", Integer.toString(adaptiveH264Crf(sourceBitrate, outWidth, outHeight)));
			options.setVideoOption("preset", PRESET);
		}
	}

	public interface VideoOptionSetter {
		void setVideoCodecName(String codec) throws Exception;

		void setVideoCodec(int codecId) throws Exception;

		void setVideoOption(String key, String value) throws Exception;

		void setVideoBitrate(int bitrate) throws Exception;
	}

	private EncodeSettings() {
		//
	}
}
