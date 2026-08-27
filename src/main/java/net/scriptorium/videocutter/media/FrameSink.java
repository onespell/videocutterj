package net.scriptorium.videocutter.media;

import org.eclipse.swt.graphics.ImageData;

public interface FrameSink {

	void present(ImageData imageData, int sourceWidth, int sourceHeight);

	void clear();
}
