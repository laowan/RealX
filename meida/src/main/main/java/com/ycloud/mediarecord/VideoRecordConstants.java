package com.ycloud.mediarecord;

import android.hardware.Camera;

public class VideoRecordConstants {

	public static final int VIDEO_RECORD_VER_10 = 1;
	public static final int VIDEO_RECORD_VER_20 = 2;

	/* facing front camera id */
	public static final int FRONT_CAMERA = Camera.CameraInfo.CAMERA_FACING_FRONT;

	/* facing back camera id */
	public static final int BACK_CAMERA = Camera.CameraInfo.CAMERA_FACING_BACK;
	
	/* camera mode auto */
	public static final int FOCUS_MODE_AUTO = 0;

	/* camera mode continuous video */
	public static final int FOCUS_MODE_CONTINUOUS_VIDEO = 1; /*VideoRecordInit用int类型*/
	//public static final String FOCUS_MODE_CONTINUOUS_VIDEO = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;

	/*
	 * When user touch the video preview view and the camera focus success, this event will be notified to user if a VideoRecordTouchListener interface is set
	 */
	public static final int FOCUS_EVENT_DONE = 0x89;
	public static final int FOCUS_EVENT = 0x90;
	/*
	 * When user double click the video preview view and the camera zoom success, this event will be notified to user if a VideoRecordTouchListener interface is set.
	 */
	public static final int ZOOM_EVENT = 0x91;
	/* zoom in */
	public static final int ZOOM_IN = 0xA0;
	/* zoom out */
	public static final int ZOOM_OUT = 0xA1;
	/* double click*/
	public static final int DOUBLE_EVENT = 0xA2;
	/* video maximum frame rate */
	public static final int MAX_FRAME_RATE = 30;
	/* video minimum frame rate */
	public static final int MIN_FRAME_RATE = 1;

	public static String PARAMS_KEY_ENABLE_AUDIO_RECORD="params_key_enable_audio_record";

}
