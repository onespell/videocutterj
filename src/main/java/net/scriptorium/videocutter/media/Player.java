package net.scriptorium.videocutter.media;

import org.eclipse.swt.widgets.Display;

import java.util.function.IntConsumer;

public final class Player {

	private final IPlayer player;

	public Player(final Display display, final FrameSink frameSink, final IntConsumer timeSink) {
		this.player = new JavacvPlayer(display, frameSink, timeSink);
	}

	public void load(final String filePath) throws Exception {
		player.open(filePath);
	}

	public boolean isPaused() {
		return player.isPaused();
	}

	public void pause() {
		player.pause();
	}

	public void play() {
		player.play();
	}

	public int goTo(final int millis) {
		return player.goTo(millis);
	}

	public void closeSession() {
		player.close();
	}
}
