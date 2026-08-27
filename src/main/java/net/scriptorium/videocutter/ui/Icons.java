package net.scriptorium.videocutter.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

final class Icons {

	final Image play;

	final Image pause;

	final Image rewind;

	final Image forward;

	final Image refineLeft;

	final Image refineRight;

	private static Image triangle(final Display display, final boolean pointRight) {
		final Image image = new Image(display, 24, 24);
		final GC gc = new GC(image);
		fillTransparent(gc, display);
		gc.setAntialias(SWT.ON);
		gc.setBackground(display.getSystemColor(SWT.COLOR_BLACK));
		if (pointRight) {
			gc.fillPolygon(new int[]{5, 4, 20, 12, 5, 20});
		} else {
			gc.fillPolygon(new int[]{19, 4, 4, 12, 19, 20});
		}
		gc.dispose();
		return image;
	}

	private static Image pause(final Display display) {
		final Image image = new Image(display, 24, 24);
		final GC gc = new GC(image);
		fillTransparent(gc, display);
		gc.setBackground(display.getSystemColor(SWT.COLOR_BLACK));
		gc.fillRectangle(5, 4, 5, 16);
		gc.fillRectangle(14, 4, 5, 16);
		gc.dispose();
		return image;
	}

	private static Image doubleTriangle(final Display display, final boolean forward) {
		final Image image = new Image(display, 24, 24);
		final GC gc = new GC(image);
		fillTransparent(gc, display);
		gc.setAntialias(SWT.ON);
		gc.setBackground(display.getSystemColor(SWT.COLOR_BLACK));
		if (forward) {
			gc.fillPolygon(new int[]{3, 5, 12, 12, 3, 19});
			gc.fillPolygon(new int[]{12, 5, 21, 12, 12, 19});
		} else {
			gc.fillPolygon(new int[]{12, 5, 3, 12, 12, 19});
			gc.fillPolygon(new int[]{21, 5, 12, 12, 21, 19});
		}
		gc.dispose();
		return image;
	}

	/**
	 * Single triangle with a vertical bar at the tip (refine into interval).
	 */
	private static Image refine(final Display display, final boolean pointRight) {
		final Image image = new Image(display, 24, 24);
		final GC gc = new GC(image);
		fillTransparent(gc, display);
		gc.setAntialias(SWT.ON);
		gc.setBackground(display.getSystemColor(SWT.COLOR_BLACK));
		if (pointRight) {
			gc.fillPolygon(new int[]{4, 5, 15, 12, 4, 19});
			gc.fillRectangle(17, 5, 3, 14);
		} else {
			gc.fillRectangle(4, 5, 3, 14);
			gc.fillPolygon(new int[]{20, 5, 9, 12, 20, 19});
		}
		gc.dispose();
		return image;
	}

	private static void fillTransparent(final GC gc, final Display display) {
		final Color bg = display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		gc.setBackground(bg);
		gc.fillRectangle(0, 0, 24, 24);
	}

	Icons(final Display display) {
		play = triangle(display, true);
		pause = pause(display);
		rewind = doubleTriangle(display, false);
		forward = doubleTriangle(display, true);
		refineLeft = refine(display, false);
		refineRight = refine(display, true);
	}

	void dispose() {
		play.dispose();
		pause.dispose();
		rewind.dispose();
		forward.dispose();
		refineLeft.dispose();
		refineRight.dispose();
	}
}
