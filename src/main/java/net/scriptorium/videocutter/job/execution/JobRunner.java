package net.scriptorium.videocutter.job.execution;

import net.scriptorium.videocutter.UncheckedException;
import net.scriptorium.videocutter.job.ClipJob;
import net.scriptorium.videocutter.job.Job;
import net.scriptorium.videocutter.job.ShotJob;
import net.scriptorium.videocutter.util.FileUtil;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static net.scriptorium.videocutter.job.JobUtil.next;
import static net.scriptorium.videocutter.job.JobUtil.throwIfInterrupted;

public final class JobRunner {

	public static List<Job> sorted(final List<Job> jobs) {
		return jobs.stream().sorted(Comparator.comparingInt(Job::timeMillis)).toList();
	}

	public static boolean execute(final Job job, final Path source, final int numOfJobs) throws Exception {
		throwIfInterrupted();
		Path out = null;
		try {
			final boolean result;
			if (job instanceof final ShotJob shot) {
				out = next(source, shot.format(), numOfJobs);
				result = ShotJobPerformer.perform(shot, source, out);
			} else if (job instanceof final ClipJob clip) {
				out = next(source, clip.format(), numOfJobs);
				result = ClipJobPerformer.perform(clip, source, out);
			} else {
				result = false;
			}
			return result;
		} catch (final Throwable t) {
			FileUtil.delete(out);
			throw new UncheckedException(t);
		}
	}

	private JobRunner() {
		//
	}
}
