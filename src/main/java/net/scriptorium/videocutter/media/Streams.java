package net.scriptorium.videocutter.media;

import net.scriptorium.videocutter.MediaStream;

import java.util.List;

public record Streams(List<MediaStream> video, List<MediaStream> audio) {
	//
}
