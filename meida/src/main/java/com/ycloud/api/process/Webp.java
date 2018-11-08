package com.ycloud.api.process;

import com.ycloud.mediaprocess.WebpEncoder;

/**
 *  视频转Web
 */

public class Webp {

    WebpEncoder mWebpEncoder;
    public Webp(){
        mWebpEncoder = new WebpEncoder();
    }

    /**
     * webp分辨率设置
     * @param width
     * @param height
     */
    public void setDimensions(int width,int height){
        mWebpEncoder.setDimensions(width,height);
    }

    /**
     * 设置帧率
     * @param frameRate
     */
    public void setFrameRate(int frameRate){
        mWebpEncoder.setFrameRate(frameRate);
    }

    /**
     * 设置文件路径
     * @param sourcePath  源文件
     * @param outputPath  输出路径（需写成webp后缀的格式）
     */
    public void setPath(String sourcePath, String outputPath) {
        mWebpEncoder.setPath(sourcePath,outputPath);
    }


    /**
     * For lossy encoding, this controls image quality, 0 to 100.
     * For lossless encoding, this controls the effort and time spent at compressing more.
     * The default value is 75.
     * @param imageQuality
     */
    public void setImageQuality(int imageQuality){
        mWebpEncoder.setImageQuality(imageQuality);
    }

    /**
     * 设置完参数后，执行该函数执行webp编码转换
     */
    public void encode(){
        mWebpEncoder.encode();
    }

    /**
     * 设置进度及错误监听
     * @param mediaListener
     */
    public void setMediaListener(IMediaListener mediaListener){
        mWebpEncoder.setMediaListener(mediaListener);
    }
}
