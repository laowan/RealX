package com.ycloud.api.process;

import com.ycloud.mediarecord.MediaNative;
import com.ycloud.utils.YYLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ycloud.mediarecord.MediaNative.mediaIsFFmpegRunningNative;

public class MediaProbe{

	private static MediaNative sMediaNative;

	private static final String TAG = MediaProbe.class.getSimpleName();
	private static final AtomicBoolean sMediaProbeLock = new AtomicBoolean(false);

	private MediaProbe() {
	}

	static public void deInit() {
		synchronized (sMediaProbeLock) {
			deInitMediaNative();
		}
	}

	static private void deInitMediaNative() {
		if (sMediaNative != null) {
			sMediaNative.release();
			sMediaNative = null;
		}
	}

	/**
	 * Get media information, for example: file size, duration, frame rate etc. The following formats are supported: mp4/3gp/flv/avi/mkv
	 * 
	 * @param file_path
	 *            local file path
	 * @return a MediaInfo class contain media information, if error happened, return null.
	 */
	static public MediaInfo getMediaInfo(String file_path, boolean shouldDelete) {

		if (mediaIsFFmpegRunningNative()) {
			YYLog.error(TAG, "Another FFmpeg task is running! return.");
			return null;
		}

		synchronized (sMediaProbeLock) {
			if (sMediaNative == null) {
				sMediaNative = new MediaNative();
			}

			String result = null;
			String cmd = null;
			MediaInfo info = new MediaInfo();

			YYLog.info(TAG, "getMediaInfo file_path = " + file_path + ", thread id = " + android.os.Process.myTid());
			if (file_path == null) {
				YYLog.error(TAG, "null file_path/info");
				if (shouldDelete) {
					deInitMediaNative();
				}
				return null;
			}
			try {
				File file = new File(file_path);
				if (!file.exists()) {
					YYLog.error(TAG, "file not exist: " + file_path);
					if (shouldDelete) {
						deInitMediaNative();
					}
					return null;
				}

				//通过对引号转义，支持file_path中有引号对情况
				file_path = file_path.replace("\"", "\\\"");
				file_path = file_path.replace("\'", "\\\'");

				cmd = "ffprobe -print_format json -show_format -show_streams " + "\"" + file_path + "\"";
				YYLog.info(TAG, "execute cmd:"  + cmd);
				result = sMediaNative.mediaProcessNative(MediaNative.libffmpeg_cmd_probe, cmd);
				YYLog.info(TAG, "execute cmd success" );
				if (result == null) {
					YYLog.error(TAG, "execute cmd fail");
					if (shouldDelete) {
						deInitMediaNative();
					}
					return null;
				}
				// MRLog.debug(TAG, result);

				JSONObject root = new JSONObject(result);
				while (root != null) {
					// streams
					JSONArray streamsArray = root.getJSONArray("streams");
					JSONObject jsonElement = null;
					if (streamsArray != null) {
						for (int i = 0; i < streamsArray.length(); ++i) {
							JSONObject obj = (JSONObject) streamsArray.get(i);
							if (obj == null)
								break;
							// codec type
							String codec_type = null;
							if (obj.has("codec_type"))
								codec_type = obj.getString("codec_type");

							//1、video
							if (codec_type != null && codec_type.equals("video")) {

								// video codec
								if (obj.has("codec_name"))
									info.v_codec_name = obj.getString("codec_name");

								if (obj.has("tags"))
									jsonElement = obj.getJSONObject("tags");
								if (jsonElement != null) {
									if (jsonElement.has("rotate"))
										info.v_rotate = jsonElement.getDouble("rotate");
								}

								// width
								if (obj.has("width"))
									info.width = obj.getInt("width");
								// height
								if (obj.has("height"))
									info.height = obj.getInt("height");
								// total frame
								if (obj.has("nb_frames"))
									info.total_frame = obj.getInt("nb_frames");
								// video_duration
								if (obj.has("duration"))
									info.video_duration = obj.getDouble("duration");
								// frame rate
								String frame_rate = null;
								if (obj.has("avg_frame_rate"))
									frame_rate = obj.getString("avg_frame_rate");
								if (frame_rate != null) {
									int div_pos = frame_rate.indexOf("/");
									try {
										float num = Integer.parseInt(frame_rate.substring(0, div_pos));
										float den = Integer.parseInt(frame_rate.substring(div_pos + 1));
										if (den == 0)
											info.frame_rate = 0;
										else
											info.frame_rate = num / den;
									} catch (NumberFormatException e) {
										YYLog.error(TAG, "frame rate parse error: " + e.getMessage());
									}
								}

								// 2、audio
							} else if (codec_type != null && codec_type.equals("audio")) {
								// audio codec
								if (obj.has("codec_name"))
									info.audio_codec_name = obj.getString("codec_name");
								// audio_duration
								if (obj.has("duration"))
									info.audio_duration = obj.getDouble("duration");
								// audio bitrate
								if (obj.has("bit_rate"))
									info.audioBitrate = obj.getInt("bit_rate");
								// audio channels
								if (obj.has("channels"))
									info.audioChannels = obj.getInt("channels");
								// audio sample rate
								if(obj.has("sample_rate"))
									info.audioSampleRate = obj.getInt("sample_rate");

							}

						}
					}

					// 3、format
					jsonElement = root.getJSONObject("format");
					if (jsonElement == null) {
						break;
					} else {

						// filename
						if (jsonElement.has("filename"))
							info.filename = jsonElement.getString("filename");
						// nb_streams
						if (jsonElement.has("nb_streams"))
							info.nb_streams = jsonElement.getInt("nb_streams");
						// format_name
						if (jsonElement.has("format_name"))
							info.format_name = jsonElement.getString("format_name");

						if (info.format_name != null) {
							if (info.format_name.equals("mov,mp4,m4a,3gp,3g2,mj2")) {
								String lfile_path = file_path.toLowerCase(Locale.getDefault());
								if (lfile_path.endsWith(".mov")) {
									info.format_name = "mov";
								} else if (lfile_path.endsWith(".m4a")) {
									info.format_name = "m4a";
								} else if (lfile_path.endsWith(".3gp")) {
									info.format_name = "3gp";
								} else if (lfile_path.endsWith(".3g2")) {
									info.format_name = "3g2";
								} else if (lfile_path.endsWith(".mj2")) {
									info.format_name = "mj2";
								} else {
									info.format_name = "mp4";
								}
							} else if (info.format_name.equals("matroska,webm")) {
								String lfile_path = file_path.toLowerCase(Locale.getDefault());
								if (lfile_path.endsWith(".webm")) {
									info.format_name = "webm";
								} else {
									info.format_name = "matroska";
								}
							}
						}

						// duration
						if (jsonElement.has("duration"))
							info.duration = jsonElement.getDouble("duration");
						// size
						if (jsonElement.has("size"))
							info.size = jsonElement.getLong("size");
						// bit_rate
						if (jsonElement.has("bit_rate"))
							info.bit_rate = jsonElement.getInt("bit_rate");

						if (jsonElement.has("tags"))
							jsonElement = jsonElement.getJSONObject("tags");
						if (jsonElement != null) {
							if (jsonElement.has("creation_time"))
								info.creation_time = jsonElement.getString("creation_time");
							if (jsonElement.has("comment"))
								info.comment = jsonElement.getString("comment");

						}
					}
					// do one time
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				YYLog.error(TAG, "execute cmd fail,exception:" + e != null ? e.getMessage() : "unknown");
				if (shouldDelete) {
					deInitMediaNative();
				}
				return null;
			}

			if (shouldDelete) {
				deInitMediaNative();
			}
			return info;
		}
	}

	static public MediaFrame[] getMediaFrame(String filePath, boolean shouldDelete) {
		synchronized (sMediaProbeLock) {
			if (sMediaNative == null) {
				sMediaNative = new MediaNative();
			}

			String result = null;
			String cmd = null;
			ArrayList<MediaFrame> mediaFrameArrayList = new ArrayList<MediaFrame>();

			if (filePath == null) {
				YYLog.error(TAG, "null file_path/info");
				if (shouldDelete) {
					deInitMediaNative();
				}
				return null;
			}
			try {
				File file = new File(filePath);
				if (!file.exists()) {
					YYLog.error(TAG, "file not exist: " + filePath);
					if (shouldDelete) {
						deInitMediaNative();
					}
					return null;
				}

				cmd = "ffprobe -print_format json -show_frames " + "\"" + filePath + "\"";
				result = sMediaNative.mediaProcessNative(MediaNative.libffmpeg_cmd_probe, cmd);
				if (result == null) {
					if (shouldDelete) {
						deInitMediaNative();
					}
					return null;
				}
				JSONObject root = new JSONObject(result);
				if (root != null) {
					JSONArray framesArray = root.getJSONArray("frames");
					if (framesArray != null) {
						for (int i = 0; i < framesArray.length(); ++i) {
							JSONObject obj = (JSONObject) framesArray.get(i);
							if (obj == null)
								continue;
							MediaFrame mediaFrame = new MediaFrame();
							try {
								if (obj.has("media_type"))
									mediaFrame.media_type = obj.getString("media_type");
								if (obj.has("pkt_dts_time"))
									mediaFrame.pkt_dts_time = Float.parseFloat(String.valueOf(obj.getDouble("pkt_dts_time")));
								if (obj.has("pkt_pts_time"))
									mediaFrame.pkt_pts_time = Float.parseFloat(String.valueOf(obj.getDouble("pkt_pts_time")));
								if (obj.has("pkt_duration_time"))
									mediaFrame.pkt_duration_time = Float.parseFloat(String.valueOf(obj.getDouble("pkt_duration_time")));
							} catch (Exception e) {
								e.printStackTrace();
							}
							mediaFrameArrayList.add(mediaFrame);
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			if (shouldDelete) {
				deInitMediaNative();
			}
			return (MediaFrame[]) mediaFrameArrayList.toArray(new MediaFrame[0]);
		}
	}
}
