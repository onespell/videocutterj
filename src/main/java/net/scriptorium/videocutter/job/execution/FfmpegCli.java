package net.scriptorium.videocutter.job.execution;

import net.scriptorium.videocutter.TimeUtil;
import net.scriptorium.videocutter.job.ClipJob;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Invokes a system {@code ffmpeg} binary for stream-copy cuts. stdout/stderr are discarded (no log files).
 */
public final class FfmpegCli {

	private static volatile String cachedBinary;

	public static boolean available() {
		return resolveBinary().isPresent();
	}

	public static boolean remux(
			final ClipJob job,
			final Path source,
			final Path resultFile) throws IOException, InterruptedException {
		return run(streamCopyArgs(job, source, resultFile));
	}

	private static List<String> streamCopyArgs(final ClipJob job, final Path source, final Path resultFile) {
		final String start = TimeUtil.toTimeCode(job.timeMillis());
		final String end = TimeUtil.toTimeCode(job.finishMillis());
		final boolean mute = job.audio() != null && job.audio().isNoSound();
		final List<String> args = new ArrayList<>();
		args.add("-hide_banner");
		args.add("-y");
		args.add("-ss");
		args.add(start);
		args.add("-to");
		args.add(end);
		args.add("-i");
		args.add(source.toAbsolutePath().toString());
		appendMaps(args, job, mute);
		args.add("-c");
		args.add("copy");
		args.add("-avoid_negative_ts");
		args.add("make_zero");
		args.add(resultFile.toAbsolutePath().toString());
		return args;
	}

	private static void appendMaps(final List<String> args, final ClipJob job, final boolean mute) {
		if (job.video() != null && job.video().id() != null && !job.video().id().isBlank()) {
			args.add("-map");
			args.add("0:" + job.video().id());
		} else {
			args.add("-map");
			args.add("0:v:0");
		}
		if (mute) {
			args.add("-an");
			return;
		}
		if (job.audio() != null && job.audio().id() != null && !job.audio().id().isBlank()) {
			args.add("-map");
			args.add("0:" + job.audio().id());
		} else {
			args.add("-map");
			args.add("0:a:0?");
		}
	}

	private static boolean run(final List<String> args) throws IOException, InterruptedException {
		final Optional<String> binary = resolveBinary();
		if (binary.isEmpty()) {
			return false;
		}
		final List<String> command = new ArrayList<>(args.size() + 1);
		command.add(binary.get());
		command.addAll(args);

		final ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
		pb.redirectError(ProcessBuilder.Redirect.DISCARD);
		final Process process = pb.start();
		while (!process.waitFor(1, TimeUnit.SECONDS)) {
			if (Thread.interrupted()) {
				return false;
			}
		}
		return process.exitValue() == 0;
	}

	private static Optional<String> resolveBinary() {
		final String cached = cachedBinary;
		if (cached != null) {
			return cached.isEmpty() ? Optional.empty() : Optional.of(cached);
		}
		final String fromEnv = System.getenv("FFMPEG");
		if (fromEnv != null && !fromEnv.isBlank() && isExecutable(fromEnv.trim())) {
			cachedBinary = fromEnv.trim();
			return Optional.of(cachedBinary);
		}
		final String fromPath = findOnPath("ffmpeg");
		cachedBinary = fromPath == null ? "" : fromPath;
		return fromPath == null ? Optional.empty() : Optional.of(fromPath);
	}

	private static String findOnPath(final String name) {
		final String pathEnv = System.getenv("PATH");
		if (pathEnv == null || pathEnv.isBlank()) {
			return null;
		}
		for (final String dir : pathEnv.split(java.io.File.pathSeparator)) {
			if (dir == null || dir.isBlank()) {
				continue;
			}
			final Path candidate = Path.of(dir.trim(), name);
			if (isExecutable(candidate.toString())) {
				return candidate.toString();
			}
			final Path withExt = Path.of(dir.trim(), name + ".exe");
			if (isExecutable(withExt.toString())) {
				return withExt.toString();
			}
		}
		return null;
	}

	private static boolean isExecutable(final String path) {
		try {
			final Path p = Path.of(path);
			return Files.isRegularFile(p) && Files.isExecutable(p);
		} catch (final Exception e) {
			return false;
		}
	}

	private FfmpegCli() {
		//
	}
}
