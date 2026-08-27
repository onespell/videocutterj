package net.scriptorium.videocutter.media;

import net.scriptorium.videocutter.FrameSize;
import net.scriptorium.videocutter.MediaStream;

import java.util.List;

public record MediaInfo(String format,
						int durationMillis,
						int width,
						int height,
						List<FrameSize> sizes,
						List<Integer> keyFrames,
						MediaStream video,
						List<MediaStream> audio) {
	//
}
