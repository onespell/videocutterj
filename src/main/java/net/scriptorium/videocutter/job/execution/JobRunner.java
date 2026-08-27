package net.scriptorium.videocutter.job.execution;

import net.scriptorium.videocutter.OutputFiles;
import net.scriptorium.videocutter.job.ClipJob;
import net.scriptorium.videocutter.job.Job;
import net.scriptorium.videocutter.job.ShotJob;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class JobRunner {

	public static List<Job> sorted(final List<Job> jobs) {
		return jobs.stream().sorted(Comparator.comparingInt(Job::timeMillis)).toList();
	}

	public static String dryRun(final Job job, final Path source, final int numOfJobs) {
		if (job instanceof final ShotJob shot) {
			final Path out = OutputFiles.next(source, shot.format(), numOfJobs);
			return ShotJobPerformer.describe(shot, source, out);
		}
		if (job instanceof final ClipJob clip) {
			final Path out = OutputFiles.next(source, clip.format(), numOfJobs);
			return ClipJobPerformer.describe(clip, source, out);
		}
		return "";
	}

	public static boolean execute(final Job job, final Path source, final int numOfJobs) throws Exception {
		if (job instanceof final ShotJob shot) {
			final Path out = OutputFiles.next(source, shot.format(), numOfJobs);
			return ShotJobPerformer.perform(shot, source, out);
		}
		if (job instanceof final ClipJob clip) {
			final Path out = OutputFiles.next(source, clip.format(), numOfJobs);
			return ClipJobPerformer.perform(clip, source, out);
		}
		return false;
	}

	private JobRunner() {
		//
	}
}
