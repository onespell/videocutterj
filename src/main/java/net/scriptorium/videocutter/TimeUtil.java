package net.scriptorium.videocutter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TimeUtil {
 
	public static final int MS_IN_SECOND = 1000;

	public static final int MS_IN_MINUTE = 60 * MS_IN_SECOND;

	public static final int MS_IN_HOUR = 60 * MS_IN_MINUTE;

	private TimeUtil() {
	}

	public static int toMillis(final String seconds) {
		final String s = seconds.trim();
		final int dot = s.indexOf('.');
		if (dot < 0) {
			return Integer.parseInt(s) * MS_IN_SECOND;
		}
		final int whole = Integer.parseInt(s.substring(0, dot));
		String frac = s.substring(dot + 1);
		if (frac.length() > 3) {
			frac = frac.substring(0, 3);
		}
		while (frac.length() < 3) {
			frac = frac + "0";
		}
		return whole * MS_IN_SECOND + Integer.parseInt(frac);
	}

	public static int toMillis(final double seconds) {
		return (int) Math.round(seconds * MS_IN_SECOND);
	}

	public static String toTimeCode(final int ms) {
		if (ms <= 0) {
			return "00:00:00.000";
		}
		final int hour = ms / MS_IN_HOUR;
		int rem = ms % MS_IN_HOUR;
		final int minute = rem / MS_IN_MINUTE;
		rem = rem % MS_IN_MINUTE;
		final int second = rem / MS_IN_SECOND;
		final int millis = rem % MS_IN_SECOND;
		return String.format("%02d:%02d:%02d.%03d", hour, minute, second, millis);
	}

	public static int fromTimeCode(final String t) {
		if (t == null || t.length() != 12) {
			throw new IllegalArgumentException("invalid timecode: " + t);
		}
		final int hour = Integer.parseInt(t.substring(0, 2));
		final int minute = Integer.parseInt(t.substring(3, 5));
		final int second = Integer.parseInt(t.substring(6, 8));
		final int millis = Integer.parseInt(t.substring(9));
		return hour * MS_IN_HOUR + minute * MS_IN_MINUTE + second * MS_IN_SECOND + millis;
	}

	public static boolean isNotValid(final int[] keyFrames) {
		int prev = -1;
		for (final int f : keyFrames) {
			if (f <= prev) {
				return true;
			}
			prev = f;
		}
		return false;
	}

	/**
	 * Sort ascending and drop duplicate millisecond timestamps.
	 */
	public static List<Integer> normalizeKeyFrames(final List<Integer> keyFrames) {
		if (keyFrames == null || keyFrames.isEmpty()) {
			return List.of();
		}
		final List<Integer> sorted = new ArrayList<>(keyFrames);
		Collections.sort(sorted);
		final List<Integer> unique = new ArrayList<>(sorted.size());
		int prev = -1;
		for (final int f : sorted) {
			if (f > prev) {
				unique.add(f);
				prev = f;
			}
		}
		return unique;
	}

	public static int gcd(int x, int y) {
		x = Math.abs(x);
		y = Math.abs(y);
		while (y != 0) {
			final int t = x % y;
			x = y;
			y = t;
		}
		return x == 0 ? 1 : x;
	}
}
