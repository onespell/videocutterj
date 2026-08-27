package net.scriptorium.videocutter.job.execution.bytedeco;

import java.util.Locale;

/**
 * Shared encode parameters for bytedeco native ({@code Session}) and JavaCV ({@code JavacvUtil}) transcode paths.
 */
public final class EncodeSettings {

	public static final int CRF_H264 = 18;

	public static final int CRF_VP9 = 30;

	public static final int WMV_VIDEO_BITRATE = 4_000_000;

	public static final int AUDIO_BITRATE = 64_000;

	public static final String PRESET_REENCODE = "medium";

	public static final String PRESET_FAST = "veryfast";

	public static String videoCodecName(final String format) {
		final String fmt = normalizeFormat(format);
		return switch (fmt) {
			case "webm" -> "libvpx-vp9";
			case "wmv" -> "wmv2";
			default -> "libx264";
		};
	}

	public static String audioCodecName(final String format) {
		return "wmv".equals(normalizeFormat(format)) ? "wmav2" : "aac";
	}

	public static String normalizeFormat(final String format) {
		return format == null ? "" : format.toLowerCase(Locale.ROOT);
	}

	private EncodeSettings() {
		//
	}
}
