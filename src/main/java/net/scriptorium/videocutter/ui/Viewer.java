package net.scriptorium.videocutter.ui;

import net.scriptorium.videocutter.media.FrameSink;
import net.scriptorium.videocutter.media.Player;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

final class Viewer implements FrameSink {

	final Composite frame;

	final Canvas video;

	final MediaBar mediaBar;

	private Image currentImage;

	private int sourceWidth = 640;

	private int sourceHeight = 360;

	Viewer(final Composite parent, final Icons icons, final Player player, final Runnable onTimeChanged) {
		frame = new Composite(parent, SWT.NONE);
		frame.setLayout(new GridLayout(1, false));
		{
			video = new Canvas(frame, SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED | SWT.BORDER);
			video.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_BLACK));
			final GridData gridData;
			{
				gridData = new GridData(SWT.CENTER, SWT.CENTER, true, true);
				gridData.widthHint = 640;
				gridData.heightHint = 360;
			}
			video.setLayoutData(gridData);
			video.addPaintListener(this::paintVideo);
		}
		{
			mediaBar = new MediaBar(frame, icons, player, onTimeChanged);
			mediaBar.frame.setLayoutData(new GridData(SWT.FILL, SWT.END, true, false));
		}
		frame.addDisposeListener(e -> {
			if (currentImage != null && !currentImage.isDisposed()) {
				currentImage.dispose();
			}
		});
	}

	private void paintVideo(final PaintEvent e) {
		final Rectangle area = video.getClientArea();
		e.gc.setBackground(video.getBackground());
		e.gc.fillRectangle(area);
		if (currentImage == null || currentImage.isDisposed()) {
			return;
		}
		final Rectangle bounds = currentImage.getBounds();
		final double scale = Math.min(1.0, Math.min(area.width / (double) Math.max(bounds.width, 1),
				area.height / (double) Math.max(bounds.height, 1)));
		final int w = Math.max(1, (int) Math.round(bounds.width * scale));
		final int h = Math.max(1, (int) Math.round(bounds.height * scale));
		final int x = area.x + (area.width - w) / 2;
		final int y = area.y + (area.height - h) / 2;
		e.gc.drawImage(currentImage, 0, 0, bounds.width, bounds.height, x, y, w, h);
	}

	@Override
	public void present(final ImageData imageData, final int width, final int height) {
		if (video.isDisposed()) {
			return;
		}
		final Image next = new Image(video.getDisplay(), imageData);
		final Image old = currentImage;
		currentImage = next;
		sourceWidth = width;
		sourceHeight = height;
		if (old != null && !old.isDisposed()) {
			old.dispose();
		}
		video.redraw();
	}

	@Override
	public void clear() {
		if (video.isDisposed()) {
			return;
		}
		final Image old = currentImage;
		currentImage = null;
		if (old != null && !old.isDisposed()) {
			old.dispose();
		}
		video.redraw();
	}

	void setSize(final int width, final int height) {
		sourceWidth = width;
		sourceHeight = height;
		final Rectangle screen = frame.getDisplay().getPrimaryMonitor().getClientArea();
		final int maxW = Math.max(320, (int) (screen.width * 0.7));
		final int maxH = Math.max(180, (int) (screen.height * 0.6));
		final double scale = Math.min(1.0, Math.min(
				maxW / (double) Math.max(width, 1), maxH / (double) Math.max(height, 1)));
		final int w = Math.max(160, (int) Math.round(width * scale));
		final int h = Math.max(90, (int) Math.round(height * scale));
		final GridData data = (GridData) video.getLayoutData();
		data.widthHint = w;
		data.heightHint = h;
		data.minimumWidth = w;
		data.minimumHeight = h;
		frame.layout(true, true);
		frame.getShell().layout(true, true);
	}
}
