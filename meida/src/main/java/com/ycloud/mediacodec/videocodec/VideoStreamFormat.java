package com.ycloud.mediacodec.videocodec;

/**
 * Created by Administrator on 2016/9/19.
 */
public class VideoStreamFormat {
    int		iCodec;
    int 	iProfile;
    int  	iPicFormat;
    long  	iWidth;
    long 	iHeight;
    int 	iFrameRate;
    int 	iBitRate;
    long 	iEncodePreset;

    byte[] 	iReserve =null;
    int   	iReserveLen;
    int    	iRawCodecId;

    long  	iCapturePreset;
    long 	iCaptureOrientation;
}
