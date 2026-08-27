package net.scriptorium.videocutter.media;

public class Position {

	private int requested = -1;

	private int actual = -1;

	public Position() {
		//
	}

	public Position(final int value) {
		this.requested = value;
		this.actual = value;
	}

	public Position(final int requested, final int actual) {
		this.requested = requested;
		this.actual = actual;
	}

	public void set(final int requested, final int actual) {
		this.requested = requested;
		this.actual = actual;
	}

	public int getRequested() {
		return (requested < 0) ? actual : requested;
	}

	public void setRequested(final int requested) {
		this.requested = requested;
	}

	public int getActual() {
		return actual;
	}

	public void setActual(final int actual) {
		this.actual = actual;
	}

	public Position copy() {
		return new Position(requested, actual);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		if (actual >= 0) {
			sb.append(actual);
		}
		if (requested >= 0) {
			sb.append(" (").append(requested).append(")");
		}
		return sb.toString();
	}
}
