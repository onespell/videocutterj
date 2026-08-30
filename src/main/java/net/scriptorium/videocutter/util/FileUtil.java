package net.scriptorium.videocutter.util;

import java.io.File;
import java.nio.file.Path;
import java.security.CodeSource;

public class FileUtil {

	public static Path getPath(final Class cls) {
		Path result;
		try {
			final CodeSource source = cls.getProtectionDomain().getCodeSource();
			if (source != null && source.getLocation() != null) {
				final Path path = Path.of(source.getLocation().toURI());
				result = path.toString().endsWith(".jar") ? path.getParent() : path;
			} else {
				result = Path.of(".").toAbsolutePath();
			}
		} catch (final Exception e) {
			result = Path.of(".").toAbsolutePath();
		}
		return result;
	}

	public static void delete(final Path path) {
		if (path == null) {
			return;
		}
		final File file = path.toFile();
		if (file.isFile()) {
			file.delete();
		}
	}
}
