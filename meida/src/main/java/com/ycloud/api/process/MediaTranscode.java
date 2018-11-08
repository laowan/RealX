package com.ycloud.api.process;

import android.content.Context;

import com.ycloud.VideoProcessTracer;
import com.ycloud.api.common.CodecMode;
import com.ycloud.mediacodec.MediaTranscoderMediacodec;
import com.ycloud.mediacodec.engine.IMediaTranscoder;
import com.ycloud.mediacodec.utils.MediacodecUtils;
import com.ycloud.mediaprocess.MediaTranscodeFfmpeg;
import com.ycloud.utils.YYLog;

/**
 * 视频转码，对外部导入视频的参数做一些调节，方便视频在编辑页处理
 * 转码的gop，fps，bitrate等参数是内部设置的，不提供外部设置接口
 * 转码的分辨率，旋转角度等可以设置
 */
public class MediaTranscode {

    private static  final  String TAG = MediaTranscode.class.getSimpleName();
    IMediaTranscoder mVideoTranscode;
    private Context mContext = null;
    private boolean mIsMediacodec = true;

    public static CodecMode mCodecMode = CodecMode.MEDIACODEC;

    public MediaTranscode() {
        VideoProcessTracer.getInstace().reset();
        mIsMediacodec = MediacodecUtils.getCodecType(mCodecMode);

        //转码逻辑统一切成暂时切成软编码测试
        mIsMediacodec = false;

        if (mIsMediacodec) {
            mVideoTranscode = new MediaTranscoderMediacodec();
        } else {
            mVideoTranscode = new MediaTranscodeFfmpeg();
        }
    }

    public MediaTranscode(Context context) {
        mContext = context.getApplicationContext();
        VideoProcessTracer.getInstace().reset();
        mIsMediacodec = MediacodecUtils.getCodecType(mCodecMode);

        //转码逻辑统一切成暂时切成软编码测试
        mIsMediacodec = false;

        if (mIsMediacodec) {
            mVideoTranscode = new MediaTranscoderMediacodec();
        } else {
            mVideoTranscode = new MediaTranscodeFfmpeg(mContext);
        }
    }

    /**
     * 设置文件路径
     *
     * @param sourcePath 源文件
     * @param outputPath 输出文件
     */
    public void setPath(String sourcePath, String outputPath) {
        mVideoTranscode.setPath(sourcePath, outputPath);
    }

    /**
     * 设置转码的视频分辨率
     * @param width
     * @param height
     */
    public void setVideoResolution(int width, int height) {
        mVideoTranscode.setVideoSize(width, height);
    }

    /**
     * 设置转码时的视频旋转角度
     *
     * @param angle 视频旋转角度,目前只支持 +/-  90/180/270 三个方向， 支持顺/逆时针两个方向
     */
    public void setForceRotateAngle(float angle) {
        YYLog.info(TAG, "setForceRotateAngle " + angle);
        float absAngle = Math.abs(angle);
        if (absAngle != 90.0f && absAngle != 180.0f && absAngle != 270.0f) {
            return;
        }

        if (angle < 0) {  // ffmpeg  转码时，传入负的旋转角度，转码速度翻了好几倍~！坑爹。
            angle += 360;
            YYLog.info(TAG, "new angle " + angle);
        }

        mVideoTranscode.setForceRotateAngle(angle);
    }

    /**
     * 执行转码
     */
    public void transcode() {
        mVideoTranscode.transcode();
    }

    /**
     * 释放资源
     */
    public void release() {
        mVideoTranscode.release();
    }

    /**
     * 设置进度及错误监听
     *
     * @param listener
     */
    public void setMediaListener(IMediaListener listener) {
        mVideoTranscode.setMediaListener(listener);
    }

    /**
     * 取消转码
     */
    public void cancel(){
        mVideoTranscode.cancel();
    }

    public void  setRecordSnapShot(String snapShotPath){
        if(mVideoTranscode != null) {
            mVideoTranscode.setSnapshotPath(snapShotPath);
        }
    }
    public void setSnapFrequency(float snapFrequency){
        if(mVideoTranscode != null) {
            mVideoTranscode.setSnapshotFrequency(snapFrequency);
        }
    }

    public void setCropField(int width, int height, int offsetX, int offsetY){
        if (mVideoTranscode != null){
            mVideoTranscode.setCropField(width, height, offsetX, offsetY);
        }
    }

    public void setMediaTime(float startTime, float totalTime){
        if (mVideoTranscode != null){
            mVideoTranscode.setMediaTime(startTime, totalTime);
        }
    }
    /**
     * 设置手Y的版本号
     * @param yyVersion
     */
    public void setYyVersion(String yyVersion){
        VideoProcessTracer.getInstace().setYyVersion(yyVersion);
    }
}
