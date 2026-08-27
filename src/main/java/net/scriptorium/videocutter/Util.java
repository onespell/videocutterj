package net.scriptorium.videocutter;

import java.nio.file.Path;
import java.security.CodeSource;

public class Util {

	public static Path getPath(final Class cls) {
		Path result;
		try {
			final CodeSource source = cls.getProtectionDomain().getCodeSource();
			if (source != null && source.getLocation() != null) {
				Path path = Path.of(source.getLocation().toURI());
				result = path.toString().endsWith(".jar") ? path.getParent() : path;
			} else {
				result = Path.of(".").toAbsolutePath();
			}
		} catch (Exception e) {
			result = Path.of(".").toAbsolutePath();
		}
		return result;
	}
}
