package com.ycloud.mediacodec.videocodec;

/**
 * Created by Hui on 2015/12/17.
 */
public class H264SurfaceEncoder extends HardSurfaceEncoder {
    private static final String TAG = "H264SurfaceEncoder";
    private static final String MIME = "video/avc";

    public H264SurfaceEncoder(long eid) {
        super(TAG, MIME, eid);
    }
}
