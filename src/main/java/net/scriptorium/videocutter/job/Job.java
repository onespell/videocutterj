package net.scriptorium.videocutter.job;

public sealed interface Job permits ShotJob, ClipJob {

	JobType type();

	int timeMillis();

	String format();

	String toDisplayString();

	String marshall();

	static Job parse(final String line) {
		final String trimmed = line.trim();
		final int frameAt = trimmed.indexOf(ShotJob.PREFIX);
		if (frameAt >= 0) {
			return ShotJob.unmarshall(trimmed.substring(frameAt));
		}
		final int clipAt = trimmed.indexOf(ClipJob.PREFIX);
		if (clipAt >= 0) {
			return ClipJob.unmarshall(trimmed.substring(clipAt));
		}
		throw new IllegalArgumentException(trimmed);
	}
}
