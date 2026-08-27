package net.scriptorium.videocutter.job.execution.bytedeco.javacv;

import net.scriptorium.videocutter.FrameSize;
import net.scriptorium.videocutter.Settings;
import net.scriptorium.videocutter.TimeUtil;
import net.scriptorium.videocutter.job.ClipJob;
import net.scriptorium.videocutter.job.ShotJob;
import net.scriptorium.videocutter.media.Analysis;
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

import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.AUDIO_BITRATE;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.CRF_H264;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.CRF_VP9;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.PRESET_FAST;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.PRESET_REENCODE;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.WMV_VIDEO_BITRATE;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.audioCodecName;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.normalizeFormat;
import static net.scriptorium.videocutter.job.execution.bytedeco.EncodeSettings.videoCodecName;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;

public class JavacvUtil {

	public static String describe(final ShotJob job, final Path source, final Path resultFile) {
		return "javacv freeze " + TimeUtil.toTimeCode(job.timeMillis()) + " "
				+ job.format() + " -> " + resultFile + " (from " + source.getFileName() + ")";
	}

	public static boolean takeShot(final ShotJob job, final Path source, final Path resultFile) throws IOException {
		try (final FFmpegFrameGrabber grabber = Analysis.openGrabber(source);
			 final Java2DFrameConverter converter = new Java2DFrameConverter()) {
			grabber.setTimestamp(job.timeMillis() * 1000L, true);
			Frame frame = grabber.grabImage();
			if (frame == null || frame.image == null) {
				frame = grabber.grabKeyFrame();
			}
			if (frame == null || frame.image == null) {
				return false;
			}
			final BufferedImage image = converter.convert(frame);
			if (image == null) {
				return false;
			}
			return writeImage(image, job.format(), resultFile);
		} catch (final Exception e) {
			throw new IOException(e.getMessage(), e);
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
			final long endUs) throws Exception {
		final boolean mute = job.audio() != null && job.audio().isNoSound();
		try (final FFmpegFrameGrabber grabber = Analysis.openGrabber(source)) {
			final int srcW = Math.max(grabber.getImageWidth(), 1);
			final int srcH = Math.max(grabber.getImageHeight(), 1);
			final FrameSize size = job.size();
			int outW = size == null ? srcW : size.resolveWidth(srcW, srcH);
			int outH = size == null ? srcH : size.resolveHeight(srcW, srcH);
			outW = Math.max(2, outW & ~1);
			outH = Math.max(2, outH & ~1);

			final int audioChannels = mute ? 0 : Math.max(grabber.getAudioChannels(), 0);
			try (final FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(resultFile.toFile(), outW, outH, audioChannels)) {
				configureJavacvRecorder(recorder, grabber, job.format(), size != null);
				recorder.start();
				grabber.setTimestamp(startUs, true);
				Frame frame;
				while ((frame = grabber.grabFrame(!mute, true, true, false, false)) != null) {
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
		}
		return true;
	}

	private static void configureJavacvRecorder(
			final FFmpegFrameRecorder recorder,
			final FFmpegFrameGrabber grabber,
			final String format,
			final boolean reencode) throws Exception {
		final String fmt = normalizeFormat(format);
		recorder.setFormat(fmt);
		recorder.setFrameRate(grabber.getFrameRate() > 0 ? grabber.getFrameRate() : 25);
		recorder.setVideoOption("threads", Integer.toString(mediaThreads()));
		if ("webm".equals(fmt)) {
			recorder.setVideoCodecName(videoCodecName(fmt));
			recorder.setVideoOption("crf", Integer.toString(CRF_VP9));
		} else if ("wmv".equals(fmt)) {
			recorder.setVideoCodecName(videoCodecName(fmt));
			recorder.setVideoBitrate(WMV_VIDEO_BITRATE);
		} else {
			recorder.setVideoCodecName(videoCodecName(fmt));
			recorder.setVideoOption("crf", Integer.toString(CRF_H264));
			recorder.setVideoOption("preset", reencode ? PRESET_REENCODE : PRESET_FAST);
		}
		recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
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
