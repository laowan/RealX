package com.ycloud;

import android.hardware.Camera;
import android.os.Build;

import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.common.GlobalConfig;
import com.ycloud.utils.DeviceUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dzhj on 17/5/18.
 */

public class VideoProcessTracer {
    /**
     * 手机型号
     */
    private String mDeviceModel;
    /**
     * Android系统版本号
     */
    private int mAndroidVersion;

    /**
     * 分辨率：用WXH表示
     */
    private String mResolution;
    /**
     * 导出时的crf值
     */
    private int mCrf;
    /**
     *preset值，如superfast，yyveryfast等
     */
    private String mPreset;

    private int mFrameRate;

    private ArrayList<String> mVidePathList;

    /**
     * 保存每段录制信息的list
     */
    private List<RecordTracer> mRecordTracerList;

    private static VideoProcessTracer mVideoProcessTracer;
    /**
     * 导出视频时间
     */
    private String mExportTime;

    /**
     * yy版本号
     */
    private String mYyVersion;

    private VideoProcessTracer(){
        mRecordTracerList = new ArrayList<>();
    }

    public synchronized static VideoProcessTracer getInstace(){

        if(mVideoProcessTracer == null ) {
            mVideoProcessTracer = new VideoProcessTracer();
        }

        return  mVideoProcessTracer;

    }

    public void setExportTime(String exportTime) {
        mExportTime = exportTime;
    }

    public void setYyVersion(String yyVersion) {
        mYyVersion = yyVersion;
    }

    public static class RecordTracer{
        /**
         * 摄像头ID
         */
        private int mCameraId;
        /**
         * 若使用了美颜，则记录美颜程度，范围1～100
         */
        private int mBeautyFaceLevel;
        /**
         * 录制时采用的色表名字
         */
        private String mLutName = "";
        /**
         * 录制时采用的曝光模式 0：自动测光 1：点测光 2：Lock
         */
        private int mExposureMode = 0;

        public RecordTracer(int cameraId,int beautyFaceLevel){
            mCameraId =cameraId;
            mBeautyFaceLevel =beautyFaceLevel;
        }

        public void setLutName(String lutName){
            mLutName = lutName;
        }

        public void setExposureMode(int mode){
            mExposureMode = mode;
        }

        @Override
        public String toString(){
            StringBuilder sb = new StringBuilder();
            String cameraStr = "b";
            try {
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(mCameraId,cameraInfo);
                cameraStr= (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK ?"b":"f");
            }catch (Exception e){
                e.printStackTrace();
            }

            sb.append("["+cameraStr+"]");
            sb.append("["+mBeautyFaceLevel+"]");
            sb.append("["+mLutName+"]");
            sb.append("[em:"+mExposureMode+"]");
            return sb.toString();
        }
    }

    public String generateComment(){
        StringBuilder sb = new StringBuilder();
        if (SDKCommonCfg.getRecordModePicture()) {
            sb.append("YOYI");
        } else {
            sb.append("Soda");
        }
        sb.append("["+ mYyVersion +"]");
        sb.append("["+mDeviceModel+"]");
        sb.append("["+mAndroidVersion+"]");
        sb.append("["+mResolution+"]");
        sb.append("[crf:"+mCrf+"]");
        sb.append("["+mPreset+"]");
        sb.append("[f:"+mFrameRate+"]");

        if(mRecordTracerList.size()>0) {
            sb.append("[r]");
        } else {
            sb.append("[d]");
        }

        sb.append("[encode:"+ GlobalConfig.getInstance().getRecordConstant().VIDEO_ENCODE_TYPE +"]");

        sb.append("["+mExportTime+"]");

        for (RecordTracer recordTracer : mRecordTracerList) {
            sb.append("|");
            sb.append(recordTracer.toString());
        }
        sb.append("|");

        return sb.toString();
    }

    public void reset(){
        mResolution = null;
        mVidePathList = null;
        mRecordTracerList.clear();
        mDeviceModel= DeviceUtil.getPhoneModel().replaceAll("\\s+", "_");
        mAndroidVersion = Build.VERSION.SDK_INT;
    }

    public void setResolution(String resolution) {
        mResolution = resolution;
    }

    public void setCrf(int crf) {
        mCrf = crf;
    }

    public void setPreset(String preset) {
        mPreset = preset;
    }

    public void setVidePathList(ArrayList<String> videPathList) {
        mVidePathList = videPathList;
    }

    public void addRecordTracer(RecordTracer recordTracer) {
        mRecordTracerList.add(recordTracer);
    }

    public void deleteLastRecordTracer() {
        if (mRecordTracerList.size() > 0) {
            mRecordTracerList.remove(mRecordTracerList.size() - 1);
        }
    }

    public void setFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }
}
