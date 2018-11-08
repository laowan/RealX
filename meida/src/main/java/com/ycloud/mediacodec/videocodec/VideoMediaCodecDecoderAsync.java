package com.ycloud.mediacodec.videocodec;

import android.media.MediaFormat;
import android.provider.MediaStore;
import android.view.Surface;

import com.ycloud.mediacodec.AbstractMediaCodecDecoderAsync;

/**
 * Created by Kele on 2018/1/3.
 */
//解码到surface上去.
public class VideoMediaCodecDecoderAsync extends AbstractMediaCodecDecoderAsync {

    public VideoMediaCodecDecoderAsync(MediaFormat mediaFormat, Surface surface, ISampleBufferQueue queue) {
        super(queue);
        startDecode(mediaFormat, surface);
    }
}
