package net.scriptorium.videocutter;

import java.util.Objects;

public record FrameSize(String width, String height) {

	public static FrameSize of(final int width, final int height) {
		return new FrameSize(Integer.toString(width), Integer.toString(height));
	}

	public static FrameSize of(final String width, final String height) {
		return new FrameSize(width, height);
	}

	public static FrameSize wildcardHeight(final int height) {
		return new FrameSize("*", Integer.toString(height));
	}

	public static FrameSize wildcardWidth(final int width) {
		return new FrameSize(Integer.toString(width), "*");
	}

	public boolean isWildcardWidth() {
		return "*".equals(width);
	}

	public boolean isWildcardHeight() {
		return "*".equals(height);
	}

	public int widthValue() {
		return Integer.parseInt(width);
	}

	public int heightValue() {
		return Integer.parseInt(height);
	}

	public int resolveWidth(final int sourceWidth, final int sourceHeight) {
		if (isWildcardWidth()) {
			final int h = heightValue();
			return Math.max(2, (sourceWidth * h / Math.max(sourceHeight, 1)) & ~1);
		}
		return widthValue();
	}

	public int resolveHeight(final int sourceWidth, final int sourceHeight) {
		if (isWildcardHeight()) {
			final int w = widthValue();
			return Math.max(2, (sourceHeight * w / Math.max(sourceWidth, 1)) & ~1);
		}
		return heightValue();
	}

	@Override
	public String toString() {
		return width + "x" + height;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof final FrameSize other)) {
			return false;
		}
		return Objects.equals(width, other.width) && Objects.equals(height, other.height);
	}
}
