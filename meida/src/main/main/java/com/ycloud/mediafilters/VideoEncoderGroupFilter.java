package com.ycloud.mediafilters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Environment;

import com.ycloud.common.Constant;
import com.ycloud.facedetection.STMobileFaceDetectionWrapper;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.mediacodec.videocodec.H264SurfaceEncoder;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.ByteBufferPool;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by kele on 2016/12/20.
 */

public class VideoEncoderGroupFilter extends AbstractYYMediaFilter implements IEncoderListener
{
    private final static int DEFAULT_START_ENCODER_TRY_COUNT = 3;

    private IEncodeFilter   mEncoderFilter = null;

    private AtomicBoolean mInited = new AtomicBoolean(false);
    private AtomicBoolean mEnable = new AtomicBoolean(false);
    VideoEncoderConfig mEncodeCfg = null;
    private boolean  mRecordMode = false;
    private MediaFilterContext mFilterContext;
    private boolean mEnableColorTable = false;
    private boolean mDrawSTMobilePoint = false;
    private ByteBufferPool mSnapshotPool = null;
//    private IVideoFilterSession mVideoFilterSession;

    YYMediaFilter mOutputFilter = new YYMediaFilter();

//    protected List<ResolutionModifyConfig> mResolutionModifyConfigs = null; //分辨率调整配置
    protected int mResolutionModifyInterval = 0; //分辨率调整间隔，单位秒
    private IEncoderListener mEncoderListener = null;

    // 调色板开播,支持所有编码类型，软硬264 265
    private boolean mColorPaletteInited = false;
    private Bitmap mColorPaletteBitmap = null;
    private Canvas mColorPaletteCanvas = null;
    private RectF mRect = null;
    private Paint mPaint = null;
    private Typeface mTypeface = null;
    private int[] mColors = {
            Color.BLACK     ,
            Color.GRAY      ,
            Color.WHITE     ,
            Color.RED       ,
            Color.GREEN     ,
            Color.BLUE      ,
            Color.YELLOW    ,
            Color.CYAN      ,
            Color.MAGENTA   ,
    };
    private int framecnt = 0;


    private void initColorPaletteTools(int width, int height) {
        if(mColorPaletteBitmap == null) {
            mColorPaletteBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        if(mColorPaletteCanvas == null) {
            mColorPaletteCanvas = new Canvas(mColorPaletteBitmap);
        }
        if(mTypeface == null) {
            String fontType = "sans";
            mTypeface = Typeface.create(fontType, Typeface.BOLD);
        }
        if(mPaint == null) {
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setTypeface(mTypeface);
            mPaint.setTextSize(20);
        }
        if(mRect == null) {
            mRect = new RectF();
        }
		
		if (mEnableColorTable && mDrawSTMobilePoint) {
        	mSnapshotPool = new ByteBufferPool(3, width * height * 4);
			STMobileFaceDetectionWrapper.getInstance(mFilterContext.getAndroidContext()).setEnableBodyDetect(true);
		}
    }

    private ByteBuffer LoadImageToBuffer(YYMediaSample sample) {
        int frameBufferId = sample.mFrameBufferId;
        if(frameBufferId < 0){
            return null;
        }
        try {
            ByteBuffer snapByteBf = mSnapshotPool.newByteBuffer();
            if (snapByteBf != null){
                snapByteBf.clear();
                snapByteBf.order(ByteOrder.LITTLE_ENDIAN);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId);
                GLES20.glReadPixels(0, 0, sample.mWidth, sample.mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, snapByteBf);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                return snapByteBf;
            }else{
                YYLog.i(TAG,"snapShot snapshotPool is empty");
            }
        } catch (Throwable e) {
            YYLog.e(TAG, "snapshot error:" + e.toString());
        }
        return null;
    }

    private void drawSTMobilePoint(YYMediaSample sample) {
        mColorPaletteCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        STMobileFaceDetectionWrapper.getInstance(mFilterContext.getAndroidContext()).drawHumanActionResults(sample.mWidth, sample.mHeight,
                                    mColorPaletteCanvas, mPaint);
    }

    private void drawColorPaletteToSample(YYMediaSample sample) {
        if (sample.mTextureTarget != GLES20.GL_TEXTURE_2D) {
            return;
        }
        if (!mColorPaletteInited) {
            initColorPaletteTools(sample.mEncodeWidth, sample.mEncodeHeight);
            mColorPaletteInited = true;
        }

        if (mDrawSTMobilePoint) {
            drawSTMobilePoint(sample);
        } else {
            int mRowCnt = mColors.length;
            int left = 0, right = sample.mEncodeWidth, top = 0, bottom = sample.mEncodeHeight / mRowCnt;
            int x_offset = left + sample.mEncodeWidth / 2, y_offset = sample.mEncodeHeight / mRowCnt / 2;
            for (int i = 0; i < mRowCnt; i++) {
                mPaint.setColor(mColors[i]);
                mRect.set(left, top, right, bottom);
                mColorPaletteCanvas.drawRect(mRect, mPaint);
                if (i + 1 < mRowCnt) {
                    mPaint.setColor(mColors[i + 1]);
                }
                if (i + 1 == mRowCnt) {
                    mPaint.setColor(mColors[0]);
                }
                mColorPaletteCanvas.drawText("0x " + framecnt, x_offset, top + y_offset, mPaint);

                top += sample.mEncodeHeight / mRowCnt;
                bottom += sample.mEncodeHeight / mRowCnt;
            }
            framecnt++;
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sample.mTextureId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mColorPaletteBitmap, 0);
    }

    private int mSnapIndex = 0;
    String mSnapShotPath = Environment.getExternalStorageDirectory().getPath() + "/YYImage";
    String mFileNamePrefix = "STSnap";
    private void saveToFile(final Bitmap bmp) {
        Thread taskTreadk = new Thread(new Runnable() {
            @Override
            public void run() {
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

                bmp.compress(Bitmap.CompressFormat.JPEG, 50, out);

                try{
                    out.flush();
                    out.close();
                } catch (IOException e){
                    YYLog.error(TAG, "save to file failed: IOException happened:" + e.toString());
                }finally {
                    YYLog.info(TAG, "jtzhu save file " + FilePath);
                    //bmp.recycle();
//                    long endTime = System.currentTimeMillis();
//                    YYLog.info(TAG, "save file cost : " + (endTime-time));
                }
            }
        });

        taskTreadk.start();
    }

    public VideoEncoderGroupFilter(MediaFilterContext filterContext, boolean recordMode) {
        mFilterContext = filterContext;
        mRecordMode = recordMode;
//        mVideoFilterSession = liveSession;
//       mFilterContext.getEncodeParamTipsMgr().setParamListener(this);
    }

    public AbstractYYMediaFilter    getOutputFilter() {
        return  mOutputFilter;
    }

    public void setEncoderListener(IEncoderListener listener) {
        mEncoderListener = listener;
    }

    public void init() {
        if(mInited.get())
            return;

        mInited.set(true);
    }

    public void deInit() {
        if(!mInited.get())
            return;

        mInited.set(false);
        stopEncode();
        if(mEnableColorTable && mColorPaletteInited) {
            if (mColorPaletteBitmap != null) {
                mColorPaletteBitmap.recycle();
            }
            mColorPaletteBitmap = null;
            mColorPaletteCanvas = null;
            mRect = null;
            mPaint = null;
            mColorPaletteInited = false;
        }
    }
    private IEncodeFilter createEncoder(VideoEncoderConfig encoderConfig) {
        IEncodeFilter encoder = null;
        if(encoderConfig.mEncodeType == VideoEncoderType.HARD_ENCODER_H264) {
            if(H264SurfaceEncoder.IsAvailable()) {
                encoder = new H264HardwareEncoderFilter(mFilterContext);
            } else {
                encoder = new X264SoftEncoderFilter(mFilterContext, mRecordMode);
            }
        } else if(encoderConfig.mEncodeType == VideoEncoderType.SOFT_ENCODER_X264) {
            encoder = new X264SoftEncoderFilter(mFilterContext, mRecordMode);
        } else if(encoderConfig.mEncodeType == VideoEncoderType.HARD_ENCODER_H265) {
            encoder = new H265HardwareEncoderFilter(mFilterContext);
        } else {
            YYLog.error(this, Constant.MEDIACODE_ENCODER+"codec type is not support, codeId="+encoderConfig.mEncodeType);
        }
        return encoder;
    }

    public boolean isEnable() {
        return mEnable.get();
    }

    public boolean startEncode(VideoEncoderConfig encoderConfig) {

        mEncodeCfg = new VideoEncoderConfig(encoderConfig);
        IEncodeFilter encoder = createEncoder(encoderConfig);
        if (encoder == null) {
            YYLog.error(this, Constant.MEDIACODE_ENCODER + "no encoder match the encoderConfig:" + encoderConfig.toString());
            return false;
        }

        boolean success = false;
        int retry = 0;
        while (retry++ < DEFAULT_START_ENCODER_TRY_COUNT) {
            if (encoder.startEncode()) {
                YYLog.info(this, Constant.MEDIACODE_ENCODER + "startEncode success");
                success = true;
                break;
            } else {
                YYLog.info(this, Constant.MEDIACODE_ENCODER + "startEncode failed");
                encoder.stopEncode();
                encoder.deInit();
            }
        }

        if (!success) {
            //try to switch other encoder.
            if (encoder.getEncoderFilterType() == VideoEncoderType.HARD_ENCODER_H264) {
                encoder = new X264SoftEncoderFilter(mFilterContext, mRecordMode);
                if (encoder.startEncode()) {
                    YYLog.info(this, Constant.MEDIACODE_ENCODER + "hardware h264 encoder switch to software 264 encoder succeed!!");
                } else {
                    YYLog.info(this, Constant.MEDIACODE_ENCODER + "hardware h264 encoder switch to software 264 encoder fail!!");
                    encoder = null;
                }
            } else {
                encoder = null;
            }
        }

        if (encoder != null) {
            mEncoderFilter = encoder;
            addDownStream(mEncoderFilter);
            mEncoderFilter.addDownStream(mOutputFilter);

            mEncoderFilter.setEncoderListener(this);
            VideoEncoderType encodeType = encoder.getEncoderFilterType();
//                mVideoQualityFilter = new HardEncodeVideoLiveQualityFilter(mFilterContext, mVideoFilterSession);
//            } else {
//                mVideoQualityFilter = new VideoLiveQualityFilter(mFilterContext, mVideoFilterSession);
//            }
//            mVideoQualityFilter.setResolutionModifyConfigs(mResolutionModifyConfigs, mResolutionModifyInterval);
//            mVideoQualityFilter.installAdaptor();
//            this.addDownStream(mVideoQualityFilter);
//
//            mUploaderFilter = new TransmitUploadFilter(mEncoderListener);
//            mEncoderFilter.addDownStream(mUploaderFilter);

            mEnable.set(true);
            YYLog.info(this, Constant.MEDIACODE_ENCODER + "startEncode encoderConfig:" + encoderConfig.toString());

            return true;
        }

        return false;
    }

    public void stopEncode() {
        YYLog.info(this, Constant.MEDIACODE_ENCODER + "stopEncode");

        mEnable.set(false);

        if(mEncoderFilter != null) {
            mEncoderFilter.setEncoderListener(null);
            mEncoderFilter.stopEncode();
            mEncoderFilter.removeAllDownStream();
            this.removeDownStream(mEncoderFilter);
            mEncoderFilter = null;
        }

//        if(mVideoQualityFilter != null) {
//            mVideoQualityFilter.deInit();
//            this.removeDownStream(mVideoQualityFilter);
//            mVideoQualityFilter = null;
//        }
//
//        if(mUploaderFilter != null) {
//            mUploaderFilter = null;
//        }
//
//        YYVideoCodec.resetCurrentEncodeName();
    }

    public void requestSyncFrame(){
        if (mEncoderFilter != null ){
            mEncoderFilter.requestSyncFrame();
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if(!mInited.get() || !mEnable.get() || !sample.mDeliverToEncoder){
            return false;
        }

        if (mEnableColorTable) {
            drawColorPaletteToSample(sample);
        }

        YYLog.debug(this, Constant.MEDIACODE_ENCODER+"encoder: processMediaSample");
        deliverToDownStream(sample);
        return false;
    }

    public void setNetworkBitrateSuggest(final int bitrate) {
//        YYLog.info(this, "setNetworkBitrateSuggest, mVideoQualityFilter is null:" + (mVideoQualityFilter == null) +
//                ", has adapter:" + (mVideoQualityFilter==null? "no":mVideoQualityFilter.hasAdapator()));
//
//        if(mVideoQualityFilter != null && mVideoQualityFilter.hasAdapator()) {
//            mVideoQualityFilter.setNetworkBitrateSuggest(bitrate);
//        } else {
//            adjustBitRate(bitrate/1024);
//        }
    }

    public void adjustBitRate(int kpbs) {
        if(mEncoderFilter != null) {
            mEncoderFilter.adjustBitRate(kpbs);
        }
    }

//    public void setResolutionModifyConfigs(List<ResolutionModifyConfig> configs, final int intervalSecs)
//    {
//        mResolutionModifyConfigs = configs;
//        mResolutionModifyInterval = intervalSecs;
//        if(mVideoQualityFilter != null) {
//            mVideoQualityFilter.setResolutionModifyConfigs(configs, intervalSecs);
//        }
//    }

    @Override
    public void onEncodeStat(int bitRate, int frameRate) {
//        if(mVideoQualityFilter != null) {
//            mVideoQualityFilter.setEncodeStats(bitRate, frameRate);
//        }
//
//        if(mEncoderListener != null) {
//            mEncoderListener.onEncodeStat(bitRate, frameRate);
//        }
    }

    @Override
    public void onEncodeResolution(int width, int height) {
        //do nothing
        if(mEncoderListener != null) {
            mEncoderListener.onEncodeResolution(width, height);
        }
    }

    @Override
    public void onEncodeFirstFrame() {
        if(mEncoderListener != null) {
            mEncoderListener.onEncodeFirstFrame();
        }
    }

    @Override
    public void onEncodeFrameData(byte[] data, int len, long pts, long dts, int frameType, VideoEncoderType encodeType) {
        //do thing
        //编码器的输出通过TransmitUploader来传递，底层编码器不触发这个事件.
    }

    @Override
    public void onEncodeEncParam(String param) {
        //do nothing
        if(mEncoderListener != null) {
            mEncoderListener.onEncodeEncParam(param);
        }
    }
}
