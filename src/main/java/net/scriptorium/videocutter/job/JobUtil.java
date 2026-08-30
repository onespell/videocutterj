package net.scriptorium.videocutter.job;

import net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings;

import java.nio.file.Files;
import java.nio.file.Path;

public final class JobUtil {

	public static void throwIfInterrupted() throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}
	}

	public static Path next(final Path source, final String format, final int numOfJobs) {
		final String prefix = stripExtension(source.toString());
		final String ext = EncodeSettings.outputExtension(format);
		final int width = Math.max(2, Integer.toString(numOfJobs).length());
		final String suffix = "_%0" + width + "d." + ext;
		int i = 1;
		while (true) {
			final Path candidate = Path.of(prefix + String.format(suffix, i));
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

	private JobUtil() {
		//
	}
}
