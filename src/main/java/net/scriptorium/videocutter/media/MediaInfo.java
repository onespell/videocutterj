package net.scriptorium.videocutter.media;

import net.scriptorium.videocutter.FrameSize;

import java.util.List;

public class MediaInfo extends ShallowMediaInfo {

	private List<FrameSize> sizes;

	private List<Integer> keyFrames;

	public List<FrameSize> getSizes() {
		return sizes;
	}

	public void setSizes(final List<FrameSize> sizes) {
		this.sizes = sizes;
	}

	public List<Integer> getKeyFrames() {
		return keyFrames;
	}

	public void setKeyFrames(final List<Integer> keyFrames) {
		this.keyFrames = keyFrames;
	}
}
