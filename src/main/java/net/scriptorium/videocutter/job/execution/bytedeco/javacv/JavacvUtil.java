package net.scriptorium.videocutter.job.execution.bytedeco.javacv;

import net.scriptorium.videocutter.FrameSize;
import net.scriptorium.videocutter.Settings;
import net.scriptorium.videocutter.UncheckedException;
import net.scriptorium.videocutter.job.ClipJob;
import net.scriptorium.videocutter.job.ShotJob;
import net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings;
import net.scriptorium.videocutter.media.Analysis;
import org.apache.logging.log4j.LogManager;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

import static net.scriptorium.videocutter.job.JobUtil.throwIfInterrupted;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.AUDIO_BITRATE;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.audioCodecName;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.containerFormat;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.normalizeFormat;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;

public class JavacvUtil {

	public static boolean takeShot(final ShotJob job, final Path source, final Path resultFile) throws IOException {
		try (final FFmpegFrameGrabber grabber = Analysis.openGrabber(source);
			 final Java2DFrameConverter converter = new Java2DFrameConverter()) {
			final long targetUs = job.timeMillis() * 1000L;
			final Analysis.GrabbedFrame grabbed = Analysis.grabFrameAtOrAfter(grabber, targetUs);
			if (grabbed == null || grabbed.frame().image == null) {
				return false;
			}
			final BufferedImage image = converter.convert(grabbed.frame());
			if (image == null) {
				return false;
			}
			return writeImage(image, job.format(), resultFile);
		} catch (final Exception e) {
			throw new IOException("failed to take shot", e);
		}
	}

	private static boolean writeImage(final BufferedImage image, final String format,
			final Path resultFile) throws Exception {
		final String fmt = format.toUpperCase(Locale.ROOT);
		return switch (fmt) {
			case "JPEG", "JPG" -> writeJpeg(image, resultFile);
			case "PNG" -> ImageIO.write(image, "png", resultFile.toFile());
			case "WEBP" -> writeViaRecorder(image, resultFile, "webp");
			default -> false;
		};
	}

	private static boolean writeJpeg(final BufferedImage image, final Path resultFile) throws IOException {
		final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) {
			return ImageIO.write(image, "jpg", resultFile.toFile());
		}
		final ImageWriter writer = writers.next();
		try (final ImageOutputStream out = ImageIO.createImageOutputStream(resultFile.toFile())) {
			writer.setOutput(out);
			final ImageWriteParam param = writer.getDefaultWriteParam();
			if (param.canWriteCompressed()) {
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				param.setCompressionQuality(0.92f);
			}
			writer.write(null, new IIOImage(image, null, null), param);
			return true;
		} finally {
			writer.dispose();
		}
	}

	private static boolean writeViaRecorder(final BufferedImage image, final Path resultFile,
			final String format) throws Exception {
		try (final Java2DFrameConverter converter = new Java2DFrameConverter();
			 final FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(resultFile.toFile(), image.getWidth(), image.getHeight())) {
			recorder.setFormat(format);
			recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
			recorder.setVideoCodecName("libwebp");
			recorder.setVideoOption("quality", "80");
			recorder.setVideoOption("lossless", "0");
			recorder.setFrameRate(1);
			recorder.start();
			final Frame frame = converter.convert(image);
			recorder.record(frame);
			recorder.stop();
			return true;
		}
	}

	public static boolean transcode(
			final ClipJob job,
			final Path source,
			final Path resultFile,
			final long startUs,
			final long endUs) {
		final boolean mute = job.audio() != null && job.audio().isNoSound();
		int outW = 0;
		int outH = 0;
		try (final FFmpegFrameGrabber grabber = Analysis.openGrabber(source)) {
			final int srcW = Math.max(grabber.getImageWidth(), 1);
			final int srcH = Math.max(grabber.getImageHeight(), 1);
			final FrameSize size = job.size();
			outW = size == null ? srcW : size.resolveWidth(srcW, srcH);
			outH = size == null ? srcH : size.resolveHeight(srcW, srcH);
			outW = Math.max(2, outW & ~1);
			outH = Math.max(2, outH & ~1);

			final int audioChannels = mute ? 0 : Math.max(grabber.getAudioChannels(), 0);
			try (final FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(resultFile.toFile(), outW, outH, audioChannels)) {
				configureJavacvRecorder(recorder, grabber, job.format(), outW, outH);
				recorder.start();
				grabber.setTimestamp(startUs, true);
				Frame frame;
				while ((frame = grabber.grabFrame(!mute, true, true, false, false)) != null) {
					throwIfInterrupted();
					final long ts = frame.timestamp;
					if (ts < 0) {
						continue;
					}
					if (ts < startUs) {
						continue;
					}
					if (ts > endUs) {
						break;
					}
					if (mute && frame.samples != null) {
						continue;
					}
					if (frame.image == null && frame.samples == null) {
						continue;
					}
					if (frame.image != null) {
						final long outTs = nextRecorderTimestamp(
								recorder.getTimestamp(), Math.max(0L, ts - startUs));
						if (outTs > recorder.getTimestamp()) {
							recorder.setTimestamp(outTs);
						}
					}
					recorder.record(frame);
				}
				recorder.stop();
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new UncheckedException(e);
		} catch (final Exception e) {
			LogManager.getLogger(JavacvUtil.class).error(
					"failed to transcode format={} {}x{}", job.format(), outW, outH, e);
			throw new RuntimeException(
					"Transcode failed: format=" + job.format() + " " + outW + "x" + outH + ": " + e.getMessage(), e);
		}
		return true;
	}

	private static void configureJavacvRecorder(
			final FFmpegFrameRecorder recorder,
			final FFmpegFrameGrabber grabber,
			final String format,
			final int outWidth,
			final int outHeight) throws Exception {
		final String fmt = normalizeFormat(format);
		recorder.setFormat(containerFormat(format));
		recorder.setFrameRate(grabber.getFrameRate() > 0 ? grabber.getFrameRate() : 25);
		recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
		if (!"hevc".equals(fmt)) {
			recorder.setVideoOption("threads", Integer.toString(mediaThreads()));
		}
		EncodeSettings.applyJavacvVideoOptions(fmt, grabber.getVideoBitrate(), outWidth, outHeight, new EncodeSettings.VideoOptionSetter() {
			@Override
			public void setVideoCodecName(final String codec) {
				recorder.setVideoCodecName(codec);
			}

			@Override
			public void setVideoCodec(final int codecId) {
				recorder.setVideoCodec(codecId);
			}

			@Override
			public void setVideoOption(final String key, final String value) {
				recorder.setVideoOption(key, value);
			}

			@Override
			public void setVideoBitrate(final int bitrate) {
				recorder.setVideoBitrate(bitrate);
			}
		});
		if (recorder.getAudioChannels() > 0) {
			recorder.setSampleRate(grabber.getSampleRate() > 0 ? grabber.getSampleRate() : 48_000);
			recorder.setAudioBitrate(AUDIO_BITRATE);
			recorder.setAudioCodecName(audioCodecName(fmt));
		}
	}

	private static int mediaThreads() {
		if (Settings.instance() == null) {
			return Math.max(1, Runtime.getRuntime().availableProcessors());
		}
		return Settings.instance().mediaThreads();
	}

	/**
	 * Returns the timestamp to pass to {@link FFmpegFrameRecorder#setTimestamp(long)}. Keeps recorder PTS strictly
	 * increasing (JavaCV maps timestamp to picture.pts).
	 */
	private static long nextRecorderTimestamp(final long currentRecorderUs, final long candidateUs) {
		return candidateUs > currentRecorderUs ? candidateUs : currentRecorderUs;
	}
}
