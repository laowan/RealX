/*
 * Copyright 2016 Mario Guggenberger <mg@protyposis.net>
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

package com.ycloud.svplayer;

import android.media.MediaFormat;

import java.io.IOException;

class AudioDecoder extends MediaDecoder {

    private AudioPlayback mAudioPlayback;

    public AudioDecoder(MediaExtractor extractor,int trackIndex,
                        AudioPlayback audioPlayback)
            throws IOException {
        super(extractor,trackIndex,CodecType.AUDIO);
        mAudioPlayback = audioPlayback;
        reinitCodec();
    }

    @Override
    protected void configureCodec(ICodec codec, MediaFormat format) {
        super.configureCodec(codec, format);
        mAudioPlayback.init(format);
    }

    @Override
    protected boolean shouldDecodeAnotherFrame() {
        return mAudioPlayback.getQueueBufferTimeUs() < 200000;
    }

    @Override
    public void renderFrame(FrameInfo frameInfo) {
        mAudioPlayback.write(frameInfo.data, frameInfo.presentationTimeUs);
        releaseFrame(frameInfo);
    }

    @Override
    protected void onOutputFormatChanged(MediaFormat format) {
        mAudioPlayback.init(format);
    }
}
