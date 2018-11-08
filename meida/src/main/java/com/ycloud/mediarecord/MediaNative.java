package com.ycloud.mediarecord;

import android.os.Bundle;
import android.util.Log;

import com.ycloud.player.annotations.CalledByNative;
import com.ycloud.utils.YYLog;

public class MediaNative {
	private static String TAG=MediaNative.class.getSimpleName();

	private static IFfmpegCallback mFfmpegCallback;

	static {
		try {
			System.loadLibrary("audioengine");
			System.loadLibrary("ffmpeg-neon");
			System.loadLibrary("ycmediayuv");
			System.loadLibrary("ycmedia");
		} catch (UnsatisfiedLinkError e) {
			YYLog.e("MediaNative", "load so fail");
			e.printStackTrace();
		}
	}

	public final static int libffmpeg_cmd_snapshot_multiple = 2;
	public final static int libffmpeg_cmd_video_concat = 3;
	public final static int libffmpeg_cmd_probe = 4;
	public final static int libffmpeg_cmd_transcode = 6;
	public final static int libffmpeg_cmd_video_effect = 8;
	public final static int libffmpeg_cmd_video_cut = 9;
	private VideoGpuFilter mVideoGpuFilter;

	public MediaNative() {
	}


	@CalledByNative
	static void  onEventCallback(Bundle b){
		if(mFfmpegCallback != null) {
			mFfmpegCallback.onCallback(b);
		}
	}

	public static void setFfmpegCallback(IFfmpegCallback callback){
		mFfmpegCallback =callback;
	}

	public void release() {
		setFfmpegCallback(null);
		if(mVideoGpuFilter != null) {
			mVideoGpuFilter.release();
			mVideoGpuFilter = null;
		}
	}

	@CalledByNative
	public static void nativeLogCallback(int log_level, byte[] log_Content) {
		try {

			String logContent = new String(log_Content, "UTF-8");
			if (log_level == Log.INFO) {
				YYLog.info(TAG, logContent);
			} else if (log_level == Log.WARN) {
				YYLog.warn(TAG,logContent);
			} else if (log_level == Log.ERROR) {
				YYLog.error(TAG,logContent);
			} else {
				YYLog.info(TAG,logContent);
			}
		} catch (Exception e) {
			YYLog.error(TAG, e.getMessage());
		}
	}

    public void setVideoGpuFilter(VideoGpuFilter videoGpuFilter) {
        mVideoGpuFilter = videoGpuFilter;
    }

	/* media process */
	public native String mediaProcessNative(int cmd_type, String cmd);
	
	/* cancel media process */
	public native void resetMediaProcessNative();
	public native void cancelMediaProcessNative();
	/* isFFmpeg running */
	public static native boolean mediaIsFFmpegRunningNative();
	public static native void mediaSetProgressIntervalNative(long progressInterval);

	/* isFFmepg process cancel */
	public static native boolean mediaIsFFmpegProcessCancelledNative();
}
