package com.ycloud.mediarecord;

public class VideoRecordException extends Exception {
	private static final long serialVersionUID = 1L;

	public final static int ERROR_CODE_OPEN_CAMERA_FAIL = -1;
	public final static int ERROR_CODE_OPEN_FILE_FAIL = -3;

	private int mErrorCode = 0;

	public VideoRecordException(String msg) {
		super(msg);
	}

	public VideoRecordException(int error_code, String msg) {
		super(msg);
		mErrorCode = error_code;
	}

	public int getErrorCode() {
		return mErrorCode;
	}
}