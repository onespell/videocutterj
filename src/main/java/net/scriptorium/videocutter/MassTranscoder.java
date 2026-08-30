package net.scriptorium.videocutter;

import net.scriptorium.videocutter.job.ClipJob;
import net.scriptorium.videocutter.job.execution.ClipJobPerformer;
import net.scriptorium.videocutter.media.Analysis;
import net.scriptorium.videocutter.media.ShallowMediaInfo;
import net.scriptorium.videocutter.util.FileUtil;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static net.scriptorium.videocutter.job.JobUtil.next;

public class MassTranscoder {

	public static void main(final String[] args) throws Exception {
		final Path path = Util.getPathArgument(args);
		if (path == null || !path.toFile().isDirectory()) {
			System.out.println("target directory is not specified");
			return;
		}
		final Path log = path.resolve("mass-transcode.log");
		final Set<String> processed;
		if (log.toFile().isFile()) {
			processed = new HashSet<>(Files.readAllLines(log));
		} else {
			processed = Collections.emptySet();
		}
		final FileVisitor<Path> visitor = new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult visitFile(final Path filePath, final BasicFileAttributes attrs) throws IOException {
				final String filePathStr = filePath.toString();
				if (!attrs.isSymbolicLink() && !processed.contains(filePathStr) && isVideo(filePath)) {
					final ShallowMediaInfo info = Analysis.shallowMediaInfo(filePath);
					final int finishMillis = info.getDurationMillis();
					final String format = "MP4".equals(info.getFormat()) ? "HEVC" : info.getFormat();
					final FrameSize size = new FrameSize(String.valueOf(info.getWidth()), String.valueOf(info.getHeight()));
					final MediaStream video = info.getVideo();
					final ClipJob job = new ClipJob(0, finishMillis, format, size, video, null);
					final Path out = next(filePath, job.format(), 1);
					if (ClipJobPerformer.perform(job, filePath, out)) {
						final long srcFileSize = Files.size(filePath);
						final long outFileSize = Files.size(out);
						if (outFileSize < srcFileSize) {
							Files.move(out, filePath, StandardCopyOption.REPLACE_EXISTING);
						} else {
							FileUtil.delete(out);
						}
						Files.write(log, (System.lineSeparator() + filePathStr).getBytes(), StandardOpenOption.APPEND);
					} else {
						FileUtil.delete(out);
					}
				}
				return FileVisitResult.CONTINUE;
			}
		};
		Files.walkFileTree(path, visitor);
	}

	public static boolean isVideo(final Path path) {
		final String format = Analysis.formatOf(path);
		return Settings.videoFormats().stream().filter(x -> x.equals(format)).findAny().isPresent();
	}
}
