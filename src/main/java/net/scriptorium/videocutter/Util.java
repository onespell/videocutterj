package net.scriptorium.videocutter;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;

public class Util {

	public static Path getPathArgument(final String[] args) {
		String s = null;
		for (final String arg : args) {
			if (!arg.startsWith("-")) {
				s = arg;
			}
		}
		return StringUtils.isBlank(s) ? null : Path.of(s).toAbsolutePath();
	}
}
