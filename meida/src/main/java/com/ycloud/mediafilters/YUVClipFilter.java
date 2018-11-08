package com.ycloud.mediafilters;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.os.Message;
import com.ycloud.gles.Drawable2d;
import com.ycloud.gles.FullFrameRect;
import com.ycloud.gles.Texture2dProgram;
import com.ycloud.mediaprocess.IMediaSnapshotPictureListener;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YMRThread;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import android.graphics.Matrix;

/**
 * Created by Administrator on 2018/1/24.
 * 1. 加载YUV数据到Y, U, V 三个纹理
 * 2. 将三个YUV纹理绘制到一个输出图片大小的纹理上，Shader做YUV->RGB 转换，GPU做缩放
 * 3. 截图保存，如果图片分辨率教大，图片压缩会比较耗时。
 */

public class YUVClipFilter extends AbstractYYMediaFilter implements YMRThread.Callback{
    private final static String TAG = "YUVClipFilter";
    private FullFrameRect mOffScreen = null;
    private int mOffScreenTextureId = -1;
    private int mOffScreenFrameBuffer = -1;
    private boolean mInited = false;
    private TexturePack mYChannel;
    private TexturePack mUChannel;
    private TexturePack mVChannel;
    private float[] mTransform;
    private ByteBuffer snapByteBuffer= null;
    private int mSnapIndex;
    private String mSnapShotPath = null;
    private String mFileNamePrefix = null;
    private int mQuality = 50;
    private MediaFilterContext mVideoFilterContext;
    private YMRThread  mClipThread = null;
    private static final int MSG_FRAME_AVAIL = 1;
    private static final int MSG_QUIT = 2;
    private AtomicReference<YYMediaFilterListener> mFilterListener = new AtomicReference<>(null);
    protected AtomicReference<IMediaSnapshotPictureListener> mPictureListener = new AtomicReference<>(null);
    private ExecutorService mSingleThreadExecutor;

    public YUVClipFilter(MediaFilterContext context) {
        mVideoFilterContext = context;
        mClipThread = new YMRThread("ymrsdk_YUVClipFilter");
        mClipThread.setCallback(this);
        mClipThread.start();
        mSnapIndex = 0;
        mSingleThreadExecutor = Executors.newSingleThreadExecutor();
    }

    public void setFilterListener(YYMediaFilterListener listener) {
        mFilterListener = new AtomicReference<>(listener);
    }

    public void setPictureListListener(IMediaSnapshotPictureListener listListener) {
        mPictureListener = new AtomicReference<>(listListener);
    }

    public void init(int width, int height, String snapShotPath, String filePrefix, int quality) {
        YYLog.info(TAG," init width " + width + " height " + height + " snapShotPath " +
                                    snapShotPath + " filePrefix " + filePrefix + " quality " + quality);
        mOutputWidth = width;
        mOutputHeight = height;
        mOffScreenTextureId = OpenGlUtils.createTexture(GLES20.GL_TEXTURE_2D, mOutputWidth, mOutputHeight);
        int[] frameBuffers = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        mOffScreenFrameBuffer = frameBuffers[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOffScreenFrameBuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                mOffScreenTextureId, 0);
        mOffScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_YUV),
                Drawable2d.Prefab.FULL_RECTANGLE,
                OpenGlUtils.createFloatBuffer(Drawable2d.FULL_RECTANGLE_TEX_COORDS),
                OpenGlUtils.createFloatBuffer(Drawable2d.FULL_RECTANGLE_TEX_COORDS));
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        mYChannel = new TexturePack();
        mYChannel.textureId = genTexture();
        mUChannel = new TexturePack();
        mUChannel.textureId = genTexture();
        mVChannel = new TexturePack();
        mVChannel.textureId = genTexture();
        mTransform = new float[] {
                1.0f, 0, 0, 0,
                0, 1.0f, 0, 0,
                0, 0, 1.0f, 0,
                0, 0, 0, 1.0f
        };
        OpenGlUtils.checkGlError(TAG+" init ");
        snapByteBuffer = ByteBuffer.allocate(width*height*4); // ARGB color format for bitmap
        snapByteBuffer.order(ByteOrder.nativeOrder());
        mSnapShotPath = snapShotPath;
        if(null != mSnapShotPath) {
            File file = new File(mSnapShotPath);
            if (!file.exists() || !file.isDirectory()) {
                if (!file.mkdirs()) {
                    YYLog.error(TAG, "mkdirs " + mSnapShotPath + " failed !");
                }
            }
        }
        mFileNamePrefix = filePrefix;
        mQuality = quality;
        mInited = true;
    }

    @Override
    public void deInit() {
        YYLog.info(this," ClipFilter deInit");
        if (mOffScreenTextureId > 0) {
            int[] textures = new int[1];
            textures[0] = mOffScreenTextureId;
            GLES20.glDeleteTextures(1, textures, 0);
            mOffScreenTextureId = -1;
        }
        if (mOffScreenFrameBuffer > 0) {
            int[] framebuffers = new int[1];
            framebuffers[0] = mOffScreenFrameBuffer;
            GLES20.glDeleteFramebuffers(1, framebuffers, 0);
            mOffScreenFrameBuffer = -1;
        }
        if (mOffScreen != null) {
            mOffScreen.release(true);
            mOffScreen = null;
        }
        releaseTexture(mYChannel);
        releaseTexture(mUChannel);
        releaseTexture(mVChannel);
        mYChannel = null;
        mUChannel = null;
        mVChannel = null;
        snapByteBuffer = null;
        if (mClipThread != null) {
            mClipThread.stop();
            mClipThread = null;
        }

        if (mSingleThreadExecutor != null) {
            mSingleThreadExecutor.shutdown();
            try {
                mSingleThreadExecutor.awaitTermination(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                YYLog.error(TAG, "InterruptedException :" + e.getMessage());
            }
        }

        mInited = false;
        YYLog.info(TAG," ClipFilter deInit finished.");
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (!mInited || mClipThread == null) {
            YYLog.info(TAG, "not init yet! return.");
            return false;
        }
        sample.addRef();
        return mClipThread.sendMessage(Message.obtain(mClipThread.getHandler(), MSG_FRAME_AVAIL, 0, 0, sample));
    }



    private void handleFrame(final YYMediaSample  sample) {
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                if(sample.mEndOfStream) {
                    YYLog.info(TAG, "end of video stream .");
                }
                Clip(sample);
            }
        });
    }

    private void updataClipTextureCoord(int imageW, int imageH, int viewW, int viewH) {
        if (imageW > imageH) {  // 横屏 宽频 视频
            float newW = ((float)viewW / viewH *  imageH);
            float offset = (imageW - newW)/imageW;
            float textureCord[] = new float[8];
            System.arraycopy(Drawable2d.FULL_RECTANGLE_TEX_COORDS,0,textureCord,0,Drawable2d.FULL_RECTANGLE_TEX_COORDS.length);

            textureCord[0] += offset/2;  // Clip left edge
            textureCord[4] += offset/2;

            textureCord[2] -= offset/2;  // Clip right edge
            textureCord[6] -= offset/2;

            mOffScreen.setClipTextureCord(textureCord);
        }
    }

    /**
     * 旋转位图
     *
     * @param origin 原图
     * @param angle  旋转角度，可正可负
     * @return 旋转后的图片
     */
    private Bitmap rotateBitmap(Bitmap origin, float angle) {
        if (origin == null) {
            return null;
        }
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(angle);
        // 围绕原地进行旋转
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        return newBM;
    }

    /**
     * 根据给定的宽和高进行拉伸
     *
     * @param origin    原图
     * @param newWidth  新图的宽
     * @param newHeight 新图的高
     * @return new Bitmap
     */
    private Bitmap scaleBitmap(Bitmap origin, int newWidth, int newHeight) {
        if (origin == null || newWidth == 0 || newHeight == 0) {
            return null;
        }
        int height = origin.getHeight();
        int width = origin.getWidth();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);// 使用后乘
        Bitmap newBM = null;
        try {
            newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        } catch (IllegalArgumentException e) {
            YYLog.error(TAG, "Exception: " + e.getMessage());
        }
        if (!origin.isRecycled()) {
            origin.recycle();
        }
        return newBM;
    }


    private void Clip(YYMediaSample sample) {
        if (!mInited) {
            YYLog.info(TAG, "not init yet! return.");
            return ;
        }
        //long time = System.currentTimeMillis();
        if (!sample.mEndOfStream && sample.mDataByteBuffer != null && mOutputHeight > 0 && mOutputWidth > 0) {
            ByteBuffer frame = sample.mDataByteBuffer;
            int width = sample.mWidth;
            int height = sample.mHeight;
            // load YUV data to texture
            loadDataToTexture(mYChannel, frame.position(0), width, height);
            loadDataToTexture(mUChannel, frame.position(width * height), width / 2, height / 2);
            loadDataToTexture(mVChannel, frame.position(width * height * 5 / 4), width / 2, height / 2);

            System.arraycopy(mTransform, 0, sample.mTransform, 0, mTransform.length);
            //sample.mTransform = mTransform;

            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
            GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);    // again, only really need to
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);     //  clear pixels outside rect
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOffScreenFrameBuffer);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                    mOffScreenTextureId, 0);
            if ((sample.mWidth != mOutputWidth || sample.mHeight != mOutputHeight) && sample.mDisplayRotation % 360 == 0) {
                updataClipTextureCoord(sample.mWidth, sample.mHeight, mOutputWidth, mOutputHeight);
            }

            mOffScreen.drawFrame(mYChannel.textureId, mUChannel.textureId, mVChannel.textureId, sample.mTransform);
            if (snapByteBuffer != null) {
                snapByteBuffer.clear();
                snapByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                GLES20.glReadPixels(0, 0, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, snapByteBuffer);

                Bitmap bitmap = Bitmap.createBitmap(mOutputWidth, mOutputHeight, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(snapByteBuffer);

                if (sample.mDisplayRotation == 90 || sample.mDisplayRotation  == 180 || sample.mDisplayRotation  == 270) {
                    Bitmap rotatedBitmap = rotateBitmap(bitmap, sample.mDisplayRotation);
                    Bitmap scaledBitmap = scaleBitmap(rotatedBitmap, mOutputWidth, mOutputHeight);
                    saveToFile(scaledBitmap, sample.mYYPtsMillions);
                } else {
                    saveToFile(bitmap, sample.mYYPtsMillions);
                }

                YYMediaFilterListener listener = mFilterListener.get();
                if(listener != null) {
                    listener.onFilterProcessMediaSample(this, sample.mSampleType, sample.mYYPtsMillions);
                }
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        } else {

            saveToFile(null, -1);
//            YYMediaFilterListener listener = mFilterListener.get();
//            if(listener != null) {
//                listener.onFilterEndOfStream(this);
//            }
            //stop();
        }

        //释放对sample的引用
        sample.decRef();
//        long end = System.currentTimeMillis();
//        YYLog.info(TAG, " Clip end, cost " + (end-time) + " ms.");
    }

    private void saveToFile(final Bitmap bmp, final long pts) {
        if (mSingleThreadExecutor == null) {
            return;
        }
        mSingleThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (bmp == null) {
                    YYMediaFilterListener listener = mFilterListener.get();
                    if (listener != null) {
                        listener.onFilterEndOfStream(null);
                    }
                    return;
                }

                //long time = System.currentTimeMillis();
                FileOutputStream out = null;
                mSnapIndex++;
                String indexStr = String.format("%03d", mSnapIndex);
                String FilePath = mSnapShotPath + File.separator + mFileNamePrefix + indexStr + ".jpg";
                try {
                    out = new FileOutputStream(FilePath);
                } catch(FileNotFoundException e) {
                    YYLog.error(TAG, String.format(Locale.getDefault(), "%s not found: %s", FilePath, e.toString()));
                }
                if (out == null){
                    return;
                }

                bmp.compress(Bitmap.CompressFormat.JPEG, mQuality, out);

                try{
                    out.flush();
                    out.close();

                    if (mPictureListener != null && mPictureListener.get() != null) {
                        mPictureListener.get().onPictureAvaliable(FilePath, pts);
                    }
                } catch (IOException e){
                    YYLog.error(TAG, "save to file failed: IOException happened:" + e.toString());
                }finally {
                    bmp.recycle();
                }
            }
        });
    }

    private void loadDataToTexture(TexturePack texturePack, Buffer data, int width, int height) {
        if (data == null || texturePack.textureId <= 0 || width <= 0 || height <= 0) {
            YYLog.error(TAG,"loadDataToTexture invalid parameter");
            return;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturePack.textureId);

        if ((texturePack.width != width) || (texturePack.height != height)) {
            if ((width & 0x03) != 0) {
                YYLog.info(TAG,"glTexImage2D width:" + width + " GL_UNPACK_ALIGNMENT: 1");
                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
            } else {
                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);
            }
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0,
                    GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, data);
            texturePack.height = height;
            texturePack.width = width;
        }
        else {
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, height,
                    GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, data);
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private int genTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
//        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width,
//                height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        return textures[0];
    }

    private void releaseTexture(TexturePack texturePack) {
        if (texturePack != null && texturePack.textureId >= 0) {
            int[] textures = new int[1];
            textures[0] = texturePack.textureId;
            GLES20.glDeleteTextures(1, textures, 0);
            texturePack.textureId = -1;
            texturePack.width = -1;
            texturePack.height = -1;
        }
    }

    private static class TexturePack {
        public int textureId = -1;
        public int width = -1;
        public int height = -1;
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onStop() {
        YYLog.info(TAG," ClipFilter Thread stop.");
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_FRAME_AVAIL:
                handleFrame((YYMediaSample)msg.obj);
                break;
            default:
                break;
        }
    }
}
