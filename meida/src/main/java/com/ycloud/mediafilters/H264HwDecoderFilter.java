package com.ycloud.mediafilters;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.GLES11Ext;
import android.os.Build;
import android.view.Surface;

import com.ycloud.api.common.SampleType;
import com.ycloud.common.Constant;
import com.ycloud.mediacodec.AbstractMediaCodecDecoder;
import com.ycloud.mediacodec.AbstractMediaCodecDecoderAsync;
import com.ycloud.mediacodec.VideoDecodeType;
import com.ycloud.mediacodec.VideoEncoderType;
import com.ycloud.mediacodec.videocodec.VideoMediaCodecDecoderAsync;
import com.ycloud.mediaprocess.StateMonitor;
import com.ycloud.svplayer.MediaConst;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Administrator on 2018/1/2.
 */

public class H264HwDecoderFilter extends AbstractMediaDecoderFilter
        implements SurfaceTexture.OnFrameAvailableListener,
        AbstractMediaCodecDecoder.Callback,
        AbstractMediaCodecDecoderAsync.ISampleBufferQueue
{
    VideoMediaCodecDecoderAsync mDecoder = null;
    //surface mode
    Surface                 mSurface = null;
    SurfaceTexture          mSurfaceTexture = null;
    int                     mExtTextureId = -1;
    MediaFilterContext      mVideoFilterContext = null;
    private ConcurrentLinkedQueue<Long> mCachedPtsList = new ConcurrentLinkedQueue<Long>();

    private VideoDecoderGroupFilter mDecoderGroup = null;

    int                     mWidth = 0;
    int                     mHeight  = 0;
    int                     mFrameCnt = 0;
    boolean                mSurfaceMode = false;

    //是否使用decode出来的bufferInfo pts的标识
    private boolean mUsePtsFromBufferInfo = false;

    protected AtomicReference<MediaBufferQueue<YYMediaSample>> mInputBufferQueue = new AtomicReference<>(null);

    public H264HwDecoderFilter(MediaFilterContext videoContext, boolean surfaceMode) {
        mVideoFilterContext = videoContext;
        mSurfaceMode = surfaceMode;
    }

    public void setInputBufferQueue(MediaBufferQueue<YYMediaSample> queue) {
        mInputBufferQueue = new AtomicReference<>(queue);
    }

    public void initDecoderSurface() {
        synchronized(this) {
            if (mDecoder == null) {
                //TODO [预先加载]
                final AtomicBoolean syncObj = new AtomicBoolean(false);
                final H264HwDecoderFilter filter = this;
                mVideoFilterContext.getGLManager().post(new Runnable() {
                    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                    @Override
                    public void run() {
                        //create an oes texutre.
                        mExtTextureId = OpenGlUtils.createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                        mSurfaceTexture = new SurfaceTexture(mExtTextureId);
                        mSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);
                        mSurface = new Surface(mSurfaceTexture);
                        mSurfaceTexture.setOnFrameAvailableListener(filter);
                        //API 21 required
                        //mSurfaceTexture.setOnFrameAvailableListener(this, mVideoFilterContext.getGLManager().getHandler());
                        synchronized (syncObj) {
                            syncObj.set(true);
                            syncObj.notifyAll();
                        }
                    }
                });

                YYLog.info(this, " syncObj check");
                if(!syncObj.get()) {
                    synchronized (syncObj) {
                        if(!syncObj.get()) {
                            try {
                                syncObj.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                YYLog.info(this, " syncObj checked");
                mDecoder = new VideoMediaCodecDecoderAsync(mMediaFormat, mSurface, this);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void initDecoder(MediaFormat format) {
        YYLog.info(this, "H264HwDecoderFilter initDecoder begin");
        super.initDecoder(format);
        mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        mVideoFilterContext.getGLManager().registerFilter(this);

        if(mSurfaceMode) {
            initDecoderSurface();
        } else {
            mDecoder = new VideoMediaCodecDecoderAsync(mMediaFormat, null, this);
        }
        StateMonitor.instance().NotifyDecoderStart(MediaConst.MEDIA_TYPE_VIDEO);
        mDecoder.setCallback(this);
        YYLog.info(this, "H264HwDecoderFilter initDecoder end");
    }

    @Override
    public void deInit() {
        YYLog.info(this, "deInit");
        super.deInit();

        if(mSurface != null) {
            mSurface.release();
        }

        //release surfaceTexture and set null in time to prevent onFrameAvailable callback
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }

        if(mExtTextureId != -1) {
            OpenGlUtils.deleteTexture(mExtTextureId);
        }
    }

    @Override
    public  void  releaseDecoder() {
        YYLog.info(this, "releaseDecoder");
        synchronized (this) {
            if (mDecoder != null) {
                mDecoder.releaseDecoder();
                mDecoder = null;
            }

            mDecoderGroup = null;
        }

        mVideoFilterContext.getGLManager().post(new Runnable() {
            @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            @Override
            public void run() {
                deInit();
            }
        });

    }

    @Override
    public void decodeFrame() {
        if(mDecoder != null) {
            YYLog.debug(this, "decoder: processMediaSample");
            mDecoder.decodeMediaSample(null);
        }
    }

    //decode to surfaceTexture, but the buffer size is out of control
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        //run in gl thread.
        try {
            if(!surfaceTexture.equals(mSurfaceTexture)) {
                YYLog.error(this, "[tracer] H264HwDecoderFilter.handleFrameAvailble, not same surfaceTexture or not initialized");
                return ;
            }

            long beginTime = System.currentTimeMillis();

            surfaceTexture.updateTexImage();
            mVideoFilterContext.getMediaStats().addTextureUpdateImageCost(System.currentTimeMillis() - beginTime);
            YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
            sample.mWidth = mWidth;
            sample.mHeight = mHeight;
            sample.mTextureId = mExtTextureId;
            sample.mTextureTarget = android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
            sample.mAndoridPtsNanos = surfaceTexture.getTimestamp();
            sample.mYYPtsMillions = sample.mAndoridPtsNanos/1000000;
//            YYLog.info(TAG, Constant.MEDIACODE_PTS_EXPORT + "use pts from surfaceTexture=" + sample.mYYPtsMillions);
            sample.mDeliverToEncoder = true;
            sample.mClipWidth = sample.mWidth;
            sample.mClipHeight = sample.mHeight;

            //如果bufferInfo输出的pts列表不为空，则优先使用bufferInfo的pts
            if (!mCachedPtsList.isEmpty()) {
                mUsePtsFromBufferInfo = true;
            } else {
                YYLog.error(TAG, Constant.MEDIACODE_PTS_EXPORT + "decode to queue:cachedPts is empty");
                mUsePtsFromBufferInfo = false;
            }

            if (mUsePtsFromBufferInfo) {
                try {
                    sample.mYYPtsMillions = mCachedPtsList.poll();
//                    YYLog.info(TAG, Constant.MEDIACODE_PTS_EXPORT + "decode to queue: poll pts from queue ");
//                    YYLog.info(TAG, Constant.MEDIACODE_PTS_EXPORT + "use pts from bufferInfo=" + sample.mYYPtsMillions);
                    sample.mAndoridPtsNanos = sample.mYYPtsMillions * 1000000;
                } catch (Exception e) {
                    YYLog.error(TAG, "mCachedPtsList Exception: " + e.toString() + e.getMessage());
                }
            }

            //YYLog.error(TAG, "sample.mYYPtsMillions " + sample.mYYPtsMillions);
            sample.mEncodeWidth = mVideoFilterContext.getVideoEncoderConfig().getEncodeWidth();
            sample.mEncodeHeight = mVideoFilterContext.getVideoEncoderConfig().getEncodeHeight();
            sample.mEncoderType =  mVideoFilterContext.getVideoEncoderConfig().mEncodeType;

            YYLog.debug(this, "[Encoder] decoder: recv decoded frame!!, [pts]" + sample.mAndoridPtsNanos/(1000*1000) + " frameCnt=" + (mFrameCnt));
            surfaceTexture.getTransformMatrix(sample.mTransform);

            mDecoder.setmOutputConsumeCnt(++mFrameCnt);

            StateMonitor.instance().NotifyDecoder(MediaConst.MEDIA_TYPE_VIDEO, sample.mYYPtsMillions);

            deliverToDownStream(sample);
            mVideoFilterContext.getMediaStats().addGLProcessCost(System.currentTimeMillis()-beginTime);

            sample.decRef();

        } catch (Exception e) {
            YYLog.error(this, "H264HwDecoderFilter.onFrameAvaible exception: " + e.toString());
        }
    }

    //output buffer mode.
    @Override
    public void onOutputBuffer(ByteBuffer buffer, MediaCodec.BufferInfo buffInfo, long ptsUs, MediaFormat mediaFormat) {
        mVideoFilterContext.getMediaStats().onVideoFrameDecodeOutput();

        YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();

        sample.mWidth = mWidth;
        sample.mHeight = mHeight;
        sample.mTextureId = -1;
        sample.mTextureTarget = -1;
        sample.mAndoridPtsNanos = ptsUs*1000;
        sample.mYYPtsMillions = sample.mAndoridPtsNanos/1000000;
        sample.mDeliverToEncoder = true;
        sample.mClipWidth = sample.mWidth;
        sample.mClipHeight = sample.mHeight;

        sample.mEncodeWidth = mVideoFilterContext.getVideoEncoderConfig().getEncodeWidth();
        sample.mEncodeHeight = mVideoFilterContext.getVideoEncoderConfig().getEncodeHeight();
        sample.mEncoderType =  mVideoFilterContext.getVideoEncoderConfig().mEncodeType;

        //需要copy一些， buffer为modiacodec的output buffer， 不能被占有.
        sample.mDataByteBuffer = ByteBuffer.allocate(buffInfo.size);
        int oldPos = buffer.position();
        buffer.position(buffInfo.offset);
        sample.mDataByteBuffer.put(buffer);
        buffer.position(oldPos);
        sample.mBufferSize = buffInfo.size;
        sample.mBufferOffset = 0;

        deliverToDownStream(sample);
        sample.decRef();
    }

    @Override
    public void onOutputSurface(MediaFormat format, long ptsUs) {
        //nothging
        if (mSurfaceMode) {
//            YYLog.info(TAG, Constant.MEDIACODE_PTS_EXPORT + "decode to queue step 2: add pts to queue ");
            mCachedPtsList.add(ptsUs / 1000);
        }
        mVideoFilterContext.getMediaStats().onVideoFrameDecodeOutput();
    }

    @Override
    public void onFormatChanged(MediaFormat mediaFormat) {
        //nerver hanppesn
    }

    @Override
    public void onEndOfInputStream() {
        //if surface mode should switch thread, in case the video data later than end of stream
        mCachedPtsList.clear();
        if(mSurfaceMode) {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
                    sample.mSampleType = SampleType.VIDEO;
                    sample.mBufferFlag |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    sample.mEncoderType = VideoEncoderType.HARD_ENCODER_H264;
                    sample.mDeliverToEncoder = true;
                    deliverToDownStream(sample);
                    sample.decRef();
                    StateMonitor.instance().NotifyDecoderEnd(MediaConst.MEDIA_TYPE_VIDEO);
                }
            });
        } else {
            YYMediaSample sample = YYMediaSampleAlloc.instance().alloc();
            sample.mSampleType = SampleType.VIDEO;
            sample.mBufferFlag |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
            sample.mEncoderType = VideoEncoderType.HARD_ENCODER_H264;
            sample.mDeliverToEncoder = true;
            deliverToDownStream(sample);
            sample.decRef();
        }
    }

    @Override
    public void onError(String errMsg) {
        YYLog.error(this, "h264HwDecoder error: " + (errMsg == null ? "" : errMsg));

        if(mDecoderGroup != null) {
            mDecoderGroup.onDecodeError(VideoDecodeType.HARD_DECODE);
        }
        //notify the erorr.
    }

    @Override
    public YYMediaSample peek() {
        MediaBufferQueue<YYMediaSample> queue = mInputBufferQueue.get();
        if(queue != null) {
            return queue.peek();
        }

        return null;
    }

    @Override
    public boolean remove() {
        MediaBufferQueue<YYMediaSample> queue = mInputBufferQueue.get();
        if(queue != null) {
            return queue.remove();
        }
        return false;
    }

    public void setVideoDecoderGroup(VideoDecoderGroupFilter group) {
        mDecoderGroup = group;
    }
}
