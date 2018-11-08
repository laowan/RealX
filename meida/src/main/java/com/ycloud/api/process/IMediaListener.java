package com.ycloud.api.process;

/**
 * 进度及错误回调监听器
 */
public interface IMediaListener {
	/**
	 * 进度回调
	 *
	 * @param progress
	 *
	 */
	void onProgress(float progress);

	/**
	 * 错误消息和类型
	 * @param errType 错误类型
	 * @param errMsg  错误消息
	 */
	void onError(int errType, String errMsg);

	/**
	 * 结束
	 */
	void onEnd();

}