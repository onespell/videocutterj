package net.scriptorium.videocutter;

public final class MediaStream {

	private final Kind kind;

	private final String id;

	private final String caption;

	private MediaStream(final Kind kind, final String id, final String caption) {
		this.kind = kind;
		this.id = id;
		this.caption = caption;
	}

	public static MediaStream audio(final String id, final String caption) {
		return new MediaStream(Kind.AUDIO, id, caption);
	}

	public static MediaStream video(final String id, final String caption) {
		return new MediaStream(Kind.VIDEO, id, caption);
	}

	public Kind kind() {
		return kind;
	}

	public String id() {
		return id;
	}

	public String caption() {
		return caption;
	}

	public boolean isNoSound() {
		return kind == Kind.AUDIO && (id == null || id.isEmpty());
	}

	public enum Kind {
		AUDIO,
		VIDEO
	}
}
