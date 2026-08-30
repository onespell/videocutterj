package net.scriptorium.videocutter;

import net.scriptorium.videocutter.util.FileUtil;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

public class Util {

	public static void init() {
		final Path confPath = getConfPath();
		final Path log4j = confPath.resolve("log4j2.xml");
		if (Files.isRegularFile(log4j)) {
			System.setProperty("log4j2.configurationFile", log4j.toUri().toString());
		}
		Settings.load(confPath);
	}

	public static Path getConfPath() {
		final Path appDir = FileUtil.getPath(Videocutter.class).getParent();
		return appDir.resolve("conf");
	}

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
