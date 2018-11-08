/*
 * Copyright (C) 2014 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ycloud.mediacodec.engine.snapshot;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.ycloud.mediacodec.engine.InputSurface;
import com.ycloud.mediacodec.engine.OutputSurface;
import com.ycloud.mediacodec.format.MediaFormatExtraConstants;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.utils.OpenGlUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

// Refer: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoToImages {
	private static final String TAG = VideoToImages.class.getSimpleName();
	private static final int DRAIN_STATE_NONE = 0;
	private static final int DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1;
	private static final int DRAIN_STATE_CONSUMED = 2;

	private final MediaExtractor mExtractor;
	private final int mTrackIndex;
	private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
	private MediaCodec mDecoder;
	private ByteBuffer[] mDecoderInputBuffers;
	private MediaFormat mActualOutputFormat;
	private OutputSurface mDecoderOutputSurfaceWrapper;
	private InputSurface mEncoderInputSurfaceWrapper;
	private boolean mIsExtractorEOS;
	private boolean mIsDecoderEOS;
	private boolean mDecoderStarted;
	private long mWrittenPresentationTimeUs;
	private ByteBuffer mByteBuffer;
	private  int mSnapIndex;

	private long mLastSnapshotTime = 0;

	private int mRotation;

	private SurfaceTexture mSurfaceTexture;

	final private int FRAMEBUFFER_NUM = 1;
	private int[] mFrameBuffer = null;
	private int[] mFrameBufferTexture = null;
	int mWidth;
	int mHeight;

	private String mSnapshotPath;
	private float mSnapshotFrequency;

	Handler mSaveHandler;

	public VideoToImages(MediaExtractor extractor, int trackIndex, int rotation) {
		mExtractor = extractor;
		mTrackIndex = trackIndex;
		mRotation = rotation;
	}

	public void setup() {
		mExtractor.selectTrack(mTrackIndex);

		int textures[] = new int[1];
		GLES20.glGenTextures(1, textures, 0);
		mSurfaceTexture = new SurfaceTexture(textures[0]);
		Surface surface = new Surface(mSurfaceTexture);

		mEncoderInputSurfaceWrapper = new InputSurface(surface);
		mEncoderInputSurfaceWrapper.makeCurrent();

		MediaFormat inputFormat = mExtractor.getTrackFormat(mTrackIndex);
		mWidth= inputFormat.getInteger(MediaFormat.KEY_WIDTH);
		mHeight= inputFormat.getInteger(MediaFormat.KEY_HEIGHT);

		if (inputFormat.containsKey(MediaFormatExtraConstants.KEY_ROTATION_DEGREES)) {
			// Decoded video is rotated automatically in Android 5.0 lollipop.
			// Turn off here because we don't want to encode rotated one.
			// refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
			inputFormat.setInteger(MediaFormatExtraConstants.KEY_ROTATION_DEGREES, 0);
		}

		mDecoderOutputSurfaceWrapper = new OutputSurface();
		try {
			mDecoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		mDecoder.configure(inputFormat, mDecoderOutputSurfaceWrapper.getSurface(), null, 0);
		mDecoder.start();
		mDecoderStarted = true;
		mDecoderInputBuffers = mDecoder.getInputBuffers();
		mSnapIndex = 0;
		mLastSnapshotTime =0;

		File file = new File(mSnapshotPath);
		if(!file.exists() || !file.isDirectory()){
			file.mkdirs();
		}

		HandlerThread ht = new HandlerThread(SDK_NAME_PREFIX +VideoToImages.class.getSimpleName());
		ht.start();
		mSaveHandler = new Handler(ht.getLooper());
	}

	public MediaFormat getDeterminedFormat() {
		return mActualOutputFormat;
	}

	public boolean stepPipeline() {
		boolean busy = false;

		int status;
/*		while (drainEncoder(0) != DRAIN_STATE_NONE)
			busy = true;*/
		do {
			status = drainDecoder(0);
			if (status != DRAIN_STATE_NONE)
				busy = true;
			// NOTE: not repeating to keep from deadlock when encoder is full.
		} while (status == DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY);
		while (drainExtractor(0) != DRAIN_STATE_NONE)
			busy = true;

		return busy;
	}

	public long getWrittenPresentationTimeUs() {
		return mWrittenPresentationTimeUs;
	}

	public boolean isFinished() {
		return mIsDecoderEOS;
	}

	public void release() {
		if (mDecoderOutputSurfaceWrapper != null) {
			mDecoderOutputSurfaceWrapper.release();
			mDecoderOutputSurfaceWrapper = null;
		}
		if (mEncoderInputSurfaceWrapper != null) {
			mEncoderInputSurfaceWrapper.release();
			mEncoderInputSurfaceWrapper = null;
		}
		if (mDecoder != null) {
			if (mDecoderStarted)
				mDecoder.stop();
			mDecoder.release();
			mDecoder = null;
		}

		if(mSaveHandler != null){
			mSaveHandler.getLooper().quit();
		}
	}

	private int drainExtractor(long timeoutUs) {
		if (mIsExtractorEOS)
			return DRAIN_STATE_NONE;
		int trackIndex = mExtractor.getSampleTrackIndex();
		if (trackIndex >= 0 && trackIndex != mTrackIndex) {
			return DRAIN_STATE_NONE;
		}
		int result = mDecoder.dequeueInputBuffer(timeoutUs);
		if (result < 0)
			return DRAIN_STATE_NONE;
		if (trackIndex < 0) {
			mIsExtractorEOS = true;
			mDecoder.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
			return DRAIN_STATE_NONE;
		}
		int sampleSize = mExtractor.readSampleData(mDecoderInputBuffers[result], 0);
		boolean isKeyFrame = (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
		mDecoder.queueInputBuffer(result, 0, sampleSize, mExtractor.getSampleTime(), isKeyFrame ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0);
		mExtractor.advance();
		return DRAIN_STATE_CONSUMED;
	}

	private int drainDecoder(long timeoutUs) {
		if (mIsDecoderEOS)
			return DRAIN_STATE_NONE;
		int result = mDecoder.dequeueOutputBuffer(mBufferInfo, timeoutUs);
		switch (result) {
		case MediaCodec.INFO_TRY_AGAIN_LATER:
			return DRAIN_STATE_NONE;
		case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
		case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
			return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
		}
		if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
			mIsDecoderEOS = true;
			mBufferInfo.size = 0;
		}
		boolean doRender = (mBufferInfo.size > 0);
		// NOTE: doRender will block if buffer (of encoder) is full.
		// Refer: http://bigflake.com/mediacodec/CameraToMpegTest.java.txt
		mDecoder.releaseOutputBuffer(result, doRender);
		if (doRender) {
			mDecoderOutputSurfaceWrapper.awaitNewImage();

			if (null == mFrameBuffer && null == mFrameBufferTexture) {
				mFrameBuffer = new int[FRAMEBUFFER_NUM];
				mFrameBufferTexture = new int[FRAMEBUFFER_NUM];
				OpenGlUtils.createFrameBuffer(mWidth, mHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);
			}
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
			GLES20.glViewport(0, 0, mWidth, mHeight);
			mDecoderOutputSurfaceWrapper.drawImage();
			mEncoderInputSurfaceWrapper.setPresentationTime(mBufferInfo.presentationTimeUs * 1000);

			if(mLastSnapshotTime == 0 ||((mBufferInfo.presentationTimeUs -mLastSnapshotTime))>(1000000.0/mSnapshotFrequency)){
				mLastSnapshotTime = mBufferInfo.presentationTimeUs;
				takePicture();
			}
			mEncoderInputSurfaceWrapper.swapBuffers();
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
		}
		return DRAIN_STATE_CONSUMED;
	}

	public void takePicture() {
		try {
			if(mByteBuffer == null) {
				mByteBuffer = ByteBuffer.allocateDirect(mWidth * mHeight * 4);
			}
			mByteBuffer.clear();
			mByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
			GLES20.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mByteBuffer);
			Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
			bitmap.copyPixelsFromBuffer(mByteBuffer);

			Bitmap ret = transformBitmap(bitmap,mRotation);
            bitmap.recycle();
			saveToFile(ret);
		} catch (Throwable e) {
			Log.e(TAG, "takePicture error:" + e.toString());
		}
	}

	private void saveToFile(final Bitmap bmp) {
		mSaveHandler.post(new Runnable() {
			@Override
			public void run() {
				mSnapIndex++;
				FileOutputStream out = null;
				String finalFilePath = mSnapshotPath + File.separator + mSnapIndex + ".jpg";
				try {
					out = new FileOutputStream(finalFilePath);
				} catch (FileNotFoundException e) {
					Log.e(TAG, finalFilePath + "not found:" + e.toString());
				}
				if (out == null) {
					return;
				}
				bmp.compress(Bitmap.CompressFormat.JPEG, 50, out);
				try {
					out.flush();
					out.close();
				} catch (IOException e) {
					Log.e(TAG, "save to file failed: IOException happened:" + e.toString());
				} finally {
					bmp.recycle();
					out = null;
				}
			}
		});

	}

	private static Bitmap transformBitmap(Bitmap bitmap, int rotation) {
		float[] floats = new float[] { 1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f };
		Matrix m = new Matrix();
		m.setValues(floats);

		m.postRotate(rotation,bitmap.getWidth() / 2, bitmap.getHeight() / 2);

		Bitmap retBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

		return retBitmap;
	}
}
