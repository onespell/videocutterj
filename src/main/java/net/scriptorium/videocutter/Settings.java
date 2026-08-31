package net.scriptorium.videocutter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class Settings {

	private static Settings instance;

	private static List<String> videoFormats;

	private String locale = "en";

	private int numOfProcessors = Math.max(1, Runtime.getRuntime().availableProcessors());

	private Path initialDir = Path.of(System.getProperty("user.home", "/home"));

	private int massTranscodeLimit = 0;

	public static Settings instance() {
		return instance;
	}

	public static Settings load(final Path path) {
		final Properties properties;
		{
			properties = new Properties();
			final File file = path.resolve("settings.properties").toFile();
			if (file.isFile()) {
				try (final InputStream in = new FileInputStream(file)) {
					properties.load(in);
				} catch (final IOException e) {
					//
				}
			}
		}
		return load(properties);
	}

	public static Settings load(final Properties properties) {
		instance = new Settings();
		instance.locale = properties.getProperty("locale", instance.locale).trim().toLowerCase(Locale.ROOT);
		instance.numOfProcessors = parseInt(properties.getProperty("numOfProcessors"), instance.numOfProcessors);
		instance.initialDir = Path.of(properties.getProperty("initialDir", instance.initialDir.toString()));
		instance.massTranscodeLimit = parseInt(properties.getProperty("massTranscode.limit"), instance.massTranscodeLimit);
		return instance;
	}

	public static List<String> imageFormats() {
		return List.of("WEBP", "JPEG", "PNG");
	}

	public static List<String> videoFormats() {
		videoFormats = (videoFormats == null) ? List.of("MP4", "HEVC", "AVI", "MKV", "WMV") : videoFormats;
		return videoFormats;
	}

	public static String[] fileFilterNames() {
		return new String[]{"all", "avi", "mkv", "mp4", "mpeg"};
	}

	public static String[] fileFilterExtensions() {
		return new String[]{"*.*", "*.avi", "*.mkv", "*.mp4", "*.mpg"};
	}

	public static Map<String, List<FrameSize>> aspectRatios() {
		final Map<String, List<FrameSize>> ratios = new LinkedHashMap<>();
		ratios.put("16:9", List.of(
				FrameSize.of(8192, 4608), FrameSize.of(7680, 4320), FrameSize.of(5120, 2880),
				FrameSize.of(3840, 2160), FrameSize.of(3200, 1800), FrameSize.of(3072, 1728),
				FrameSize.of(2880, 1620), FrameSize.of(2560, 1440), FrameSize.of(1920, 1080),
				FrameSize.of(1600, 900), FrameSize.of(1280, 720), FrameSize.of(640, 360)));
		ratios.put("4:3", List.of(
				FrameSize.of(6144, 4608), FrameSize.of(4096, 3072), FrameSize.of(3840, 2880),
				FrameSize.of(3072, 2304), FrameSize.of(2880, 2160), FrameSize.of(2304, 1728),
				FrameSize.of(2160, 1620), FrameSize.of(1440, 1080), FrameSize.of(1280, 960),
				FrameSize.of(1024, 768), FrameSize.of(960, 720)));
		ratios.put("3:2", List.of(FrameSize.of(1080, 720)));
		ratios.put("1:1", List.of(FrameSize.of(1080, 1080), FrameSize.of(720, 720)));
		ratios.put("19:10", List.of(FrameSize.of(4096, 2160), FrameSize.of(2048, 1080), FrameSize.of(1024, 540)));
		ratios.put("16:10", List.of(FrameSize.of(1280, 800), FrameSize.of(1152, 720), FrameSize.of(576, 360)));
		ratios.put("235:100", List.of(
				FrameSize.of(5120, 2178), FrameSize.of(4096, 1642), FrameSize.of(3840, 1634),
				FrameSize.of(2880, 1226), FrameSize.of(2048, 870), FrameSize.of(1920, 816),
				FrameSize.of(1280, 544)));
		ratios.put("239:100", List.of(FrameSize.of(4096, 1716), FrameSize.of(2048, 858), FrameSize.of(1280, 536)));
		return ratios;
	}

	public static List<FrameSize> ratioWildcards() {
		return List.of(
				FrameSize.wildcardHeight(400),
				FrameSize.wildcardHeight(480),
				FrameSize.wildcardHeight(540),
				FrameSize.wildcardHeight(720),
				FrameSize.wildcardWidth(960));
	}

	private static int parseInt(final String value, final int fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (final NumberFormatException e) {
			return fallback;
		}
	}

	private Settings() {
		//
	}

	public String locale() {
		return locale;
	}

	public int numOfProcessors() {
		return numOfProcessors;
	}

	public int mediaThreads() {
		// return Math.max(1, (int) (numOfProcessors * 1.5));
		return Math.max(1, numOfProcessors);
	}

	public int massTranscodeLimit() {
		return (massTranscodeLimit > 0) ? massTranscodeLimit : Integer.MAX_VALUE;
	}

	public Path initialDir() {
		return initialDir;
	}
}
