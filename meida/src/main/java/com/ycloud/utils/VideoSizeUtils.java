package com.ycloud.utils;


import com.ycloud.common.Constant;

/**
 * Created by zhuhui on 16/2/9.
 */
public class VideoSizeUtils {
    public static Size CalcFitSize(int imageWidth, int imageHeight, int frameWidth, int frameHeight, Constant.ScaleMode scaleMode) {
        double f; //fit layer percentage
        Size rs = new Size(imageWidth, imageHeight);
        if (Constant.ScaleMode.AspectFit == scaleMode) {
            if(frameHeight * imageWidth < frameWidth * imageHeight) {
                f = 1.0 * frameHeight / imageHeight;
                rs.width = (int)(f * imageWidth + 0.5);
                rs.height = frameHeight;
                rs.x = (frameWidth - rs.width) / 2;
                rs.y = 0;
            } else {
                f = 1.0 * frameWidth / imageWidth;
                rs.width = frameWidth;
                rs.height = (int)(f * imageHeight + 0.5);
                rs.x = 0;
                rs.y = (frameHeight - rs.height) / 2;
            }
        }
        else if (Constant.ScaleMode.AspectFill == scaleMode) {
            if(frameHeight * imageWidth < frameWidth * imageHeight) {
                f = 1.0 * frameWidth / imageWidth;
                rs.width = frameWidth;
                rs.height = (int)(f * imageHeight + 0.5);
                rs.x = 0;
                rs.y = (frameHeight - rs.height) / 2;
            } else {
                f = 1.0 * frameHeight / imageHeight;
                rs.width = (int)(f * imageWidth + 0.5);
                rs.height = frameHeight;
                rs.x = (frameWidth - rs.width) / 2;
                rs.y = 0;
            }
        }
        else if (Constant.ScaleMode.ScacleToFill == scaleMode) {
            rs.height = frameHeight;
            rs.width = frameWidth;
            rs.x = 0;
            rs.y = 0;
        }
        return rs;
    }
    
    public static Size CalcFitSize(int srcW, int srcH, int expectedW, int expectedH) {
        Size res = new Size();
        if (srcH > 0 && srcW > 0 && expectedH > 0 && expectedW > 0) {
            if (expectedW > srcW || expectedH > srcH) {     //inner fit, adjust size
                double realRate = 1.0 * srcW / srcH;
                double nRate = 1.0 * expectedW / expectedH;
                if (realRate > nRate) {
                    res.height = srcH;
                    res.width = srcH * expectedW / expectedH;
                }
                else {
                    res.width = srcW;
                    res.height = srcW * expectedH / expectedW;
                }
                res.width &= ~0xF;   //16字节对齐
                res.height &= ~0xF;   //16字节对齐
            }
            else {
                res.width = expectedW;
                res.height = expectedH;
            }
        }
        return res;
    }

    public static class Size {
        public int width;
        public int height;
        public int x;
        public int y;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public Size() {
            this.width = 0;
            this.height = 0;
        }
    }
}
