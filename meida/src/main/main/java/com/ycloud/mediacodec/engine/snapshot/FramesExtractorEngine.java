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
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;

import com.ycloud.mediacodec.InvalidOutputFormatException;
import com.ycloud.mediacodec.format.IMediaFormatStrategy;
import com.ycloud.mediacodec.utils.MediaExtractorUtils;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Internal engine, do not use this directly.
 */
// TODO: treat encrypted data
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class FramesExtractorEngine {
	private static final String TAG = FramesExtractorEngine.class.getSimpleName();
	private static final float PROGRESS_UNKNOWN = -1.0f;
	private static final long SLEEP_TO_WAIT_TRACK_TRANSCODERS = 10;
	private static final long PROGRESS_INTERVAL_STEPS = 10;
	private FileDescriptor mInputFileDescriptor;
	private VideoToImages mVideoToImages;
	private MediaExtractor mExtractor;
	private volatile float mProgress;
	private ProgressCallback mProgressCallback;
	private long mDurationUs;

	/**
	 * Do not use this constructor unless you know what you are doing.
	 */
	public FramesExtractorEngine() {
	}

	public void setDataSource(FileDescriptor fileDescriptor) {
		mInputFileDescriptor = fileDescriptor;
	}

	public ProgressCallback getProgressCallback() {
		return mProgressCallback;
	}

	public void setProgressCallback(ProgressCallback progressCallback) {
		mProgressCallback = progressCallback;
	}

	/**
	 * NOTE: This method is thread safe.
	 */
	public double getProgress() {
		return mProgress;
	}

	/**
	 * Run video transcoding. Blocks current thread. Audio data will not be transcoded; original stream will be wrote to output file.
	 *
	 * @param outputPath
	 *            File path to output transcoded video file.
	 * @param formatStrategy
	 *            Output format strategy.
	 * @throws IOException
	 *             when input or output file could not be opened.
	 * @throws InvalidOutputFormatException
	 *             when output format is not supported.
	 * @throws InterruptedException
	 *             when cancel to transcode.
	 */
	public void transcodeVideo(String outputPath) throws IOException, InterruptedException {
		if (outputPath == null) {
			throw new NullPointerException("Output path cannot be null.");
		}
		if (mInputFileDescriptor == null) {
			throw new IllegalStateException("Data source is not set.");
		}
		try {
			// NOTE: use single extractor to keep from running out audio track fast.
			mExtractor = new MediaExtractor();
			mExtractor.setDataSource(mInputFileDescriptor);
			setupTrackTranscoders();
			runPipelines();
		} finally {
			try {
				if (mVideoToImages != null) {
					mVideoToImages.release();
					mVideoToImages = null;
				}

				if (mExtractor != null) {
					mExtractor.release();
					mExtractor = null;
				}
			} catch (RuntimeException e) {
				// Too fatal to make alive the app, because it may leak native resources.
				// noinspection ThrowFromFinallyBlock
				throw new Error("Could not shutdown extractor, codecs and muxer pipeline.", e);
			}
		}
	}

	private void setupTrackTranscoders() {

		MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
		mediaMetadataRetriever.setDataSource(mInputFileDescriptor);

		int rotation = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));

		try {
			mDurationUs = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
		} catch (NumberFormatException e) {
			mDurationUs = -1;
		}
		Log.d(TAG, "Duration (us): " + mDurationUs+", rotation:" + rotation);

		MediaExtractorUtils.TrackResult trackResult = MediaExtractorUtils.getFirstVideoAndAudioTrack(mExtractor);


		mVideoToImages = new VideoToImages(mExtractor, trackResult.mVideoTrackIndex,rotation);
		mVideoToImages.setup();
		mExtractor.selectTrack(trackResult.mVideoTrackIndex);
	}

	private void runPipelines() {
		long loopCount = 0;
		if (mDurationUs <= 0) {
			float progress = PROGRESS_UNKNOWN;
			mProgress = progress;
			if (mProgressCallback != null)
				mProgressCallback.onProgress(progress); // unknown
		}
		while (!mVideoToImages.isFinished()) {
			boolean stepped = mVideoToImages.stepPipeline();
			loopCount++;
			if (mDurationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0) {
				double videoProgress = mVideoToImages.isFinished() ? 1.0 : Math.min(1.0, (double) mVideoToImages.getWrittenPresentationTimeUs() / mDurationUs);
				mProgress = (float) videoProgress;
				if (mProgressCallback != null)
					mProgressCallback.onProgress(mProgress);
			}
			if (!stepped) {
				try {
					Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS);
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
		}
	}

	public interface ProgressCallback {
		/**
		 * Called to notify progress. Same thread which initiated transcode is used.
		 *
		 * @param progress
		 *            Progress in [0.0, 1.0] range, or negative value if progress is unknown.
		 */
		void onProgress(float progress);
	}
}
