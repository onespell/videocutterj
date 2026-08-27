package net.scriptorium.videocutter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class OutputFiles {

	public static Path next(final Path source, final String format, final int numOfJobs) {
		final String root = stripExtension(source.toString());
		final String ext = format.toLowerCase(Locale.ROOT);
		final int width = Math.max(2, Integer.toString(numOfJobs).length());
		final String pattern = root + "_%0" + width + "d." + ext;
		int i = 1;
		while (true) {
			final Path candidate = Path.of(String.format(pattern, i));
			if (!Files.exists(candidate)) {
				return candidate;
			}
			i++;
		}
	}

	private static String stripExtension(final String filePath) {
		final int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
		final int dot = filePath.lastIndexOf('.');
		if (dot > slash) {
			return filePath.substring(0, dot);
		}
		return filePath;
	}

	private OutputFiles() {
		//
	}
}
