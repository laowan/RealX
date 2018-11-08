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
package com.ycloud.mediacodec;

import android.util.Log;

import com.ycloud.VideoProcessTracer;
import com.ycloud.api.process.IMediaListener;
import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.process.MediaProbe;
import com.ycloud.common.Constant;
import com.ycloud.common.GlobalConfig;
import com.ycloud.mediacodec.engine.IMediaTranscoder;
import com.ycloud.mediacodec.engine.MediaTranscoderEngine;
import com.ycloud.mediacodec.format.IMediaFormatStrategy;
import com.ycloud.mediacodec.format.MediaFormatStrategy;
import com.ycloud.utils.ExecutorUtils;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

public class MediaTranscoderMediacodec implements IMediaTranscoder {
    private static final String TAG = MediaTranscoderMediacodec.class.getSimpleName();

    private IMediaFormatStrategy mOutFormatStrategy;
    MediaTranscoderEngine mEngine;

    IMediaListener mMediaListener;

    String mSourcePath;
    String mOutputPath;
    private int mRealWidth;
    private int mRealHeight;

    public MediaTranscoderMediacodec() {
        mOutFormatStrategy = new MediaFormatStrategy();
        mEngine = new MediaTranscoderEngine();
    }

    /**
     * Transcodes video file asynchronously.
     * Audio track will be kept unchanged.
     *
     * @throws IOException if input file could not be read.
     */
    private void transcodeVideo() throws IOException {

        FileInputStream fileInputStream = null;
        FileDescriptor inFileDescriptor;
        try {
            fileInputStream = new FileInputStream(mSourcePath);
            inFileDescriptor = fileInputStream.getFD();
        } catch (IOException e) {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException eClose) {
                    Log.e(TAG, "Can't close input stream: ", eClose);
                }
            }
            throw e;
        }

        mEngine.setProgressCallback(new MediaTranscoderEngine.ProgressCallback() {
            @Override
            public void onProgress(final float progress) {
                mMediaListener.onProgress(progress);
            }
        });
        mEngine.setDataSource(inFileDescriptor);
        try {
            mEngine.transcodeVideo(mOutputPath, mOutFormatStrategy);
            mMediaListener.onEnd();
        } catch (Exception e) {
            mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, e.getMessage());
        }

        if (fileInputStream != null) {
            try {
                fileInputStream.close();
            } catch (IOException eClose) {
                Log.e(TAG, "Can't close input stream: ", eClose);
            }
        }
    }

    @Override
    public void setPath(String sourcePath, String outputPath) {
        mSourcePath = sourcePath;
        mOutputPath =outputPath;
    }

    @Override
    public void setForceRotateAngle(float angle) {

    }

    @Override
    public void setVideoSize(int width, int height) {
        mOutFormatStrategy.setDemension(width,height);
    }

    @Override
    public void setCropField(int width, int height, int offsetX, int offsetY){

    }

    @Override
    public void setSnapshotPath(String snapshotPath) {
        mEngine.setSnapshotPath(snapshotPath);
    }

    @Override
    public void setSnapshotFrequency(float snapshotFrequency) {
        mEngine.setSnapshotFrequency(snapshotFrequency);
    }

    @Override
    public void setSnapshotFileType(String fileType) {

    }

    @Override
    public void setSnapshotPrefix(String prefix){

    }

    @Override
    public void setMediaTime(float startTime, float totalTime) {

    }

    @Override
    public void setFrameRate(int frameRate) {
        mOutFormatStrategy.setFrameRate(frameRate);
    }

    @Override
    public void setBitrate(int bitrate) {
        mOutFormatStrategy.setBitrate(bitrate);

    }

    @Override
    public void setBitrateRange(int minRate, int maxtRate, int bufsize) {

    }

    @Override
    public void setGop(int gop) {
        mOutFormatStrategy.setIFrameInternal(gop);

    }

    @Override
    public void setCrf(int crf) {

    }

    @Override
    public void transcode() {
        ExecutorUtils.getBackgroundExecutor(TAG).execute(new Runnable() {
            @Override
            public void run() {
                MediaInfo mediaInfo = MediaProbe.getMediaInfo(mSourcePath,true);

                if(mediaInfo == null) {
                    mMediaListener.onError(Constant.MediaNativeResult.FFMPEG_EXECUTE_FAIL, "file mediaprobe error");
                    return;
                }

                computeResolution(mediaInfo);
                setVideoSize(mRealWidth, mRealHeight);
                VideoProcessTracer.getInstace().setResolution(mRealWidth+"x"+mRealHeight);
                setBitrate(GlobalConfig.getInstance().getRecordConstant().TRANSCODE_BITRATE);
                mOutFormatStrategy.setFrameRate((int)mediaInfo.frame_rate);

                try {
                    transcodeVideo();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void computeResolution(MediaInfo mediaInfo) {
        mRealWidth = GlobalConfig.getInstance().getRecordConstant().TRANSCODE_WIDTH;
        mRealHeight = GlobalConfig.getInstance().getRecordConstant().TRANSCODE_HEIGHT;

        if (mediaInfo != null) {
            float videoRadio = (float) mediaInfo.width / mediaInfo.height;
            if (mediaInfo.v_rotate == 90.0D || mediaInfo.v_rotate == 270.0D) {
                // 旋转
//                        videoRadio = 1 / videoRadio;
                mRealWidth = GlobalConfig.getInstance().getRecordConstant().TRANSCODE_HEIGHT;
                mRealHeight = GlobalConfig.getInstance().getRecordConstant().TRANSCODE_WIDTH;
            }

            float radioW = 1.0f * mediaInfo.width / mRealWidth;
            float radioH = 1.0f * mediaInfo.height / mRealHeight;

            if (radioW > radioH) {
                mRealHeight = (int) (mRealWidth / videoRadio);
            } else {
                mRealWidth = (int) (mRealHeight * videoRadio);
            }
        }

        if (mRealHeight % 2 != 0) {
            mRealHeight = mRealHeight - 1;
        }

        if (mRealWidth % 2 != 0) {
            mRealWidth = mRealWidth - 1;
        }
    }


    @Override
    public void release() {

    }

    @Override
    public void setMediaListener(IMediaListener listener) {
        mMediaListener = listener;

    }

    @Override
    public void cancel() {

    }

    public interface Listener {
        /**
         * Called to notify progress.
         *
         * @param progress Progress in [0.0, 1.0] range, or negative value if progress is unknown.
         */
        void onTranscodeProgress(float progress);

        /**
         * Called when transcode completed.
         */
        void onTranscodeCompleted();

        /**
         * Called when transcode canceled.
         */
        void onTranscodeCanceled();

        /**
         * Called when transcode failed.
         *
         */
        void onTranscodeFailed(Exception exception);
    }

}
