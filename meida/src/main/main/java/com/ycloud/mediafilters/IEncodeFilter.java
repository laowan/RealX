package com.ycloud.mediafilters;


import com.ycloud.mediacodec.VideoEncoderType;

/**
 * Created by kele on 2016/11/21.
 */

public abstract class IEncodeFilter extends AbstractYYMediaFilter
{
    public boolean startEncode() {return false;}
    public void stopEncode() {};
    public boolean init() {return false;};
    public void deInit(){};
    public void adjustBitRate(final int bitRateInKbps) {}
    public void setEncoderListener(IEncoderListener listener){};
    public void requestSyncFrame(){};
    public VideoEncoderType getEncoderFilterType() {return VideoEncoderType.HARD_ENCODER_H264;}
}
