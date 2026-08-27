package net.scriptorium.videocutter.job;

import net.scriptorium.videocutter.FrameSize;
import net.scriptorium.videocutter.MediaStream;
import net.scriptorium.videocutter.TimeUtil;

public record ClipJob(
		int timeMillis,
		int finishMillis,
		String format,
		FrameSize size,
		MediaStream video,
		MediaStream audio
) implements Job {

	public static final String PREFIX = "clip";

	private static final String VIDEO_PREFIX = "v:";

	private static final String AUDIO_PREFIX = "a:";

	@Override
	public JobType type() {
		return JobType.CLIP;
	}

	@Override
	public String toDisplayString() {
		final StringBuilder result = new StringBuilder();
		result.append('[').append(TimeUtil.toTimeCode(timeMillis)).append('-')
				.append(TimeUtil.toTimeCode(finishMillis)).append(']');
		if (size != null) {
			result.append(' ').append(size);
		}
		if (audio != null) {
			result.append(" a:");
			result.append(audio.isNoSound() ? "no" : audio.id());
		}
		result.append(' ').append(format);
		return result.toString();
	}

	@Override
	public String marshall() {
		final StringBuilder result = new StringBuilder();
		result.append(PREFIX).append(' ').append(TimeUtil.toTimeCode(timeMillis)).append('-')
				.append(TimeUtil.toTimeCode(finishMillis));
		if (size != null) {
			result.append(' ').append(size);
		}
		result.append(' ').append(VIDEO_PREFIX).append(video == null ? "" : video.id());
		if (audio != null) {
			result.append(' ').append(AUDIO_PREFIX);
			result.append(audio.isNoSound() ? "no" : audio.id());
		}
		result.append(' ').append(format);
		return result.toString();
	}

	public static ClipJob unmarshall(final String str) {
		int p = PREFIX.length();
		int q = str.indexOf('-', p);
		final int start = TimeUtil.fromTimeCode(str.substring(p + 1, q).trim());
		p = q + 1;
		q = str.indexOf(' ', p);
		final int finish = TimeUtil.fromTimeCode(str.substring(p, q).trim());
		final String[] split = str.substring(q + 1).trim().split(" ");
		MediaStream video = null;
		MediaStream audio = null;
		FrameSize size = null;
		for (int i = 0; i < split.length - 1; i++) {
			final String chunk = split[i];
			if (chunk.startsWith(VIDEO_PREFIX)) {
				final String videoId = chunk.substring(VIDEO_PREFIX.length()).trim();
				video = MediaStream.video(videoId, "video " + videoId);
			} else if (chunk.startsWith(AUDIO_PREFIX)) {
				final String audioId = chunk.substring(AUDIO_PREFIX.length()).trim();
				audio = MediaStream.audio("no".equals(audioId) ? "" : audioId, "audio " + audioId);
			} else {
				final int x = chunk.indexOf('x');
				size = FrameSize.of(chunk.substring(0, x).trim(), chunk.substring(x + 1).trim());
			}
		}
		final String format = split[split.length - 1];
		return new ClipJob(start, finish, format, size, video, audio);
	}
}
