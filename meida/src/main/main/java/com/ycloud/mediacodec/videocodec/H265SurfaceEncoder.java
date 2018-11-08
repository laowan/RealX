package com.ycloud.mediacodec.videocodec;

/**
 * Created by lookatmeyou on 2016/4/22.
 */
public class H265SurfaceEncoder extends HardSurfaceEncoder {
    private static final String TAG = "H265SurfaceEncoder";
    private static final String MIME = "video/hevc";

    public H265SurfaceEncoder(long eid) {
        super(TAG, MIME, eid);
    }
}
