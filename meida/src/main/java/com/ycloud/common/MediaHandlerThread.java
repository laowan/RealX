package com.ycloud.common;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;


public class MediaHandlerThread {
	private HandlerThread mHandlerThread = null;
	private MyHandler mHandler = null;
	private Handler mMainHandler = null;
	private IMediaRunnable mIMediaRunnable = null;

	public interface IMediaRunnable {
		public boolean run();

		public void callbackResult(boolean isSuccess);
	};

	public MediaHandlerThread(String threadName) {
		super();
		if (null == mHandlerThread) {
			mHandlerThread = new HandlerThread(SDK_NAME_PREFIX +threadName);
			mHandlerThread.start();
			mHandler = new MyHandler(mHandlerThread.getLooper());
		}
	}

	public void setmMainHandler(Handler handler, final IMediaRunnable iMediaRunnable) {
		this.mMainHandler = handler;
		this.mIMediaRunnable = iMediaRunnable;
	}

	public void sendMessage(Message msg) {
		if (null != mHandler)
			mHandler.sendMessage(msg);
	}

	public void start() {
		if (null != mHandler)
			mHandler.sendEmptyMessage(1);
	}

	public void release() {
		if (null != mHandlerThread) {
			mHandlerThread.quit();
			mHandlerThread = null;
		}
		if (null != mHandler) {
			mHandler.removeCallbacksAndMessages(null);
			mHandler = null;
		}
	}

	class MyHandler extends Handler {
		MyHandler() {
		}

		MyHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			boolean result = false;
			if (null != mIMediaRunnable) {
				result = mIMediaRunnable.run();
				mIMediaRunnable.callbackResult(result);
			}

			if (null != mMainHandler) {
				Message toMainMsg = mMainHandler.obtainMessage();
				Bundle bundle = new Bundle();
				bundle.putBoolean("resutl", result);
				toMainMsg.setData(bundle);
				mMainHandler.sendMessage(toMainMsg);
			}
		}
	}
	
	public void postRunning(Runnable run)
	{
		if (null != mHandler)
			mHandler.post(run);
	}
}
