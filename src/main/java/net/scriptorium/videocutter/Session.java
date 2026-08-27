package net.scriptorium.videocutter;

import java.nio.file.Path;

public final class Session {

	private Path workingDir;

	private Path filePath;

	public Session(final Path workingDir) {
		this.workingDir = workingDir;
	}

	public Path workingDir() {
		return workingDir;
	}

	public void setWorkingDir(final Path workingDir) {
		this.workingDir = workingDir;
	}

	public Path filePath() {
		return filePath;
	}

	public void setFilePath(final Path filePath) {
		this.filePath = filePath;
	}
}
