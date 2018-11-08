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
package com.ycloud.mediacodec.engine;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.ycloud.common.Constant;
import com.ycloud.common.GlobalConfig;
import com.ycloud.mediacodec.format.MediaFormatExtraConstants;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.utils.YYLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// Refer: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoTrackTranscoder implements TrackTranscoder {
	private static final String TAG = VideoTrackTranscoder.class.getSimpleName();
	private static final int DRAIN_STATE_NONE = 0;
	private static final int DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1;
	private static final int DRAIN_STATE_CONSUMED = 2;

	private final MediaExtractor mExtractor;
	private final int mTrackIndex;
	private final MediaFormat mOutputFormat;
	private final QueuedMuxer mMuxer;
	private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
	private MediaCodec mDecoder;
	private MediaCodec mEncoder;
	private ByteBuffer[] mDecoderInputBuffers;
	private ByteBuffer[] mEncoderOutputBuffers;
	private MediaFormat mActualOutputFormat;
	private OutputSurface mDecoderOutputSurfaceWrapper;
	private InputSurface mEncoderInputSurfaceWrapper;
	private boolean mIsExtractorEOS;
	private boolean mIsDecoderEOS;
	private boolean mIsEncoderEOS;
	private boolean mDecoderStarted;
	private boolean mEncoderStarted;
	private long mWrittenPresentationTimeUs;
	private ByteBuffer mByteBuffer;
	private  int mSnapIndex;

	private long mLastSnapshotTime = 0;

	private int mRotation;
	private int mRequestSyncCnt = 0;

	private String mSnapshotPath;
	private float mSnapshotFrequency;

	public VideoTrackTranscoder(MediaExtractor extractor, int trackIndex, MediaFormat outputFormat, QueuedMuxer muxer,int rotation) {
		mExtractor = extractor;
		mTrackIndex = trackIndex;
		mOutputFormat = outputFormat;
		mMuxer = muxer;
		mRotation = rotation;
	}

	public void setSnapshotPath(String snapshotPath) {
		mSnapshotPath = snapshotPath;
	}

	public void setSnapshotFrequency(float snapshotFrequency) {
		mSnapshotFrequency = snapshotFrequency;
	}

	@Override
	public void setup() {
		mExtractor.selectTrack(mTrackIndex);
		try {
			mEncoder = MediaCodec.createEncoderByType(mOutputFormat.getString(MediaFormat.KEY_MIME));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		mEncoder.configure(mOutputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mEncoderInputSurfaceWrapper = new InputSurface(mEncoder.createInputSurface());
		mEncoderInputSurfaceWrapper.makeCurrent();
		mEncoder.start();
		mEncoderStarted = true;
		mEncoderOutputBuffers = mEncoder.getOutputBuffers();

		MediaFormat inputFormat = mExtractor.getTrackFormat(mTrackIndex);

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

		if(null != mSnapshotPath) {
			File file = new File(mSnapshotPath);
			if (!file.exists() || !file.isDirectory()) {
				file.mkdirs();
			}
		}
	}

	@Override
	public MediaFormat getDeterminedFormat() {
		return mActualOutputFormat;
	}

	@Override
	public boolean stepPipeline() {
		boolean busy = false;

		int status;
		while (drainEncoder(0) != DRAIN_STATE_NONE)
			busy = true;
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

	@Override
	public long getWrittenPresentationTimeUs() {
		return mWrittenPresentationTimeUs;
	}

	@Override
	public boolean isFinished() {
		return mIsEncoderEOS;
	}

	// TODO: CloseGuard
	@Override
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
		if (mEncoder != null) {
			if (mEncoderStarted)
				mEncoder.stop();
			mEncoder.release();
			mEncoder = null;
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
			mEncoder.signalEndOfInputStream();
			mIsDecoderEOS = true;
			mBufferInfo.size = 0;
		}
		boolean doRender = (mBufferInfo.size > 0);
		// NOTE: doRender will block if buffer (of encoder) is full.
		// Refer: http://bigflake.com/mediacodec/CameraToMpegTest.java.txt
		mDecoder.releaseOutputBuffer(result, doRender);
		if (doRender) {
			mDecoderOutputSurfaceWrapper.awaitNewImage();
			mDecoderOutputSurfaceWrapper.drawImage();

			if((mBufferInfo.presentationTimeUs -mLastSnapshotTime)>(1000000.0/mSnapshotFrequency)){
				takePicture();//第一帧不能截，会黑屏
				mLastSnapshotTime = mBufferInfo.presentationTimeUs;
			}

			mEncoderInputSurfaceWrapper.setPresentationTime(mBufferInfo.presentationTimeUs * 1000);
			requestSyncFrame();
			mEncoderInputSurfaceWrapper.swapBuffers();
		}
		return DRAIN_STATE_CONSUMED;
	}

	private int drainEncoder(long timeoutUs) {
		if (mIsEncoderEOS)
			return DRAIN_STATE_NONE;
		int result = mEncoder.dequeueOutputBuffer(mBufferInfo, timeoutUs);
		switch (result) {
		case MediaCodec.INFO_TRY_AGAIN_LATER:
			return DRAIN_STATE_NONE;
		case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
			if (mActualOutputFormat != null)
				throw new RuntimeException("Video output format changed twice.");
			mActualOutputFormat = mEncoder.getOutputFormat();
			mMuxer.setOutputFormat(QueuedMuxer.SampleType.VIDEO, mActualOutputFormat);
			return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
		case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
			mEncoderOutputBuffers = mEncoder.getOutputBuffers();
			return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
		}
		if (mActualOutputFormat == null) {
			throw new RuntimeException("Could not determine actual output format.");
		}

		if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
			mIsEncoderEOS = true;
			mBufferInfo.set(0, 0, 0, mBufferInfo.flags);
		}
		if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
			// SPS or PPS, which should be passed by MediaFormat.
			mEncoder.releaseOutputBuffer(result, false);
			return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
		}
		mMuxer.writeSampleData(QueuedMuxer.SampleType.VIDEO, mEncoderOutputBuffers[result], mBufferInfo);
		mWrittenPresentationTimeUs = mBufferInfo.presentationTimeUs;
		mEncoder.releaseOutputBuffer(result, false);
		return DRAIN_STATE_CONSUMED;
	}

	public void takePicture() {
		int width = mOutputFormat.getInteger(MediaFormat.KEY_WIDTH);
		int height = mOutputFormat.getInteger(MediaFormat.KEY_HEIGHT);
		try {
			if(mByteBuffer == null) {
				mByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
			}
			mByteBuffer.clear();
			mByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
			GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mByteBuffer);
			Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			bitmap.copyPixelsFromBuffer(mByteBuffer);

			Bitmap ret = transformBitmap(bitmap,mRotation);
            bitmap.recycle();
			saveToFile(ret);
			ret.recycle();
		} catch (Throwable e) {
			Log.e(TAG, "takePicture error:" + e.toString());
		}
	}

	private void saveToFile(final Bitmap bmp) {
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
		bmp.compress(Bitmap.CompressFormat.JPEG, GlobalConfig.getInstance().getRecordConstant().SNAPSHOT_QUALITY, out);
		try {
			out.flush();
			out.close();
		} catch (IOException e) {
			Log.e(TAG, "save to file failed: IOException happened:" + e.toString());
		} finally {
			out = null;
		}
	}

	private static Bitmap transformBitmap(Bitmap bitmap, int rotation) {
		float[] floats = new float[] { 1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f };
		Matrix m = new Matrix();
		m.setValues(floats);

		m.postRotate(rotation,bitmap.getWidth() / 2, bitmap.getHeight() / 2);

		Bitmap retBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

		return retBitmap;
	}

	/**
	 * Request that the encoder produce a sync frame "soon".
	 */
	public void requestSyncFrame() {
		if (mEncoder == null || !mEncoderStarted) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			try {
				Bundle bundle = new Bundle();
				bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
				mEncoder.setParameters(bundle);
				if(mRequestSyncCnt++ % 30 == 0) {
					YYLog.info(TAG, Constant.MEDIACODE_ENCODER + "requestSyncFrame, cnt="+mRequestSyncCnt);
				}
			} catch (Throwable t) {
				YYLog.error(this, Constant.MEDIACODE_ENCODER + "[exception] requestSyncFrame: " + t.toString());
			}
		} else {
			if(mRequestSyncCnt++ % 30 == 0) {
				YYLog.warn(TAG, Constant.MEDIACODE_ENCODER + "requestSyncFrame is only available on Android API 19+, cnt="+mRequestSyncCnt);
			}
		}
	}
}
