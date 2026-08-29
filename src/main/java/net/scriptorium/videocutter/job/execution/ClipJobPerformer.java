package net.scriptorium.videocutter.job.execution;

import net.scriptorium.videocutter.job.ClipJob;
import net.scriptorium.videocutter.job.execution.bytedeco.ffmpeg.BytedecoUtil;
import net.scriptorium.videocutter.job.execution.bytedeco.javacv.JavacvUtil;

import java.nio.file.Path;
import java.util.Locale;

final class ClipJobPerformer {

	public static boolean perform(final ClipJob job, final Path source, final Path resultFile) throws Exception {
		final long startUs = job.timeMillis() * 1000L;
		final long endUs = job.finishMillis() * 1000L;
		if (endUs <= startUs) {
			return false;
		}
		if (!needsReencode(job)) {
			if (FfmpegCli.available() && FfmpegCli.remux(job, source, resultFile)) {
				return true;
			}
			if (BytedecoUtil.remux(job, source, resultFile, startUs, endUs)) {
				return true;
			}
			// Incompatible container/codec for remux — fall back to decode/encode.
		}
		if (BytedecoUtil.transcode(job, source, resultFile, startUs, endUs)) {
			return true;
		}
		return JavacvUtil.transcode(job, source, resultFile, startUs, endUs);
	}

	/**
	 * Stream copy when no resize and container supports remux. Mute drops audio with {@code -an} (still copy, no
	 * decode). Resize, WEBM, WMV, and HEVC always reencode (bytedeco native, JavaCV fallback).
	 */
	private static boolean needsReencode(final ClipJob job) {
		if (job.size() != null) {
			return true;
		}
		final String fmt = job.format() == null ? "" : job.format().toLowerCase(Locale.ROOT);
		return "webm".equals(fmt) || "wmv".equals(fmt) || "hevc".equals(fmt);
	}

	private ClipJobPerformer() {
		//
	}
}
