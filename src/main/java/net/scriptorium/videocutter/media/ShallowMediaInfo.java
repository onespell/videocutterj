package net.scriptorium.videocutter.media;

import net.scriptorium.videocutter.MediaStream;

import java.util.List;

public class ShallowMediaInfo {

	private String format;

	private int durationMillis;

	private int width;

	private int height;

	private MediaStream video;

	private List<MediaStream> audio;

	public ShallowMediaInfo() {
		//
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(final String format) {
		this.format = format;
	}

	public int getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(final int durationMillis) {
		this.durationMillis = durationMillis;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(final int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(final int height) {
		this.height = height;
	}

	public MediaStream getVideo() {
		return video;
	}

	public void setVideo(final MediaStream video) {
		this.video = video;
	}

	public List<MediaStream> getAudio() {
		return audio;
	}

	public void setAudio(final List<MediaStream> audio) {
		this.audio = audio;
	}
}
