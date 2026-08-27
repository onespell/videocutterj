package net.scriptorium.videocutter.job.execution.bytedeco.ffmpeg;

import net.scriptorium.videocutter.TimeUtil;
import net.scriptorium.videocutter.job.ClipJob;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_rescale_ts;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_copy;
import static org.bytedeco.ffmpeg.global.avformat.AVFMT_NOFILE;
import static org.bytedeco.ffmpeg.global.avformat.AVIO_FLAG_WRITE;
import static org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD;
import static org.bytedeco.ffmpeg.global.avformat.av_interleaved_write_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_seek_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_write_trailer;
import static org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_free_context;
import static org.bytedeco.ffmpeg.global.avformat.avformat_new_stream;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_write_header;
import static org.bytedeco.ffmpeg.global.avformat.avio_closep;
import static org.bytedeco.ffmpeg.global.avformat.avio_open;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.AV_TIME_BASE;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q;

public class BytedecoUtil {

	public static String describe(final ClipJob job, final boolean needsReencode, final Path source,
			final Path resultFile) {
		final String mode;
		if (needsReencode) {
			mode = "bytedeco reencode";
		} else {
			mode = "bytedeco remux";
		}
		return mode + " "
				+ TimeUtil.toTimeCode(job.timeMillis()) + "-" + TimeUtil.toTimeCode(job.finishMillis())
				+ (job.size() != null ? " " + job.size() : "")
				+ " " + job.format() + " -> " + resultFile
				+ " (from " + source.getFileName() + ")";
	}

	/**
	 * Decode/encode/mux via bytedeco FFmpeg natives.
	 *
	 * @param job
	 * @param source
	 * @param resultFile
	 * @param startUs
	 * @param endUs
	 * @return
	 */
	public static boolean transcode(
			final ClipJob job,
			final Path source,
			final Path resultFile,
			final long startUs,
			final long endUs) {
		if (endUs <= startUs) {
			return false;
		}
		try {
			FFmpegFrameGrabber.tryLoad();
		} catch (final Exception e) {
			return false;
		}
		try (final Session session = new Session()) {
			return session.run(job, source, resultFile, startUs, endUs);
		} catch (final Exception e) {
			return false;
		}
	}

	/**
	 * Packet-level remux (stream copy) via bytedeco FFmpeg natives — no decode/encode.
	 *
	 * @param job
	 * @param source
	 * @param resultFile
	 * @param startUs
	 * @param endUs
	 * @return
	 */
	public static boolean remux(
			final ClipJob job,
			final Path source,
			final Path resultFile,
			final long startUs,
			final long endUs) {
		if (endUs <= startUs) {
			return false;
		}
		try {
			FFmpegFrameGrabber.tryLoad();
		} catch (final Exception e) {
			return false;
		}
		final boolean mute = job.audio() != null && job.audio().isNoSound();
		final Integer videoIdx = parseStreamId(job.video() == null ? null : job.video().id());
		final Integer audioIdx = mute ? null : parseStreamId(job.audio() == null ? null : job.audio().id());

		final AVFormatContext ifmt = new AVFormatContext(null);
		AVFormatContext ofmt = null;
		final AVPacket packet = new AVPacket();
		final AVRational timeBaseQ = new AVRational();
		timeBaseQ.num(1);
		timeBaseQ.den(AV_TIME_BASE);

		boolean inputOpened = false;
		boolean headerWritten = false;
		boolean pbOpened = false;
		try {
			if (avformat_open_input(ifmt, source.toAbsolutePath().toString(), null, null) < 0) {
				return false;
			}
			inputOpened = true;
			if (avformat_find_stream_info(ifmt, (AVDictionary) null) < 0) {
				return false;
			}

			final int nb = ifmt.nb_streams();
			final int[] mapping = new int[nb];
			Arrays.fill(mapping, -1);

			ofmt = new AVFormatContext(null);
			if (avformat_alloc_output_context2(ofmt, null, muxerName(job.format()),
					resultFile.toAbsolutePath().toString()) < 0) {
				return false;
			}

			int outIndex = 0;
			boolean haveVideo = false;
			boolean haveAudio = false;
			for (int i = 0; i < nb; i++) {
				final AVStream inStream = ifmt.streams(i);
				if (inStream == null || inStream.isNull() || inStream.codecpar() == null) {
					continue;
				}
				final int type = inStream.codecpar().codec_type();
				if (type == AVMEDIA_TYPE_VIDEO) {
					if (videoIdx != null && videoIdx != i) {
						continue;
					}
					if (videoIdx == null && haveVideo) {
						continue;
					}
					haveVideo = true;
				} else if (type == AVMEDIA_TYPE_AUDIO) {
					if (mute) {
						continue;
					}
					if (audioIdx != null && audioIdx != i) {
						continue;
					}
					if (audioIdx == null && haveAudio) {
						continue;
					}
					haveAudio = true;
				} else {
					continue;
				}
				final AVStream outStream = avformat_new_stream(ofmt, null);
				if (outStream == null || outStream.isNull()) {
					return false;
				}
				if (avcodec_parameters_copy(outStream.codecpar(), inStream.codecpar()) < 0) {
					return false;
				}
				outStream.codecpar().codec_tag(0);
				mapping[i] = outIndex++;
			}
			if (!haveVideo) {
				return false;
			}

			if ((ofmt.oformat().flags() & AVFMT_NOFILE) == 0) {
				final AVIOContext pb = new AVIOContext(null);
				if (avio_open(pb, resultFile.toAbsolutePath().toString(), AVIO_FLAG_WRITE) < 0) {
					return false;
				}
				ofmt.pb(pb);
				pbOpened = true;
			}

			if (avformat_write_header(ofmt, (AVDictionary) null) < 0) {
				return false;
			}
			headerWritten = true;

			av_seek_frame(ifmt, -1, startUs, AVSEEK_FLAG_BACKWARD);

			final long[] tsOffset = new long[nb];
			final boolean[] gotOffset = new boolean[nb];
			final long[] lastDts = new long[outIndex];
			final boolean[] hasLastDts = new boolean[outIndex];

			while (av_read_frame(ifmt, packet) >= 0) {
				try {
					final int inIdx = packet.stream_index();
					if (inIdx < 0 || inIdx >= nb || mapping[inIdx] < 0) {
						continue;
					}
					if (packet.pts() == AV_NOPTS_VALUE && packet.dts() == AV_NOPTS_VALUE) {
						continue;
					}
					final AVStream inStream = ifmt.streams(inIdx);
					long pktTs = packet.pts();
					if (pktTs == AV_NOPTS_VALUE) {
						pktTs = packet.dts();
					}
					if (pktTs != AV_NOPTS_VALUE) {
						final long pktUs = av_rescale_q(pktTs, inStream.time_base(), timeBaseQ);
						if (pktUs < startUs) {
							continue;
						}
						if (pktUs > endUs) {
							if (inStream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
								break;
							}
							continue;
						}
					}
					if (!gotOffset[inIdx]) {
						final long offset;
						if (packet.pts() != AV_NOPTS_VALUE && packet.dts() != AV_NOPTS_VALUE) {
							offset = Math.min(packet.pts(), packet.dts());
						} else if (packet.dts() != AV_NOPTS_VALUE) {
							offset = packet.dts();
						} else {
							offset = packet.pts();
						}
						tsOffset[inIdx] = offset;
						gotOffset[inIdx] = true;
					}
					if (packet.pts() != AV_NOPTS_VALUE) {
						packet.pts(packet.pts() - tsOffset[inIdx]);
					}
					if (packet.dts() != AV_NOPTS_VALUE) {
						packet.dts(packet.dts() - tsOffset[inIdx]);
					}

					final AVStream outStream = ofmt.streams(mapping[inIdx]);
					av_packet_rescale_ts(packet, inStream.time_base(), outStream.time_base());
					packet.stream_index(mapping[inIdx]);
					packet.pos(-1);

					final int outIdx = mapping[inIdx];
					if (packet.dts() != AV_NOPTS_VALUE) {
						if (hasLastDts[outIdx] && packet.dts() <= lastDts[outIdx]) {
							final long fixed = lastDts[outIdx] + 1;
							if (packet.pts() != AV_NOPTS_VALUE && packet.pts() < fixed) {
								packet.pts(fixed);
							}
							packet.dts(fixed);
						}
						lastDts[outIdx] = packet.dts();
						hasLastDts[outIdx] = true;
					} else if (packet.pts() != AV_NOPTS_VALUE) {
						if (hasLastDts[outIdx] && packet.pts() <= lastDts[outIdx]) {
							packet.pts(lastDts[outIdx] + 1);
						}
						lastDts[outIdx] = packet.pts();
						hasLastDts[outIdx] = true;
					}

					if (av_interleaved_write_frame(ofmt, packet) < 0) {
						return false;
					}
				} finally {
					av_packet_unref(packet);
				}
			}

			av_write_trailer(ofmt);
			headerWritten = false;
			return true;
		} catch (final Exception e) {
			return false;
		} finally {
			packet.close();
			timeBaseQ.deallocate();
			if (ofmt != null && !ofmt.isNull()) {
				if (headerWritten) {
					try {
						av_write_trailer(ofmt);
					} catch (final Exception ignored) {
						//
					}
				}
				if (pbOpened && ofmt.pb() != null && !ofmt.pb().isNull()) {
					avio_closep(ofmt.pb());
				}
				avformat_free_context(ofmt);
			}
			if (inputOpened) {
				avformat_close_input(ifmt);
			}
		}
	}

	static String muxerName(final String format) {
		final String fmt = format == null ? "" : format.toLowerCase(Locale.ROOT);
		return switch (fmt) {
			case "mkv" -> "matroska";
			case "wmv" -> "asf";
			default -> fmt;
		};
	}

	static Integer parseStreamId(final String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(id.trim());
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	private BytedecoUtil() {
		//
	}
}
