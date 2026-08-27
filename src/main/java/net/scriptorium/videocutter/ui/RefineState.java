package net.scriptorium.videocutter.ui;

import net.scriptorium.videocutter.media.Position;

/**
 * Active binary-search interval between keyframe navigation marks.
 */
final class RefineState {

	final Position l;

	final Position r;

	RefineState(final Position l, final Position r) {
		assert l != null && r != null && 0 <= l.getRequested() && l.getRequested() <= r.getRequested() :
				"invalid arguments: (" + l + ", " + r + ")";
		this.l = l;
		this.r = r;
	}

	boolean isDegenerate() {
		return l.getRequested() == r.getRequested();
	}

	boolean contains(final Position x) {
		return x.getActual() >= l.getActual() && x.getActual() <= r.getActual();
	}
}
