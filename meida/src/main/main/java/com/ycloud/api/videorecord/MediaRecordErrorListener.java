package com.ycloud.api.videorecord;

import android.hardware.Camera;

/**
 * 视频录制错误回调监听器
 */
public interface MediaRecordErrorListener {
	int AUDIO_ERROR_GET_MIN_BUFFER_SIZE_NOT_SUPPORT = 1;
	int AUDIO_ERROR_CREATE_FAILED = 2;
	int CAMERCA_ERROR = 3;
	/*recorder has stopped*/
	int MR_MSG_STOPPED = 4;
	/*Media recorder has an error when stop*/
	int MR_MSG_STOP_ERROR = 5;
	/* statistics info*/
	int MR_MSG_RECORD_STATISTICS = 8;
	int MR_MSG_RECORD_START_ERROR = 9;

	/**
	 * Media server died. In this case, the application must release the
	 * Camera object and instantiate a new one.
	 * @see Camera.ErrorCallback
	 */
	int MR_MSG_CAMERA_SERVER_DIED = Camera.CAMERA_ERROR_SERVER_DIED; //100



	/**
	 * callback function with info or error message
	 *
	 * @param what    message type
	 * @param message message string content, can be null
	 */
	void onVideoRecordError(int what, String message);
}
