package net.scriptorium.videocutter.media;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Display;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * Software player: decode on a worker thread, paint on SWT. Video only (no audio).
 */
public final class JavacvPlayer implements IPlayer {

	private final Logger log = LogManager.getLogger(getClass());

	private final Display display;

	private final FrameSink frameSink;

	private final IntConsumer timeSink;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private final AtomicBoolean paused = new AtomicBoolean(true);

	private final AtomicBoolean shutdown = new AtomicBoolean(true);

	private final Object seekLock = new Object();

	private volatile Long pendingSeekMicros;

	private volatile FFmpegFrameGrabber grabber;

	private volatile Thread decodeThread;

	private volatile double frameRate = 25;

	private volatile long lastTimestampUs = 0;

	private volatile int durationMillis = 0;

	private volatile long playOriginWallMs;

	private volatile long playOriginMediaUs;

	/**
	 * After seek: rebase clock once the first in-range packet arrives.
	 */
	private volatile boolean awaitingSeekTarget;

	/**
	 * Present video slightly ahead of the media clock to cover convert + SWT paint latency.
	 */
	private static final long VIDEO_PRESENT_LEAD_US = 80_000L;

	/**
	 * Discard convert only when this far behind the media clock (no present lead in the comparison). Tight windows
	 * freeze the picture after one slow convert.
	 */
	private static final long CATCHUP_LATE_US = 250_000L;

	/**
	 * If nothing was presented for this long, force-show the next frame even when late-check would drop it.
	 */
	private static final long FORCE_PRESENT_STALL_MS = 300L;

	/**
	 * Latest frame waiting for SWT; coalesce so the UI queue cannot accumulate lag.
	 */
	private final AtomicReference<PendingFrame> pendingPresent = new AtomicReference<>();

	private final AtomicBoolean presentScheduled = new AtomicBoolean(false);

	/**
	 * Wall-clock ms of last successful present (decode or UI schedule).
	 */
	private final AtomicLong lastPresentWallMs = new AtomicLong(0);

	public JavacvPlayer(final Display display, final FrameSink frameSink, final IntConsumer timeSink) {
		this.display = display;
		this.frameSink = frameSink;
		this.timeSink = timeSink;
	}

	private record PendingFrame(ImageData data, int width, int height) {

	}

	@Override
	public void open(final String filePath) throws Exception {
		close();
		shutdown.set(false);
		paused.set(true);
		final FFmpegFrameGrabber g = Analysis.openGrabber(java.nio.file.Path.of(filePath));
		grabber = g;
		frameRate = g.getFrameRate() > 0 ? g.getFrameRate() : 25;
		durationMillis = Analysis.durationMillis(g);
		lastTimestampUs = 0;
		running.set(true);
		decodeThread = new Thread(this::decodeLoop, "vc-decode");
		decodeThread.setDaemon(true);
		decodeThread.start();
		presentFirstFrame(g);
	}

	private void presentFirstFrame(final FFmpegFrameGrabber g) {
		try {
			final Frame frame = g.grabImage();
			if (frame != null) {
				publishVideo(frame);
				lastTimestampUs = Math.max(0, frame.timestamp);
				timeSink.accept((int) (lastTimestampUs / 1000L));
			}
		} catch (final Exception ignored) {
			// first frame optional
		}
	}

	private void decodeLoop() {
		final Java2DFrameConverter converter = new Java2DFrameConverter();
		try {
			while (running.get() && !shutdown.get()) {
				if (paused.get()) {
					Thread.sleep(20);
					continue;
				}
				Long seek = null;
				synchronized (seekLock) {
					seek = pendingSeekMicros;
					pendingSeekMicros = null;
				}
				final FFmpegFrameGrabber g = grabber;
				if (g == null) {
					break;
				}
				if (seek != null) {
					try {
						final Analysis.GrabbedFrame grabbed = Analysis.grabFrameAtOrAfter(g, seek);
						if (grabbed != null) {
							final long ts = grabbed.timestampMicros();
							playOriginWallMs = System.currentTimeMillis();
							playOriginMediaUs = ts;
							lastTimestampUs = ts;
							awaitingSeekTarget = false;
							final Frame seekFrame = grabbed.frame();
							if (seekFrame.image != null) {
								final ImageData data = convertFrame(seekFrame, converter);
								if (data != null) {
									presentImageData(data, seekFrame.imageWidth, seekFrame.imageHeight);
								}
								notifyTime((int) (ts / 1000L));
							}
							continue;
						}
					} catch (final Exception e) {
						log.error("seek failed", e);
					}
				}
				final Frame frame;
				try {
					frame = g.grabFrame(false, true, true, false, false);
				} catch (final Exception e) {
					break;
				}
				if (frame == null) {
					paused.set(true);
					continue;
				}
				long ts = frame.timestamp;
				if (ts < 0) {
					ts = lastTimestampUs + (long) (1_000_000.0 / Math.max(frameRate, 1));
				}
				if (ts < playOriginMediaUs) {
					// after seek, drop keyframe pre-roll
					continue;
				}
				if (awaitingSeekTarget) {
					awaitingSeekTarget = false;
					playOriginWallMs = System.currentTimeMillis();
					playOriginMediaUs = ts;
				}
				lastTimestampUs = ts;
				if (frame.image != null) {
					final long clockUs = mediaClockUs();
					final boolean stalled = System.currentTimeMillis() - lastPresentWallMs.get()
							>= FORCE_PRESENT_STALL_MS;
					if (!stalled && ts < clockUs - CATCHUP_LATE_US) {
						// far behind — skip convert; stall guard will force a frame soon
						notifyTime((int) (ts / 1000L));
					} else {
						// convert before wait so present lands near the deadline
						final ImageData data = convertFrame(frame, converter);
						if (data != null) {
							syncVideo(ts);
							presentImageData(data, frame.imageWidth, frame.imageHeight);
						}
						notifyTime((int) (ts / 1000L));
					}
				}
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			converter.close();
		}
	}

	/**
	 * Pace video to the wall-clock media timeline (plus present lead). Sleeps only when early; when late, returns
	 * immediately so the caller can still paint the frame.
	 */
	private void syncVideo(final long mediaUs) throws InterruptedException {
		final long deadlineMs = System.currentTimeMillis() + 500L;
		while (System.currentTimeMillis() < deadlineMs) {
			final long delayUs = mediaUs - (mediaClockUs() + VIDEO_PRESENT_LEAD_US);
			// Late or within 2 ms — paint now; never sleep while behind.
			if (delayUs <= 2_000L) {
				return;
			}
			final long sleepUs = Math.min(delayUs, 20_000L);
			Thread.sleep(Math.max(1L, sleepUs / 1000L));
		}
	}

	/**
	 * Media time according to wall-clock since play/seek origin.
	 */
	private long mediaClockUs() {
		return playOriginMediaUs + (System.currentTimeMillis() - playOriginWallMs) * 1000L;
	}

	private void publishVideo(final Frame frame) {
		try (final Java2DFrameConverter converter = new Java2DFrameConverter()) {
			final ImageData data = convertFrame(frame, converter);
			if (data != null) {
				presentImageData(data, frame.imageWidth, frame.imageHeight);
			}
		}
	}

	private static ImageData convertFrame(final Frame frame, final Java2DFrameConverter converter) {
		final BufferedImage image = converter.convert(frame);
		if (image == null) {
			return null;
		}
		return toImageData(image);
	}

	private void presentImageData(final ImageData data, final int width, final int height) {
		if (display.isDisposed()) {
			return;
		}
		lastPresentWallMs.set(System.currentTimeMillis());
		pendingPresent.set(new PendingFrame(data, width, height));
		if (!presentScheduled.compareAndSet(false, true)) {
			return;
		}
		display.asyncExec(this::drainPendingPresent);
	}

	private void drainPendingPresent() {
		presentScheduled.set(false);
		if (shutdown.get()) {
			return;
		}
		final PendingFrame pending = pendingPresent.getAndSet(null);
		if (pending == null || display.isDisposed() || shutdown.get()) {
			return;
		}
		frameSink.present(pending.data(), pending.width(), pending.height());
		// A newer frame may have arrived after getAndSet; schedule one more drain.
		if (pendingPresent.get() != null && presentScheduled.compareAndSet(false, true)) {
			display.asyncExec(this::drainPendingPresent);
		}
	}

	static ImageData toImageData(final BufferedImage image) {
		final int width = image.getWidth();
		final int height = image.getHeight();
		if (image.getType() == BufferedImage.TYPE_3BYTE_BGR
				&& image.getRaster().getDataBuffer() instanceof final DataBufferByte byteBuffer
				&&
				image.getRaster().getSampleModel() instanceof final java.awt.image.ComponentSampleModel sampleModel) {
			final byte[] src = byteBuffer.getData();
			final int srcOffset = byteBuffer.getOffset();
			final int srcStride = sampleModel.getScanlineStride();
			final PaletteData palette = new PaletteData(0x0000FF, 0x00FF00, 0xFF0000);
			final ImageData data = new ImageData(width, height, 24, palette);
			final int rowBytes = width * 3;
			final int dstStride = data.bytesPerLine;
			if (srcStride == dstStride && srcOffset == 0 && src.length >= height * dstStride) {
				System.arraycopy(src, 0, data.data, 0, height * dstStride);
			} else {
				for (int y = 0; y < height; y++) {
					System.arraycopy(src, srcOffset + y * srcStride, data.data, y * dstStride, rowBytes);
				}
			}
			return data;
		}
		final PaletteData palette = new PaletteData(0xFF0000, 0x00FF00, 0x0000FF);
		final ImageData data = new ImageData(width, height, 24, palette);
		final int[] rgb = new int[width];
		for (int y = 0; y < height; y++) {
			image.getRGB(0, y, width, 1, rgb, 0, width);
			for (int x = 0; x < width; x++) {
				data.setPixel(x, y, rgb[x] & 0xFFFFFF);
			}
		}
		return data;
	}

	private void notifyTime(final int millis) {
		if (display.isDisposed()) {
			return;
		}
		display.asyncExec(() -> {
			if (!display.isDisposed()) {
				timeSink.accept(millis);
			}
		});
	}

	@Override
	public void play() {
		if (paused.compareAndSet(true, false)) {
			playOriginWallMs = System.currentTimeMillis();
			playOriginMediaUs = lastTimestampUs;
		}
	}

	@Override
	public void pause() {
		paused.compareAndSet(false, true);
	}

	@Override
	public boolean isPaused() {
		return paused.get();
	}

	@Override
	public int goTo(final int millis) {
		int target = Math.max(0, millis);
		final int duration = durationMillis;
		if (duration > 0) {
			target = Math.min(target, duration);
		}
		final long micros = target * 1000L;
		synchronized (seekLock) {
			pendingSeekMicros = micros;
		}
		lastTimestampUs = micros;
		final boolean wasPaused = paused.get();
		int actual = target;
		if (wasPaused) {
			final FFmpegFrameGrabber g = grabber;
			if (g != null) {
				try {
					final Analysis.GrabbedFrame grabbed = Analysis.grabFrameAtOrAfter(g, micros);
					if (grabbed != null) {
						publishVideo(grabbed.frame());
						lastTimestampUs = grabbed.timestampMicros();
						actual = (int) Math.min(Integer.MAX_VALUE, lastTimestampUs / 1000L);
					} else {
						final long ts = g.getTimestamp();
						if (ts >= 0) {
							lastTimestampUs = ts;
							actual = (int) Math.min(Integer.MAX_VALUE, ts / 1000L);
						}
					}
					if (duration > 0) {
						actual = Math.min(actual, duration);
					}
					playOriginMediaUs = lastTimestampUs;
					awaitingSeekTarget = false;
					synchronized (seekLock) {
						pendingSeekMicros = null;
					}
					timeSink.accept(actual);
				} catch (final Exception e) {
					log.error("paused seek failed", e);
				}
			}
		}
		if (duration > 0) {
			actual = Math.min(actual, duration);
		}
		return actual;
	}

	@Override
	public void close() {
		shutdown.set(true);
		running.set(false);
		paused.set(true);
		durationMillis = 0;
		final Thread decode = decodeThread;
		if (decode != null) {
			decode.interrupt();
			try {
				decode.join(1000);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			decodeThread = null;
		}
		final FFmpegFrameGrabber g = grabber;
		grabber = null;
		if (g != null) {
			try {
				g.stop();
			} catch (final Exception ignored) {
				// ignore
			}
			try {
				g.release();
			} catch (final Exception ignored) {
				// ignore
			}
		}
		pendingPresent.set(null);
		presentScheduled.set(false);
		lastPresentWallMs.set(0L);
	}
}
