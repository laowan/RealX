package com.ycloud.mediacodec;

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

import android.util.Log;

import com.ycloud.api.process.IMediaListener;
import com.ycloud.common.Constant;
import com.ycloud.mediacodec.engine.snapshot.FramesExtractorEngine;
import com.ycloud.utils.ExecutorUtils;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by dzhj on 17/5/8.
 */

public class VideoSnapshot {

    private static final String TAG = VideoSnapshot.class.getSimpleName();

    FramesExtractorEngine mEngine;

    IMediaListener mMediaListener;

    String mSourcePath;
    String mOutputPath;


    public VideoSnapshot(String snapshotPath, float snapshotFrequency) {
        mEngine = new FramesExtractorEngine();
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

        mEngine.setProgressCallback(new FramesExtractorEngine.ProgressCallback() {
            @Override
            public void onProgress(final float progress) {
                mMediaListener.onProgress(progress);
            }
        });
        mEngine.setDataSource(inFileDescriptor);
        try {
            mEngine.transcodeVideo(mOutputPath);
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

    public void setPath(String sourcePath, String outputPath) {
        mSourcePath = sourcePath;
        mOutputPath = outputPath;
    }

    public void transcode() {
        ExecutorUtils.getBackgroundExecutor(TAG).execute(new Runnable() {
            @Override
            public void run() {
                try {
                    transcodeVideo();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void release() {

    }

    public void setMediaListener(IMediaListener listener) {
        mMediaListener = listener;

    }

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
         */
        void onTranscodeFailed(Exception exception);
    }

}
