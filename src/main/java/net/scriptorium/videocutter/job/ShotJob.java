package net.scriptorium.videocutter.job;

import net.scriptorium.videocutter.TimeUtil;

public record ShotJob(int timeMillis, String format) implements Job {
 
	public static final String PREFIX = "frame";

	@Override
	public JobType type() {
		return JobType.SHOT;
	}

	@Override
	public String toDisplayString() {
		return "<" + TimeUtil.toTimeCode(timeMillis) + "> " + format;
	}

	@Override
	public String marshall() {
		return PREFIX + " " + TimeUtil.toTimeCode(timeMillis) + " " + format;
	}

	public static ShotJob unmarshall(final String str) {
		final int p = str.lastIndexOf(' ');
		final String timeCode = str.substring(PREFIX.length(), p).trim();
		final String format = str.substring(p + 1).trim();
		return new ShotJob(TimeUtil.fromTimeCode(timeCode), format);
	}
}
