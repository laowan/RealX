package com.ycloud.utils;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.opengl.GLES20;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

/**
 * Created by kele on 2017/4/17.
 */

public class ImageStorageUtil {
    private final static String TAG = "ImageUtil";
    public static int mFileIndex = 1;
    private final static  String sImageFiles = "YYImage";

    //filesname. "Image"+pid+index.
    public static String getLogFileName() {
        String logFileName  = null;
        String logPath = null;
        File path = Environment.getExternalStorageDirectory(); //取得sdcard文件路径
        logPath = path.toString();
        logPath += File.separator +sImageFiles;

        File file = new File(logPath);
        if(!file.exists() && !file.isDirectory()) {
            file.mkdir();
        }

        logFileName = logPath + File.separator + "Image-" + Thread.currentThread().getId() + (mFileIndex++);
        return logFileName;
    }

    public static void LogImage2Files(final byte[] image, final String imageType) {
        //different files.
        Thread taskThead = new Thread(new Runnable() {
            @Override
            public void run() {
                String logFileName = getLogFileName() + "." + imageType;
                File logFile = new File(logFileName);
                if (!logFile.exists()) {

                    FileOutputStream fos = null;

                    try {
                        logFile.createNewFile();
                        // we can make FileWriter static, but when to close it
                        fos = new FileOutputStream(logFile);
                        fos.write(image, 0, image.length);
                        fos.flush();
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        fos = null;
                    }
                } else {
                    //fail
                }
            }
        }, SDK_NAME_PREFIX +"LogImage2Files");
        taskThead.start();
    }

    public static  void saveYUV2JPEG(final byte[] data, final int width, final int height) {
        Thread taskThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String fileName = getLogFileName() + ".jpg";
                File pictureFile = new File(fileName);
                if (!pictureFile.exists()) {
                    FileOutputStream filecon = null;
                    try {
                        pictureFile.createNewFile();
                        filecon = new FileOutputStream(pictureFile);
                        YuvImage image = new YuvImage(data, ImageFormat.NV21, width, height, null);    //将NV21 data保存成YuvImage
                        //图像压缩
                        image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 70, filecon);    // 将NV21格式图片，以质量70压缩成Jpeg，并得到JPEG数据流
                        filecon.flush();
                        filecon.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        filecon = null;
                    }
                }
            }
        }, SDK_NAME_PREFIX +"saveYUV2JPEG");

        taskThread.start();
    }



    public static Bitmap createImgae(byte[] bits, int width, int height) {
        Bitmap bitmap=null;
        bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bits));
        return bitmap;
    }

    public static void saveToFile(final Bitmap bmp) {
        Thread taskTreadk = new Thread(new Runnable() {
            @Override
            public void run() {
                FileOutputStream out = null;
                String filename = getLogFileName() + "rbg.jpg";
                try {
                    out = new FileOutputStream(filename);
                } catch(FileNotFoundException e) {
                    YYLog.error(TAG, String.format(Locale.getDefault(), "%s not found: %s", filename, e.toString()));
                }
                if (out == null){
                    return;
                }
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
                try{
                    out.flush();
                    out.close();
                } catch (IOException e){
                    YYLog.error(TAG, "save to file failed: IOException happened:" + e.toString());
                }finally {
                    out = null;
                }

            }
        });

        taskTreadk.start();
    }

    public static void save2DTextureToJPEG(int textureId, int width, int height) {
        int[] frameBuffers = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        OpenGlUtils.checkGlError("glGenFramebuffers ");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0]);
        OpenGlUtils.checkGlError("glBindFramebuffer ");
        if(frameBuffers[0] != 0 && textureId != 0 && width > 0 && height > 0) {
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0);
            OpenGlUtils.checkGlError("glFramebufferTexture2D ");
            ByteBuffer mByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
            mByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mByteBuffer);
            OpenGlUtils.checkGlError("glReadPixels ");
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            if (bitmap != null) {
                bitmap.copyPixelsFromBuffer(mByteBuffer);
                saveToFile(bitmap);
            }
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDeleteFramebuffers(1, frameBuffers, 0);
    }


    public static void SaveEGLSurfaceToJpeg(int width, int height) {
        OpenGlUtils.checkGlError("SaveEGLSurfaceToJpeg  enter... ");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        ByteBuffer mByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
        mByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mByteBuffer);
        OpenGlUtils.checkGlError("glReadPixels ");
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (bitmap != null) {
            bitmap.copyPixelsFromBuffer(mByteBuffer);
            saveToFile(bitmap);
        }
        OpenGlUtils.checkGlError("SaveEGLSurfaceToJpeg  out... ");
    }




}
