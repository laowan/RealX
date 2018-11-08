package com.ycloud.api.process;

import com.ycloud.mediaprocess.VideosUnionInternal;

import java.util.List;

/**
 * Created by dzhj on 17/6/17.
 */

public class VideosUnion {
    VideosUnionInternal mVideosUnion;


    /**
     *
     * @param videoOverlayInfoList：组成合演视频文件列表
     * @param outputVideoPath:合演文件输出路径
     */
    public void setVideos(List<VideosUnionInternal.VideoOverlayInfo> videoOverlayInfoList, String outputVideoPath){
        mVideosUnion.setVideos(videoOverlayInfoList,outputVideoPath);
    }

    /**
     * 设置合演视频的总分辨率
     * @param totalWidth
     * @param totalHeight
     */
    public void setTotalResolution(int totalWidth,int totalHeight){
        mVideosUnion.setTotalResolution(totalWidth,totalHeight);
    }

    /**
     * 设置监听
     * @param listener
     */
    public void setMeidaListener(IMediaListener listener){
        mVideosUnion.setMediaListener(listener);
    }

    /**
     * 合演取消接口
     */
    public void cancel(){
        mVideosUnion.cancel();
    }

    /**
     * 设置合演背景色
     * @param color
     */
    public void setBackgroundColor(String color){
        mVideosUnion.setBackgroundColor(color);
    }

    /**
     * 设置水印
     * @param waterMarkInfoList
     */
    public void setWaterMark(List<VideosUnionInternal.WaterMarkInfo> waterMarkInfoList){
        mVideosUnion.setWaterMark(waterMarkInfoList);
    }


    public VideosUnion(List<VideosUnionInternal.VideoOverlayInfo> videoOverlayInfoList, List<VideosUnionInternal.WaterMarkInfo> waterMarkInfoList,IMediaListener listener, int totalWidth, int totalHeight,  String outputPath) {

        mVideosUnion = new VideosUnionInternal();
        mVideosUnion.setVideos(videoOverlayInfoList, outputPath);
        mVideosUnion.setWaterMark(waterMarkInfoList);
        mVideosUnion.setTotalResolution(totalWidth, totalHeight);
        mVideosUnion.setMediaListener(listener);
    }

    public void unionWithExport(){
        mVideosUnion.unionWithExport();
    }
}
