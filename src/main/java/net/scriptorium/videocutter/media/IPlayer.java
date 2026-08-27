package net.scriptorium.videocutter.media;

public interface IPlayer {

	void open(String filePath) throws Exception;

	void play();

	void pause();

	boolean isPaused();

	int goTo(int millis);

	void close();
}
