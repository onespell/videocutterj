package net.scriptorium.videocutter.job.execution;

import net.scriptorium.videocutter.job.ShotJob;
import net.scriptorium.videocutter.job.execution.bytedeco.javacv.JavacvUtil;

import java.io.IOException;
import java.nio.file.Path;

public final class ShotJobPerformer {

	public static boolean perform(final ShotJob job, final Path source, final Path resultFile) throws IOException {
		return JavacvUtil.takeShot(job, source, resultFile);
	}

	private ShotJobPerformer() {
		//
	}
}
