package com.ycloud.mediacodec.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.ycloud.common.FileUtils;
import com.ycloud.common.ResourceUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

public class HwCodecConfig {
	private static Support h264DecoderSupport = Support.UNCERTAIN;
	private static Support h264EncoderSupport = Support.UNCERTAIN;

	private final static String URL = "http://bi2.duowan.com/upgrade/Hw264Config";
	private final static String CODEC_COFING_NAME = "Hw264Config";
	private final static String H264_HARDWARE_DECODE = "h264_hardware_decode";
	private final static String H264_HARDWARE_ENCODE = "h264_hardware_encode";
	private static Context mCtx;
	private static String mCodecConfigDir;

	public static Support getH264DecoderSupport() {
		return h264DecoderSupport;
	}

	public static Support getH264EncoderSupport() {
		return h264EncoderSupport;
	}

	public static AtomicBoolean firstConfig = new AtomicBoolean(true);

	public static void doCodecConfigOnce() {
		if (firstConfig.get()) {
			boolean h264DecodeOn = false;
			boolean h264EncodeOn = false;
			/*
			 * if (mediaVideo == null) { // YLog.error("HwCodecConfig", "mediaVideo is null"); return; }
			 */

			if (HwCodecConfig.getH264DecoderSupport() == Support.UNSUPPORTED || !H264DecoderUtils.IsAvailable()) {
				h264DecodeOn = false;
				// configs.put(MediaVideoMsg.MediaConfigKey.CCK_HARDWARE_DECODE, 0);
				// YLog.info("HwCodecConfig", "set h264 software decoder");
			} else {
				h264DecodeOn = true;
				// configs.put(MediaVideoMsg.MediaConfigKey.CCK_HARDWARE_DECODE, 1);
				// YLog.info("HwCodecConfig", "set h264 hardware decoder");
			}

			if (HwCodecConfig.getH264EncoderSupport() == Support.UNSUPPORTED || !H264SurfaceEncoderUtils.IsAvailable()) {
				h264EncodeOn = false;
				// configs.put(MediaVideoMsg.MediaConfigKey.CCK_HARDWARE_ENCODE, 0);
				// YLog.info("HwCodecConfig", "set h264 software encoder");
			} else {
				h264EncodeOn = true;
				// configs.put(MediaVideoMsg.MediaConfigKey.CCK_HARDWARE_ENCODE, 1);
				// YLog.info("HwCodecConfig", "set h264 hardware encoder");
			}

			if (null != mCtx) {
				SharedPreferences configPreferences = mCtx.getSharedPreferences(CODEC_COFING_NAME, 0);
				SharedPreferences.Editor editor = configPreferences.edit();
				editor.putBoolean(H264_HARDWARE_DECODE, h264DecodeOn);
				editor.putBoolean(H264_HARDWARE_ENCODE, h264EncodeOn);
				editor.commit();
			}

			// mediaVideo.notifyHardwareCodecConfigured(h264DecodeOn, h264EncodeOn, h265DecodeOn, h265EncodeOn);
			firstConfig.set(false);
		}
	}

	public static boolean isHw264DecodeEnabled() {
		if (null != mCtx) {
			SharedPreferences configPreferences = mCtx.getSharedPreferences(CODEC_COFING_NAME, 0);
			return configPreferences.getBoolean(H264_HARDWARE_DECODE, false);
		}
		return false;
	}

	public static boolean isHw264EncodeEnabled() {
		if (null != mCtx) {
			SharedPreferences configPreferences = mCtx.getSharedPreferences(CODEC_COFING_NAME, 0);
			return configPreferences.getBoolean(H264_HARDWARE_ENCODE, false);
		}
		return false;
	}

	public static void setContext(Context context) {
		mCtx =context;
	}

	public void AsyncLoad(Context ctx) {
		mCtx = ctx;
		mCodecConfigDir = FileUtils.getDiskCacheDir(ctx) + File.separator + "hwconfig" + File.separator;
		FileUtils.createDir(mCodecConfigDir);
		LoadThread lt = new LoadThread();
		Thread th = new Thread(lt);
		th.start();
	}

	class LoadThread implements Runnable {
		@Override
		public void run() {
			try {
				JSONObject js = getJsonConfig();
				if (0 != js.getInt("code")) {
					return;
				}

				if (IsInList(js, "DecoderBlack")) {
					h264DecoderSupport = Support.UNSUPPORTED;
				} else  if (IsInList(js, "DecoderWhite")) {
					h264DecoderSupport = Support.SUPPORTED;
				} else {
					h264DecoderSupport = Support.UNCERTAIN;
				}

				if (IsInList(js, "EncoderBlack")) {
					h264EncoderSupport = Support.UNSUPPORTED;
				} else if (IsInList(js, "EncoderWhite")) {
					h264EncoderSupport = Support.SUPPORTED;
				} else {
					h264EncoderSupport = Support.UNCERTAIN;
				}

				if (Build.VERSION.SDK_INT < 18) {
					h264DecoderSupport = Support.UNSUPPORTED;
					h264EncoderSupport = Support.UNSUPPORTED;
				}
			} catch (Exception e) {
				// YLog.error("HwCodecConfig", "Load Error " + e.getMessage());
			} finally {
				doCodecConfigOnce();
			}
		}

		JSONObject getJsonConfig() {
			try {
				JSONObject js;
				File hwcf = new File(mCodecConfigDir, CODEC_COFING_NAME);
				if (!hwcf.exists() || !InToday(hwcf.lastModified())) {
					// copyFaceModeBaseToSdcard();// Hw264Config文件暂时使用从assets拷备到sd卡方式，以后会改成请求服务器下载
					try {
						String str = get(URL);
						js = new JSONObject(str);
						FileWriter fw = new FileWriter(hwcf);
						fw.write(str);
						fw.close();
						return js;
					} catch (Exception e) {
						Log.e("HwCodecConfig", "getURL " + e.getMessage());
					}
				}
				FileInputStream fi = new FileInputStream(hwcf);
				ByteArrayOutputStream bo = new ByteArrayOutputStream();
				byte[] buf = new byte[1024];
				int tl;
				while ((tl = fi.read(buf, 0, 1024)) > 0) {
					bo.write(buf, 0, tl);
				}
				fi.close();
				String str = bo.toString();
				js = new JSONObject(str);
				return js;
			} catch (Exception e) {
				e.printStackTrace();
				Log.e("HwCodecConfig", "getJsonConfig " + e.getMessage());
			}
			return null;
		}

		boolean IsInList(JSONObject js, String listName) {
			try {
				JSONArray list = js.getJSONArray(listName);
				if (null == list || list.length() == 0) {
					return false;
				}
				int cnt = list.length();
				int i;
				for (i = 0; i < cnt; ++i) {
					JSONObject jo = list.getJSONObject(i);
					String model = jo.getString("model");
					if (Build.MODEL.equals(model)) {
						return true;
					}
				}
			} catch (Exception e) {
				// YLog.error("HwCodecConfig", "IsInList " + e.getMessage());
			}
			return false;
		}
	}

	static boolean InToday(long ts) {
		Calendar ca = Calendar.getInstance();
		ca.set(Calendar.HOUR_OF_DAY, 0);
		ca.set(Calendar.MINUTE, 0);
		ca.set(Calendar.SECOND, 0);
		ca.set(Calendar.MILLISECOND, 0);
		return ts > ca.getTimeInMillis();
	}

	static String get(String url) throws Exception {
		URL getUrl = new URL(url);
		HttpURLConnection connection = (HttpURLConnection) getUrl.openConnection();
		connection.connect();
		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder sb = new StringBuilder();
		String lines;
		while ((lines = reader.readLine()) != null) {
			sb.append(lines);
		}
		reader.close();
		connection.disconnect();
		return sb.toString();
	}

	public enum Support {
		SUPPORTED(0), UNSUPPORTED(1), UNCERTAIN(2);
		Support(int sp) {
			this.sp = sp;
		}

		private int sp;
	}
}
