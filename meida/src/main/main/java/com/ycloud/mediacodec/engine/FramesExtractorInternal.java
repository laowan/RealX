package com.ycloud.mediacodec.engine;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;

import com.ycloud.mediacodec.format.MediaFormatExtraConstants;
import com.ycloud.mediacodec.utils.MediaExtractorUtils;
import com.ycloud.utils.OpenGlUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

/**
 * Created by dzhj on 17/5/2.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class FramesExtractorInternal implements  Runnable  {


    private static final String TAG = FramesExtractorInternal.class.getSimpleName();
    private static final int DRAIN_STATE_NONE = 0;
    private static final int DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1;
    private static final int DRAIN_STATE_CONSUMED = 2;

    private MediaExtractor mExtractor;
    private int mTrackIndex;
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec mDecoder;
    private ByteBuffer[] mDecoderInputBuffers;
    private MediaFormat mActualOutputFormat;
    private OutputSurface mDecoderOutputSurfaceWrapper;
    private boolean mIsExtractorEOS;
    private boolean mIsDecoderEOS;
    private boolean mDecoderStarted;
    SurfaceTexture mSurfaceTexture;
    int mWidth;
    int mHeight;

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    String mVideoFilePath;
    String mOutpath;

    private int mSnapIndex;

    private ByteBuffer mByteBuffer;

    private MediaTranscoderEngine.ProgressCallback mProgressCallback;
    private volatile float mProgress;
    private long mDurationUs;
    private long mWrittenPresentationTimeUs;

    private static final float PROGRESS_UNKNOWN = -1.0f;
    private static final long SLEEP_TO_WAIT_TRACK_TRANSCODERS = 10;
    private static final long PROGRESS_INTERVAL_STEPS = 10;


    public FramesExtractorInternal(String videoFilePath, String outputPath) {
        mVideoFilePath = videoFilePath;
        mOutpath = outputPath;
        mSnapIndex = 0;


    }

    public void start(){
        new Thread(this, SDK_NAME_PREFIX +FramesExtractorInternal.class.getSimpleName()).run();
    }

    @Override
    public void run() {

        FileInputStream fileInputStream = null;
        FileDescriptor inFileDescriptor;
        try {
            fileInputStream = new FileInputStream(mVideoFilePath);
            inFileDescriptor = fileInputStream.getFD();
        } catch (IOException e) {
            e.printStackTrace();
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException eClose) {
                    Log.e(TAG, "Can't close input stream: ", eClose);
                }
            }

            return;
        }


        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(inFileDescriptor);
        try {
            mDurationUs = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
        } catch (NumberFormatException e) {
            mDurationUs = -1;
        }


        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(inFileDescriptor);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mTrackIndex = MediaExtractorUtils.getFirstVideoTrack(mExtractor);
        mExtractor.selectTrack(mTrackIndex);
        MediaFormat inputFormat = mExtractor.getTrackFormat(mTrackIndex);
        mWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        if (inputFormat.containsKey(MediaFormatExtraConstants.KEY_ROTATION_DEGREES)) {
            // Decoded video is rotated automatically in Android 5.0 lollipop.
            // Turn off here because we don't want to encode rotated one.
            // refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
            inputFormat.setInteger(MediaFormatExtraConstants.KEY_ROTATION_DEGREES, 0);
        }

        eglInit();
        mDecoderOutputSurfaceWrapper = new OutputSurface();



        try {
            mDecoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        mDecoder.configure(inputFormat, mDecoderOutputSurfaceWrapper.getSurface(), null, 0);
        mDecoder.start();
        mDecoderStarted = true;
        mDecoderInputBuffers = mDecoder.getInputBuffers();

        runPipelines();
    }

    private void eglInit(){

        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        mSurfaceTexture = new SurfaceTexture(texture[0]);


        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }
        // Configure EGL for recordable and OpenGL ES 2.0.  We want enough RGB bits
        // to minimize artifacts from possible YUV conversion.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }
        // Configure context for OpenGL ES 2.0.
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attrib_list, 0);
        OpenGlUtils.checkGlError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }
        // Create a window surface, and attach it to the Surface we received.
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0],mSurfaceTexture,
                surfaceAttribs, 0);
        OpenGlUtils.checkGlError("eglCreateWindowSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }

        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    private void runPipelines() {
        long loopCount = 0;
        if (mDurationUs <= 0) {
            float progress = PROGRESS_UNKNOWN;
            mProgress = progress;
            if (mProgressCallback != null)
                mProgressCallback.onProgress(progress); // unknown
        }
        while (!mIsDecoderEOS) {
            boolean stepped = stepPipeline();
            loopCount++;
            if (mDurationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0) {
                double videoProgress = mIsDecoderEOS? 1.0 : Math.min(1.0, (double)getWrittenPresentationTimeUs() / mDurationUs);
                mProgress = (float) videoProgress;
                if (mProgressCallback != null)
                    mProgressCallback.onProgress(mProgress);
            }
            if (!stepped) {
                try {
                    Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS);
                } catch (InterruptedException e) {
                    // nothing to do
                }
            }
        }
    }

    public long getWrittenPresentationTimeUs() {
        return mWrittenPresentationTimeUs;
    }

    public boolean stepPipeline() {
        boolean busy = false;
        int status;

        do {
            status = drainDecoder(0);
            if (status != DRAIN_STATE_NONE)
                busy = true;
            // NOTE: not repeating to keep from deadlock when encoder is full.
        } while (status == DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY);
        while (drainExtractor(0) != DRAIN_STATE_NONE)
            busy = true;

        return busy;
    }

    public void release() {
        if (mDecoderOutputSurfaceWrapper != null) {
            mDecoderOutputSurfaceWrapper.release();
            mDecoderOutputSurfaceWrapper = null;
        }
        if (mDecoder != null) {
            if (mDecoderStarted)
                mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }
    }

    private int drainExtractor(long timeoutUs) {
        if (mIsExtractorEOS)
            return DRAIN_STATE_NONE;
        int trackIndex = mExtractor.getSampleTrackIndex();
        if (trackIndex >= 0 && trackIndex != mTrackIndex) {
            return DRAIN_STATE_NONE;
        }
        int result = mDecoder.dequeueInputBuffer(timeoutUs);
        if (result < 0)
            return DRAIN_STATE_NONE;
        if (trackIndex < 0) {
            mIsExtractorEOS = true;
            mDecoder.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return DRAIN_STATE_NONE;
        }
        int sampleSize = mExtractor.readSampleData(mDecoderInputBuffers[result], 0);
        boolean isKeyFrame = (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
        mDecoder.queueInputBuffer(result, 0, sampleSize, mExtractor.getSampleTime(), isKeyFrame ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0);
        mExtractor.advance();
        return DRAIN_STATE_CONSUMED;
    }

    private int drainDecoder(long timeoutUs) {
        if (mIsDecoderEOS)
            return DRAIN_STATE_NONE;
        int result = mDecoder.dequeueOutputBuffer(mBufferInfo, timeoutUs);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return DRAIN_STATE_NONE;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mIsDecoderEOS = true;
            mBufferInfo.size = 0;
        }
        boolean doRender = (mBufferInfo.size > 0);
        // NOTE: doRender will block if buffer (of encoder) is full.
        // Refer: http://bigflake.com/mediacodec/CameraToMpegTest.java.txt
        mDecoder.releaseOutputBuffer(result, doRender);
        if (doRender) {
            mDecoderOutputSurfaceWrapper.awaitNewImage();
            mDecoderOutputSurfaceWrapper.drawImage();
            mWrittenPresentationTimeUs = mBufferInfo.presentationTimeUs * 1000;
            EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
            takePicture();
        }
        return DRAIN_STATE_CONSUMED;
    }

    public void takePicture() {
        try {
            if(mByteBuffer == null) {
                mByteBuffer = ByteBuffer.allocateDirect(mWidth * mHeight * 4);
            }
            mByteBuffer.clear();
            mByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            GLES20.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mByteBuffer);
            Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(mByteBuffer);
            saveToFile(bitmap);
        } catch (Throwable e) {
            Log.e(TAG, "takePicture error:" + e.toString());
        }
    }

    private void saveToFile(final Bitmap bmp) {
        mSnapIndex++;
        FileOutputStream out = null;
        String finalFilePath = mOutpath + File.separator + mSnapIndex + ".jpg";
        try {
            out = new FileOutputStream(finalFilePath);
        } catch (FileNotFoundException e) {
            Log.e(TAG, finalFilePath + "not found:" + e.toString());
        }
        if (out == null) {
            return;
        }
        bmp.compress(Bitmap.CompressFormat.JPEG, 50, out);
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "save to file failed: IOException happened:" + e.toString());
        } finally {
            out = null;
        }
    }
}
