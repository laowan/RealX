package com.ycloud.mediafilters;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLES20;

import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.config.AspectRatioType;
import com.ycloud.api.videorecord.IChangeAspectRatioListener;
import com.ycloud.api.videorecord.IPreviewSnapshotListener;
import com.ycloud.common.Constant;
import com.ycloud.gles.Drawable2d;
import com.ycloud.gles.EglFactory;
import com.ycloud.gles.FullFrameRect;
import com.ycloud.gles.IWindowSurface;
import com.ycloud.gles.Texture2dProgram;
import com.ycloud.utils.ImageSizeUtil;
import com.ycloud.utils.ImageUtils;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.VideoSizeUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.AbstractSurfaceInfo;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class PreviewFilter extends AbstractYYMediaFilter {
    private static final String TAG = "PreviewFilter";
    private MediaFilterContext mFilterContext;
    private AtomicBoolean mInited = new AtomicBoolean(false);

	/*Surface只接受事件来设置.
	  Android4.2以下系统不支持直接通过surface来创建window surface.
	  4.2以下的系统使用surfaceHolderInfo,
	 */
	private AbstractSurfaceInfo mSurfaceInfo = null;

	//默认的拉伸模式为AspectFill: 也即保持图片的比列不变化，且不要求显示全部图片, 但是没有边距空白地带.
	//按照surface的width/height的比例，居中剪切用来显示的纹理.

	//不然会产生预览图片的拉升， 变形,
	private Constant.ScaleMode mScaleMode = Constant.ScaleMode.AspectFill;
	
	private IWindowSurface mPreviewWindowSurface = null;
    private FullFrameRect mPreviewScreen = null;

	private int mViewWidth = 0;
	private int mViewHeight= 0;
	private int mViewX = 0;
	private int mViewY = 0;
	private int mSurfaceWidth = 0;
	private int mSurfaceHeight = 0;


	//保留上一帧的输入/裁剪长宽
	private int mInputWidth = 0;
	private int mInputHeight = 0;
	private int mClipWidth = 0;
	private int mClipHeight = 0;

	//该textureId用于保存结束录制时最后一帧预览图片
	private  YYMediaSample mLastYYMediaSample;
	private int[] mFrameBuffer;
	private int[] mFrameBufferTexture;

	private boolean mPreviewStart = false;

	//增加surface valid标识，当surfaceDestroyed后首先将flag设置为false，先停止渲染
	//这样做的目的是防止surfaceDestroyed后发消息到gl线程release eglSurface不能立即停止render线程操作surface
	public AtomicBoolean mSurfaceValid = new AtomicBoolean(false);

    private final AspectRatioType mDefaultAspect = AspectRatioType.ASPECT_RATIO_4_3;
    private AspectRatioType mAspect = mDefaultAspect;
    private IChangeAspectRatioListener mAspectRatioListener = null;
    private int mTargetWidth = 0;
    private int mTargetHeight = 0;
    private int mCurrentWidht = 0;
    private int mCurrentHeight = 0;
    private int mLastWidth = 0;
    private int mLastHeight = 0;

    private int mTargetXOffset = 0;
    private int mTargetYOffset = 0;
    private int mCurrentXOffset = 0;
    private int mCurrentYOffset = 0;
    private int mLastXOffset = 0;
    private int mLastYOffset = 0;

    private static final int DEFAULT_EFFECT_STEP = 8;
    private int mEffectStep = DEFAULT_EFFECT_STEP;    // 8 帧完成变换
    private Timer mTimer = null;
    private TimerTask mTimerTask = null;
    private boolean mTimerStop = true;
    private boolean mLastTimerState = true;
    private IPreviewSnapshotListener mPreviewSnapshotListener = null;

    private int m_xOffset = 0;
    private int m_yOffset = 0;
    private ExecutorService mSingleThreadExecutor = null;
    private boolean bEffect = false;
    private boolean mHaveSetOffset = false;

    public PreviewFilter(MediaFilterContext fitlerContext) {
        mFilterContext = fitlerContext;
        mFrameBuffer = new int[1];
        mFrameBufferTexture = new int[1];

		mFrameBuffer[0]  = -1;
		mFrameBufferTexture[0] = -1;
	}
	
	/** init 与 deInit都在同一个线程中调用，不然会有线程同步问题 */
	public void init(int width, int height) {
		if(mInited.get()) {
			YYLog.info(this, Constant.MEDIACODE_VIEW+"init: intialized state now, so return");
			return;
		}

		YYLog.info(this, Constant.MEDIACODE_VIEW+"PreviewFitler.doInit begin");
		mPreviewScreen = new FullFrameRect(
				new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D_WITH_EXTRA_TXT_2),
				Drawable2d.Prefab.FULL_RECTANGLE,
				OpenGlUtils.createFloatBuffer(Drawable2d.FULL_RECTANGLE_TEX_COORDS),
				OpenGlUtils.createFloatBuffer(Drawable2d.FULL_RECTANGLE_TEX_COORDS));

		setPreviewFlipY();

		mLastYYMediaSample = YYMediaSampleAlloc.instance().alloc();

        setOutputSize(width, height);
        mCurrentWidht = width;
        mCurrentHeight = height;
        mLastWidth = mCurrentWidht;
        mLastHeight = mCurrentHeight;
        if (SDKCommonCfg.getRecordModePicture()) {
            initScaleEffectTimer();
            mSingleThreadExecutor = Executors.newSingleThreadExecutor();
        }
        mInited.set(true);
        YYLog.info(this, Constant.MEDIACODE_VIEW + " PreviewFitler.doInit end, width " + width + " height " + height);
    }

	public void deInit() {
		if(!mInited.getAndSet(false)) {
			YYLog.info(this, Constant.MEDIACODE_VIEW+"deInit: no Initialied state now, so return");
			return;
		}
		YYLog.info(this, Constant.MEDIACODE_VIEW+"PreviewFilter deInit");

		mLastYYMediaSample.decRef();

		releasePreviewStaffs();
		if (mPreviewScreen != null) {
			mPreviewScreen.release(true);
			mPreviewScreen = null;

			mInputWidth = 0;
			mInputHeight = 0;
			mClipWidth = 0;
			mClipHeight = 0;
		}
        if (SDKCommonCfg.getRecordModePicture()) {
            if (mSingleThreadExecutor != null) {
                mSingleThreadExecutor.shutdown();
            }
            if (mTimerTask != null) {
                mTimerTask.cancel();
                mTimerTask = null;
            }
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
        }
    }

    public void SyncFinalPreviewRect(int surfaceWidth, int surfaceHeight) {
        mSurfaceWidth = surfaceWidth;
        mSurfaceHeight = surfaceHeight;
    }
    public Rect getFinalPreviewRectByAspect(AspectRatioType aspect) {
        int width = mOutputWidth;
        int height = mOutputHeight;
        Rect rect = new Rect(0, 0, mOutputWidth, mOutputHeight);

        switch (aspect) {
            case ASPECT_RATIO_1_1:   // 3:4 -> 1:1
                height = width;
                break;
            case ASPECT_RATIO_16_9:  // 3:4 -> 9:16
                width = (int) (height * (float) 9 / (float) 16);
                break;
            case ASPECT_RATIO_4_3:
                break;
            default:
                break;
        }

        VideoSizeUtils.Size si = VideoSizeUtils.CalcFitSize(width, height, mSurfaceWidth, mSurfaceHeight, Constant.ScaleMode.AspectFit);

        rect.left = si.x;
        rect.top = si.y;
        rect.right = rect.left + si.width;
        rect.bottom = rect.top + si.height;

        YYLog.info(TAG, "getFinalPreviewRectByAspect aspect " + aspect + " rect " + rect);

        return rect;
    }
    private void updataAspectRatio() {
        int width = mInputWidth;
        int height = mInputHeight;

        if (mAspect != mDefaultAspect) {
            switch (mAspect) {
                case ASPECT_RATIO_1_1:   // 3:4 -> 1:1
                    height = width;
                    break;
                case ASPECT_RATIO_16_9:  // 3:4 -> 9:16
                    width = (int) (height * (float) 9 / (float) 16);
                    break;
                default:
                    break;
            }
        }

        mTargetWidth = width;
        mTargetHeight = height;
        mTargetXOffset = m_xOffset;
        mTargetYOffset = m_yOffset;

        int wDelta = Math.abs(mTargetWidth - mLastWidth);
        int hDelta = Math.abs(mTargetHeight - mLastHeight);
        int xOffsetDelta = Math.abs(mTargetXOffset - mLastXOffset);
        int yOffsetDelta = Math.abs(mTargetYOffset - mLastYOffset);

        // 1. 预览画面宽度渐变
        if (mCurrentWidht < mTargetWidth) {
            mCurrentWidht += wDelta / mEffectStep;
            if (mCurrentWidht >= mTargetWidth) {
                stopScaleEffectTimer();
            }
        } else if (mCurrentWidht > mTargetWidth) {
            mCurrentWidht -= wDelta / mEffectStep;
            if (mCurrentWidht <= mTargetWidth) {
                stopScaleEffectTimer();
            }
        }

        // 2. 预览画面高度渐变
        if (mCurrentHeight < mTargetHeight) {
            mCurrentHeight += hDelta / mEffectStep;
            if (mCurrentHeight >= mTargetHeight) {
                stopScaleEffectTimer();
            }
        } else if (mCurrentHeight > mTargetHeight) {
            mCurrentHeight -= hDelta / mEffectStep;
            if (mCurrentHeight <= mTargetHeight) {
                stopScaleEffectTimer();
            }
        }

        // 3. 预览画面 x 坐标偏移渐变
        if (mCurrentXOffset < mTargetXOffset) {
            mCurrentXOffset += xOffsetDelta / mEffectStep;
            if (mCurrentXOffset >= mTargetXOffset) {
                stopScaleEffectTimer();
            }
        } else if (mCurrentXOffset > mTargetXOffset) {
            mCurrentXOffset -= xOffsetDelta / mEffectStep;
            if (mCurrentXOffset <= mTargetXOffset) {
                stopScaleEffectTimer();
            }
        }

        // 4. 预览画面 y 坐标偏移渐变
        if (mCurrentYOffset < mTargetYOffset) {
            mCurrentYOffset += yOffsetDelta / mEffectStep;
            if (mCurrentYOffset >= mTargetYOffset) {
                stopScaleEffectTimer();
            }
        } else if (mCurrentYOffset > mTargetYOffset) {
            mCurrentYOffset -= yOffsetDelta / mEffectStep;
            if (mCurrentYOffset <= mTargetYOffset) {
                stopScaleEffectTimer();
            }
        }

        if (mTimerStop) {
            mCurrentWidht = mTargetWidth;
            mCurrentHeight = mTargetHeight;
            mLastWidth = mTargetWidth;
            mLastHeight = mTargetHeight;

            mCurrentXOffset = m_xOffset;
            mCurrentYOffset = m_yOffset;
            mLastXOffset = m_xOffset;
            mLastYOffset = m_yOffset;

            YYLog.info(TAG, " mLastWidth " + mLastWidth + " mLastHeight " + mLastHeight);
        }

        if (mCurrentWidht == mTargetWidth && mCurrentHeight == mTargetHeight &&
                mCurrentXOffset == mTargetXOffset && mCurrentYOffset == mTargetYOffset) {
            stopScaleEffectTimer();
        }

        YYLog.info(TAG, " mCurrentWidht " + mCurrentWidht + " mCurrentHeight " + mCurrentHeight +
                " mTargetWidth " + mTargetWidth + " mTargetHeight " + mTargetHeight + " mAspect " + mAspect);
    }
    private void updateScaleEffectSize(YYMediaSample sample) {
        if (mCurrentWidht > 0 && mCurrentHeight > 0) {
            sample.mClipWidth = mCurrentWidht;
            sample.mClipHeight = mCurrentHeight;
            //YYLog.info(TAG, "mCurrentWidth : " + mCurrentWidht + " mCurrentHeight " + mCurrentHeight);
        }
    }

    private void initScaleEffectTimer() {
        if (mTimer == null) {
            mTimer = new Timer();
            mTimerTask = new TimerTask() {
                public void run() {
                    if (!mTimerStop) {
                        updataAspectRatio();
                    }
                }
            };
            mTimer.schedule(mTimerTask, 1000, 30);
        }
    }

    private void startScaleEffectTimer() {
        mTimerStop = false;
    }

    private void stopScaleEffectTimer() {
        mTimerStop = true;
    }

    // For YOYI
    public Rect getCurrentVideoRect() {
        Rect rect = new Rect(0, 0, mViewWidth, mViewHeight);
        rect.left = mViewX + m_xOffset;
        rect.top = mViewY + m_yOffset;
        rect.right = rect.left + mViewWidth;
        rect.bottom = rect.top + mViewHeight;
        YYLog.info(TAG, "getCurrentVideoRect " + rect);
        return rect;
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {

		if(!mInited.get() || !sample.mDeliverToPreview) {
			return false;
		}
        if (SDKCommonCfg.getRecordModePicture()) {   // 适配不同Aspect
            updateScaleEffectSize(sample);
        }

		mLastYYMediaSample.assigne(sample);
        if (SDKCommonCfg.getRecordModePicture()) {
            if (checkImageSizeUpdated(sample.mClipWidth, sample.mClipHeight, true)) {
                updateVideoPosition();
            }
            if (!mLastTimerState && mTimerStop) {  //mTimerStop state change from false to true indicate the timer have stopped in timer thread now
                if (null != mAspectRatioListener) {
                    YYLog.info(TAG, "onChangeAspectRatioFinish " + mAspect);
                    mAspectRatioListener.onChangeAspectRatioFinish(mAspect);
                }
            }
            mLastTimerState = mTimerStop;

        } else {
            if (checkImageSizeUpdated(sample.mWidth, sample.mHeight, true)) {
                updateVideoPosition();
            }
        }

		if(mPreviewWindowSurface != null && mSurfaceValid.get()) {
			try {
				mPreviewWindowSurface.makeCurrent();
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                if (SDKCommonCfg.getRecordModePicture()) {
                    if (mAspect == mDefaultAspect) {
                        if (mCurrentYOffset == 0) {
                            mCurrentYOffset = mViewY;  // 4:3 杀进程重启时，防止画面上移的情况
                        }
                    }
                    GLES20.glViewport(mViewX + mCurrentXOffset, mViewY + mCurrentYOffset, mViewWidth, mViewHeight);
                } else {
                    GLES20.glViewport(mViewX, mViewY, mViewWidth, mViewHeight);
                }
				if (checkClipRatioChanged(sample.mWidth, sample.mHeight, sample.mClipWidth, sample.mClipHeight)){
					YYLog.info(this,Constant.MEDIACODE_VIEW+"VideoSize X:"+mViewX+" Y:"+mViewY+" width:"+mViewWidth+" height:"+mViewHeight);

					mPreviewScreen.adjustTexture(sample.mWidth, sample.mHeight, sample.mClipWidth, sample.mClipHeight);
				}

				if (!SDKCommonCfg.getRecordModePicture() || mHaveSetOffset) {
                    mPreviewScreen.drawFrame(sample.mTextureId, sample.mTransform, mFilterContext.getWatermarkTextureID(), mFilterContext.getDynamicTextureID());
                    //mPreviewWindowSurface.setPresentationTime(sample.mAndoridPtsNanos);  //这个api有api level的版本要求， 这里也不需要设置.
                    mPreviewWindowSurface.swapBuffers();
                }

				if (!mPreviewStart) {
					mPreviewStart = true;
					if (mFilterContext.getRecordConfig() != null && mFilterContext.getRecordConfig().getPreviewListener() != null) {
						mFilterContext.getRecordConfig().getPreviewListener().onStart();
						YYLog.info(TAG, "previewFilter start render first frame");
					}
				}
			}
			catch (Throwable t) {
				t.printStackTrace();
				YYLog.error(this, Constant.MEDIACODE_VIEW+"[exception] exception occur, "+t.toString());
				releasePreviewStaffs();
			}
		}

		deliverToDownStream(sample);
		return false;
	}

	private boolean checkClipRatioChanged(int inputWidth, int inputHeight, int clipWidth, int clipHeight){
		boolean ret = false;
		if (mInputWidth != inputWidth || mInputHeight != inputHeight || mClipWidth != clipWidth || mClipHeight != clipHeight){
			YYLog.info(this, Constant.MEDIACODE_VIEW+"inputWidth:"+inputWidth+" inputHeight:"+inputHeight+" clipWidth:"+clipWidth+" clipHeight:"+clipHeight);
			mInputWidth = inputWidth;
			mInputHeight = inputHeight;
			mClipWidth = clipWidth;
			mClipHeight = clipHeight;
			ret = true;
		}
		return ret;
	}

	private void setPreviewFlipY(){
		if (mPreviewScreen != null){
			YYLog.info(this,Constant.MEDIACODE_VIEW+"preview setPreviewFlipY");
			mPreviewScreen.setTextureFlipY(FullFrameRect.MAIN_TEXTURE);
			mPreviewScreen.setTextureFlipY(FullFrameRect.EXTRA_TEXTURE1);
			mPreviewScreen.setTextureFlipY(FullFrameRect.EXTRA_TEXTURE2);
		}
	}

    public void setPreviewFlipX() {
        if (mPreviewScreen != null) {
            YYLog.info(this, Constant.MEDIACODE_VIEW + "preview setPreviewFlipX");
            mPreviewScreen.setTextureFlipX(FullFrameRect.MAIN_TEXTURE);
            mPreviewScreen.setTextureFlipX(FullFrameRect.EXTRA_TEXTURE1);
            mPreviewScreen.setTextureFlipX(FullFrameRect.EXTRA_TEXTURE2);
        }
    }

    private void releasePreviewStaffs() {


		if(mFrameBufferTexture[0] != -1){
			OpenGlUtils.releaseFrameBuffer(1, mFrameBufferTexture, mFrameBuffer);
			mFrameBufferTexture[0] = -1;
			mFrameBuffer[0] = -1;
		}

        if (mPreviewWindowSurface != null) {
            //mEglCore.makeCurrent(mEnvSurface); //TODO: need to reset the eglcontext of gl manager?
			mFilterContext.getGLManager().resetContext();
            mPreviewWindowSurface.release();
            mPreviewWindowSurface = null;

			YYLog.info(this, Constant.MEDIACODE_VIEW+"[tracer] release prview window surface!!");
        }
	}
	
    private void updateVideoPosition() {
		VideoSizeUtils.Size  si = VideoSizeUtils.CalcFitSize(mImageWidth, mImageHeight, mSurfaceWidth, mSurfaceHeight, Constant.ScaleMode.AspectFit);;
//		if(BasketballGameManager.isUseBasketBallFilter()){
//			 si = VideoSizeUtils.CalcFitSize(mImageWidth, mImageHeight, mSurfaceWidth, mSurfaceHeight, Constant.ScaleMode.AspectFit);
//		}else{
//			 si = VideoSizeUtils.CalcFitSize(mImageWidth, mImageHeight, mSurfaceWidth, mSurfaceHeight, Constant.ScaleMode.AspectFill);
//		}

       mViewX = si.x;
       mViewY = si.y;
       mViewWidth = si.width;
       mViewHeight = si.height;

		YYLog.info(this, Constant.MEDIACODE_VIEW+"updateVideoPosition surfaceWidth="+mSurfaceWidth+" surfaceHeight="+mSurfaceHeight);
		YYLog.info(this, Constant.MEDIACODE_VIEW+"updateVideoPosition, View.x="+mViewX + " View.y="+mViewY + " ViewWidth="+mViewWidth + " ViewHeight="+mViewHeight);
    }
	
	private void handleSurfaceChanged(AbstractSurfaceInfo sfInfo)
	{
	    try {
            releasePreviewStaffs();
			mSurfaceInfo = sfInfo;
            mPreviewWindowSurface = EglFactory.newWindowSurface(mFilterContext.getGLManager().getEglCore(), mSurfaceInfo, false);
            mSurfaceWidth = sfInfo.mWidth;
            mSurfaceHeight = sfInfo.mHeight;

			YYLog.info(this, Constant.MEDIACODE_VIEW+"[tracer] create preview window surface!!");
            updateVideoPosition();
        }
        catch (Throwable t) {
            t.printStackTrace();
			YYLog.error(this, Constant.MEDIACODE_VIEW+"[exception] handleSurfaceChanged exception: " + t.toString());
        }
	}


	public void onSurfaceChanged(final AbstractSurfaceInfo sfInfo)
	{
		YYLog.info(this, Constant.MEDIACODE_VIEW+"onSurfaceChanged change, width="+sfInfo.mWidth + " height="+sfInfo.mHeight);
		if(Thread.currentThread().getId() == mFilterContext.getGLManager().getThreadId()) {
			handleSurfaceChanged(sfInfo);
		} else {
			mFilterContext.getGLManager().post(new Runnable() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					handleSurfaceChanged(sfInfo);
				}
			});	
		}
	}
	
	private void handleSurfaceDestroy() {
		releasePreviewStaffs();
		mSurfaceInfo = null;
	}
	
	public void onSurfaceDestroy()
	{
		YYLog.info(this, Constant.MEDIACODE_VIEW+"onSurfaceDestroy ");
		if(Thread.currentThread().getId() == mFilterContext.getGLManager().getThreadId()) {
			handleSurfaceDestroy();
		} else {
			mFilterContext.getGLManager().post(new Runnable() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					handleSurfaceDestroy();
				}
			});	
		}
	}

	public void setSurfaceValid(boolean surfaceValid) {
		mSurfaceValid.set(surfaceValid);
	}

    public Bitmap getLastBitmap() {
        if (!mInited.get()) {
            return null;
        }

        int width = mSurfaceWidth / 4;
        int height = mSurfaceHeight / 4;

        Bitmap bitmap = null;

        if (checkImageSizeUpdated(mLastYYMediaSample.mWidth, mLastYYMediaSample.mHeight, true)) {
            updateVideoPosition();
        }
        if (mPreviewWindowSurface != null) {
            try {
                mPreviewWindowSurface.makeCurrent();

                ByteBuffer byteBuffer = ByteBuffer.allocate(width * height * 4);
				if(mFrameBufferTexture[0] == -1) {
					OpenGlUtils.createFrameBuffer(width, height, mFrameBuffer, mFrameBufferTexture, 1);
				}

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
                GLES20.glClearColor(0, 0, 0, 0);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				GLES20.glViewport(0, 0, width, height);

                // 算纹理坐标 裁剪纹理
                if (checkClipRatioChanged(mLastYYMediaSample.mWidth, mLastYYMediaSample.mHeight, mLastYYMediaSample.mClipWidth, mLastYYMediaSample.mClipHeight)) {
                    YYLog.info(this, Constant.MEDIACODE_VIEW + "VideoSize X:" + mViewX + " Y:" + mViewY + " width:" + mViewWidth + " height:" + mViewHeight);
                    mPreviewScreen.adjustTexture(mLastYYMediaSample.mWidth, mLastYYMediaSample.mHeight, mLastYYMediaSample.mClipWidth, mLastYYMediaSample.mClipHeight);
                }

                mPreviewScreen.drawFrameForBlurBitmap(mLastYYMediaSample.mTextureId, mLastYYMediaSample.mTransform, mFilterContext.getWatermarkTextureID(),
                        mFilterContext.getDynamicTextureID(), OpenGlUtils.VERTEXCOORD_BUFFER_UPDOWN);

                GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer);
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(byteBuffer);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            } catch (Throwable t) {
                t.printStackTrace();
                YYLog.error(this, Constant.MEDIACODE_VIEW + "[exception] exception occur, " + t.toString());
                releasePreviewStaffs();
            }
        }

        return bitmap;
    }

	public void setPreviewStart(boolean previewStart) {
		this.mPreviewStart = previewStart;
	}
    private Bitmap getMirrorBitmap(Bitmap src, int newWidth, int newHeight, boolean flipX, AspectRatioType aspect) {
        int width = src.getWidth();
        int height = src.getHeight();

        Matrix matrix = new Matrix();
        //matrix.preScale(1, -1);
        if (flipX) {
            matrix.preScale(-1.0f, 1.0f);   // mirror by X axis
        }

        Rect r = ImageSizeUtil.getCropRect(width, height, aspect);

        int adjustWidth = (r.right-r.left);
        int adjustHeight = ((r.bottom-r.top));
        float scaleWidth = ((float) newWidth) / adjustWidth;
        float scaleHeight = ((float) newHeight) / adjustHeight;
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        Bitmap result = Bitmap.createBitmap(
                src, r.left, r.top, (r.right-r.left), (r.bottom-r.top), matrix, true);
        src.recycle();
        return result;
    }

    // !!! warning!!! 在有些手机上无法直接读取屏幕 framebuffer 0 上的数据
    private Bitmap getScreenBitmap(int x, int y, int width, int height, int newW, int newH, boolean flipX, AspectRatioType aspect) {
        OpenGlUtils.checkGlError("getScreenBitmap  enter... ");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        ByteBuffer mByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
        mByteBuffer.order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(x, y, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mByteBuffer);
        OpenGlUtils.checkGlError("glReadPixels ");
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (bitmap != null) {
            bitmap.copyPixelsFromBuffer(mByteBuffer);
            return getMirrorBitmap(bitmap, newW, newH, flipX, aspect);
        }
        OpenGlUtils.checkGlError("getScreenBitmap  out... ");
        return null;
    }

    private Bitmap getLastBitmapFromTexture(int newW, int newH, boolean flipX, AspectRatioType aspect) {
        if (mLastYYMediaSample == null || mLastYYMediaSample.mTextureId < 0) {
            return null;
        }
        Bitmap bitmap = null;
        int[] frameBuffers = new int[1];
        int textureId = mLastYYMediaSample.mTextureId;
        int width = mLastYYMediaSample.mWidth;
        int height = mLastYYMediaSample.mHeight;

        GLES20.glGenFramebuffers(1, frameBuffers, 0);
        OpenGlUtils.checkGlError("glGenFramebuffers ");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0]);
        OpenGlUtils.checkGlError("glBindFramebuffer ");
        if (frameBuffers[0] != 0 && textureId != 0 && width > 0 && height > 0) {
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0);
            OpenGlUtils.checkGlError("glFramebufferTexture2D ");
            ByteBuffer mByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
            mByteBuffer.order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mByteBuffer);
            OpenGlUtils.checkGlError("glReadPixels ");
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            if (bitmap != null) {
                bitmap.copyPixelsFromBuffer(mByteBuffer);
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDeleteFramebuffers(1, frameBuffers, 0);
        return bitmap == null ? null : getMirrorBitmap(bitmap, newW, newH, flipX, aspect);
    }
    private void notifyResult(int result, String path) {
        if (mPreviewSnapshotListener != null) {
            mPreviewSnapshotListener.onScreenSnapshot(result, path);
        }
    }
    private void takeSnapshot(final String path, final int width, final int height, final int type, final int quality, boolean flipX) {
        final long time = System.currentTimeMillis();
        final Bitmap bmp = getLastBitmapFromTexture(width, height, flipX, mAspect);
        YYLog.info(TAG, "takeSnapshot getLastBitmapFromTexture cost :" + (System.currentTimeMillis() - time));

        mSingleThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (null == bmp) {
                    YYLog.error(TAG, "takePicture error ! bmp == null. ");
                    notifyResult(-1, path);
                    return;
                }

                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(path);
                } catch (FileNotFoundException e) {
                    YYLog.error(TAG, String.format(Locale.getDefault(), "%s not found: %s", path, e.toString()));
                }
                if (out == null) {
                    notifyResult(-1, path);
                    bmp.recycle();
                    return;
                }
                Bitmap.CompressFormat format = (type == 0 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG);
                boolean ret = bmp.compress(format, quality, out);
                try {
                    out.flush();
                    out.close();
                    notifyResult(0, path);
                } catch (IOException e) {
                    YYLog.error(TAG, "save to file failed: IOException happened:" + e.toString());
                    ret = false;
                    notifyResult(-1, path);
                } finally {
                    bmp.recycle();
                }
                YYLog.info(TAG, "takeSnapshot " + path + " ret : " + ret + " cost :" + (System.currentTimeMillis() -  time));
            }
        });
    }

    public void setAspectRatioListener(IChangeAspectRatioListener listener) {
        mAspectRatioListener = listener;
    }

    public void setAspectWithDynamicEffect(boolean b) {
        bEffect = b;
    }

    public void setAspectRatio(AspectRatioType aspectRatio, int x_offset, int y_offset) {
        YYLog.info(TAG, " setAspectRatio " + aspectRatio + " x_offset " + x_offset + " y_offset " + y_offset + " bEffect " + bEffect);
        if (aspectRatio != mAspect || x_offset != m_xOffset || y_offset != m_yOffset) {
            mAspect = aspectRatio;
            m_xOffset = x_offset;
            m_yOffset = y_offset;
            if (!bEffect) {
                mEffectStep = 1;
                updataAspectRatio();
                mEffectStep = DEFAULT_EFFECT_STEP;
            } else {
                startScaleEffectTimer();
            }
        }
		mHaveSetOffset = true;
    }

    public void setPreviewSnapshotListener(IPreviewSnapshotListener listener) {
        mPreviewSnapshotListener = listener;
    }

    public void takePreviewSnapshot(String path, int width, int height, int type, int quality, boolean flipX) {

        takeSnapshot(path, width, height, type, quality, flipX);

    }

    public void setPreviewRectOffset(int x_offset, int y_offset) {
//		m_xOffset = x_offset;
//		m_yOffset = y_offset;
//		YYLog.info(TAG, "setPreviewRectOffset  x_offset " + x_offset + " y_offset " + y_offset);
    }
}
