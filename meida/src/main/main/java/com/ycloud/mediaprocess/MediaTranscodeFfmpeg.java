package com.ycloud.mediaprocess;

import android.content.Context;

import com.ycloud.VideoProcessTracer;
import com.ycloud.api.process.MediaInfo;
import com.ycloud.audio.SilenceWaveFileGenerator;
import com.ycloud.common.Constant;
import com.ycloud.mediacodec.engine.IMediaTranscoder;
import com.ycloud.mediarecord.MediaBase;
import com.ycloud.mediarecord.MediaNative;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.ExecutorUtils;
import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;

import java.io.File;

public class MediaTranscodeFfmpeg extends MediaBase implements IMediaTranscoder {

    public static final String TAG = MediaTranscodeFfmpeg.class.getSimpleName();
    public static final int PRESET_OUTPUT_WIDTH = 540;
    public static final int PRESET_OUTPUT_HEIGHT = 960;
    private float mStartTime = -1;
    private float mTotalTime = -1;
    private float mFrameRate = 0;
    private int mBitrate = 36 * 1000 * 1000;
    private int mMinRate = 36 * 1000 * 1000;
    private int mMaxRate = 36 * 1000 * 1000;
    private int mBufsize = 72 * 1000 * 1000;
    private int mGop = 2;
    private int mCrf = 0;
    private int mOutputWidth;
    private int mOutputHeight;
    private String mInputPath;
    private String mOutputPath;

    private int mCropWidth;
    private int mCropHeight;
    private int mCropOffsetX;
    private int mCropOffsetY;
    private boolean needCrop;

    private double mSnapshotFreq = 2.0;
    private String mSnapshotPath;
    private String mSnapshotFileType = "jpg";
    private String mSnapshotPrefix;

    private float mRotateAngle = 0.0f;
    private boolean mFixedResolution = false;
    private Context mContext = null;
    private String mSilenceAudioFilePath = null;

    public MediaTranscodeFfmpeg() {
        super();
        setExcuteCmdId(MediaNative.libffmpeg_cmd_transcode);
        needCrop = false;
    }

    public MediaTranscodeFfmpeg(Context context) {
        super();
        mContext = context;
        setExcuteCmdId(MediaNative.libffmpeg_cmd_transcode);
        needCrop = false;
    }

    public void setMediaTime(float startTime, float totalTime) {
        if (startTime < 0 || totalTime < 0) {
            return;
        }
        mStartTime = startTime;
        mTotalTime = totalTime;
    }

    @Override
    public void setPath(String sourcePath, String outputPath) {
        YYLog.info(this, Constant.MEDIACODE_TRANSCODE + " MediaTranscodeFfmpeg setPath=" + sourcePath + " outputPath=" + outputPath);
        mInputPath = sourcePath;
        mOutputPath = outputPath;

        if (isFFMpegRunning()) {
            cancel();
        }

        // 目前此逻辑是由于业务层还没修改FFmpeg运行状态的判断的处理,所以先内部处理
        int totalTime = 0;
        while (isFFMpegProcessCancelled()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            totalTime++;
            if (totalTime > 3) {
                break;
            }
        }
        // 与 videoTranscode() 中getMediaInfo中重复
//        MediaInfo mediaInfo = MediaUtils.getMediaInfo(sourcePath);
//        if (mediaInfo != null) {
//            setTotalFrame(mediaInfo.total_frame);
//        }
    }

    @Override
    public void setForceRotateAngle(float angle) {
        mRotateAngle = angle;
    }

    public void setVideoSize(int width, int height) {
        YYLog.info(this, Constant.MEDIACODE_TRANSCODE+" setVideoSize width:"+width+" height:"+height);
        mOutputWidth = width;
        mOutputHeight = height;
        mFixedResolution = true;
    }

    public void setCropField(int width, int height, int offsetX, int offsetY){
        YYLog.info(this, Constant.MEDIACODE_TRANSCODE+" setCropField width:"+width+" height:"+height+" X:"+offsetX+" Y:"+offsetY);
        if (width != 0 || height != 0 || offsetX != 0 || offsetY != 0){
            needCrop = true;
        }
        mCropWidth = width;
        mCropHeight = height;
        mCropOffsetX = offsetX;
        mCropOffsetY = offsetY;
    }

    public void setSnapshotPath(String path) {
        YYLog.info(TAG, Constant.MEDIACODE_TRANSCODE + "setSnapshotPath:" + path);
        mSnapshotPath = path;
    }

    public void setSnapshotFrequency(float frequency) {
        YYLog.info(TAG, Constant.MEDIACODE_TRANSCODE + "setSnapshotFrequency:" + frequency);
        mSnapshotFreq = frequency;
    }

    public void setSnapshotFileType(String fileType) {
        if (FileUtils.isSnapshotSupport(fileType))
            mSnapshotFileType = fileType;
    }

    public void setSnapshotPrefix(String prefix) {
        mSnapshotPrefix = prefix;
    }

    public void setFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }

    public void setBitrate(int bitrate) {
        mBitrate = bitrate;
    }

    public void setGop(int gop) {
        mGop = gop;
    }

    public void setCrf(int crf) {
        mCrf = crf;
    }

    public void setBitrateRange(int minRate, int maxtRate, int bufsize) {
        mMinRate = minRate;
        mMaxRate = maxtRate;
        mBufsize = bufsize;
    }

    protected boolean execute() {
        FileUtils.createFile(mOutputPath);
        if (!FileUtils.checkPath(mInputPath) || !FileUtils.checkFile(mOutputPath)) {
            return false;
        }

        return videoTranscode();
    }

    /**
     * Warning !!! -> FFmpeg arguments are order sensitive
     * @return
     */
    private boolean videoTranscode() {
        MediaInfo mediaInfo = MediaUtils.getMediaInfo(mInputPath);
        // 外部导入无音频的视频MP4文件，转码时填充mute音频，不然多个视频拼接时会出现音视频不同步。
        if (mContext != null && mediaInfo != null && (mediaInfo.audio_codec_name == null || mediaInfo.audio_duration <= 0) ) {
            String cacheDir = com.ycloud.common.FileUtils.getDiskCacheDir(mContext) + File.separator;
            mSilenceAudioFilePath = cacheDir + "muteAudio.wav";
            SilenceWaveFileGenerator.write(mSilenceAudioFilePath, (long)mediaInfo.video_duration * 1000);
            YYLog.warn(TAG, "Add mute audio file :" + mSilenceAudioFilePath);
        }

        boolean bRotate = false;
        // check parameters
        do {
            if (mediaInfo == null || mediaInfo.width == 0 || mediaInfo.height == 0) {
                return false;
            }

            if (!FileUtils.isVideoTypeSupport(mediaInfo.format_name)) {
                FileUtils.deleteDir(mOutputPath);
                return false;
            }

            if (mediaInfo.v_rotate == 90.0 || mediaInfo.v_rotate == -270.0 || mediaInfo.v_rotate == -90 || mediaInfo.v_rotate == 270) {
                bRotate = true;
            }

        } while (false);

        long begin = System.currentTimeMillis();

        int rotatedWidth = mediaInfo.width;
        int rotatedHeight = mediaInfo.height;
        // 如果原视频有旋转参数，则对换其输出宽高，这样开发者调用api不需要关心原视频是否有旋转参数，只关注视频按比例输出
        if (bRotate) {
            int tmp = rotatedWidth;
            rotatedWidth = rotatedHeight;
            rotatedHeight = tmp;
        }

        float requestRatio = 0.0f;
        if (mOutputWidth > 0 && mOutputHeight > 0){
            requestRatio = (float)(1.0* mOutputWidth / mOutputHeight);
        }

        //短的那边上限定为PRESET_OUTPUT_WIDTH，此宽度应该可以让不同的app设置，目前hardcode为540
        if ((!needCrop || mOutputWidth == 0 || mOutputHeight == 0) && !mFixedResolution) {
            int resultWidth = rotatedWidth;
            int resultHeight = rotatedHeight;
            if (requestRatio == 0.0f && needCrop) {
                requestRatio = (float) (1.0 * mCropWidth / mCropHeight);
            }
            if (rotatedWidth > rotatedHeight) {
                mOutputHeight = resultHeight > PRESET_OUTPUT_WIDTH ? PRESET_OUTPUT_WIDTH : (mOutputHeight > 0 ? mOutputHeight : resultHeight);
                if (requestRatio > 0) {
                    mOutputWidth = (int) ((float) mOutputHeight / requestRatio);
                } else {
                    mOutputWidth = (int) (1.0 * resultWidth * mOutputHeight / resultHeight);
                }
            } else {
                mOutputWidth = resultWidth > PRESET_OUTPUT_WIDTH ? PRESET_OUTPUT_WIDTH : (mOutputWidth > 0 ? mOutputWidth : resultWidth);
                if (requestRatio > 0) {
                    mOutputHeight = (int) ((float) mOutputWidth / requestRatio);
                } else {
                    mOutputHeight = (int) (1.0 * resultHeight * mOutputWidth / resultWidth);
                }
            }
        }

        //宽高做16位对齐
        mOutputWidth += ((mOutputWidth % 16) == 0 ? 0 : (16 - (mOutputWidth % 16)));
        mOutputHeight += ((mOutputHeight % 16) == 0 ? 0 : (16 - (mOutputHeight % 16)));

        YYLog.info(this,Constant.MEDIACODE_TRANSCODE+" ffmpeg transcode rotate:"+mediaInfo.v_rotate+" cropWidth:"+mCropWidth+" cropHeight:"+mCropHeight+" cropOffsetX:"+mCropOffsetX+" cropOffsetY:"+mCropOffsetY+" outputWidth:"+mOutputWidth+" outputHeight:"+mOutputHeight);

        StringBuilder cmdStrBer = new StringBuilder();
        cmdStrBer.append("ffmpeg -y ");

        // -ss 放到 -i 后面会导致seek 到-ss指定的时间点极慢！！！！
        if (mStartTime != -1 && mTotalTime != -1) {
            cmdStrBer.append("-ss " + mStartTime + " ");
        }

        cmdStrBer.append("-i \"" + mInputPath + "\" ");

        if (mSilenceAudioFilePath != null && FileUtils.checkPath(mSilenceAudioFilePath)) {
            cmdStrBer.append("-i \"" + mSilenceAudioFilePath + "\" ");
        }

        cmdStrBer.append("-profile:v high ");
        if (needCrop){
            cmdStrBer.append("-filter_complex \"crop="+mCropWidth+":"+mCropHeight+":"+mCropOffsetX+":"+mCropOffsetY+",scale="+mOutputWidth+":"+mOutputHeight+"\" ");
        } else if (mFixedResolution) {  // 转码设置固定分辨率，改变宽高比时，加黑边
            float absAngle = Math.abs(mRotateAngle);
            if (absAngle == 0.0f) {
                cmdStrBer.append("-filter_complex \"scale=" + mOutputWidth + ":" + mOutputHeight + ":force_original_aspect_ratio=1,pad="
                        + mOutputWidth + ":" + mOutputHeight + ":" + "(ow-iw)/2:(oh-ih)/2:color=black" + "\" ");
            } else {
                if (absAngle == 90.0f || absAngle == 270.0f) { // 这里指定输出分辨率，必须先做旋转，再缩放
                    cmdStrBer.append("-filter_complex \"rotate=" + mRotateAngle + "*PI/180:out_w=ih:out_h=iw" +",scale=" + mOutputWidth + ":" + mOutputHeight
                            +":force_original_aspect_ratio=1,pad="+ mOutputWidth + ":" + mOutputHeight + ":" + "(ow-iw)/2:(oh-ih)/2:color=black"
                            +"\" ");
                } else if (absAngle == 180) {
                    cmdStrBer.append("-filter_complex \"scale=" + mOutputWidth + ":" + mOutputHeight + ":force_original_aspect_ratio=1,pad="
                            + mOutputWidth + ":" + mOutputHeight + ":" + "(ow-iw)/2:(oh-ih)/2:color=black"
                            + ",rotate=" + mRotateAngle + "*PI/180" +
                            "\" ");
                }
            }
            cmdStrBer.append("-r 30 ");  // 拼接视频时，如果采用非转码模式，多个视频的帧率，分辨率等参数必须一致，不然会音视频不同步
        } else{
            float absAngle = Math.abs(mRotateAngle);
            if (absAngle == 0.0f) {
                cmdStrBer.append("-filter_complex \"scale=" + mOutputWidth + ":" + mOutputHeight + "\" ");
            } else {
                if (absAngle == 90.0f || absAngle == 270.0f) { // 先做缩放，旋转速度会更快
                    cmdStrBer.append("-filter_complex \"scale=" + mOutputWidth + ":" + mOutputHeight + ",rotate=" + mRotateAngle + "*PI/180:out_w=ih:out_h=iw" + "\" ");
                } else if(absAngle == 180) {
                    cmdStrBer.append("-filter_complex \"scale=" + mOutputWidth + ":" + mOutputHeight + ",rotate=" + mRotateAngle + "*PI/180" + "\" ");
                }
            }
        }
        if (mFixedResolution) {  // 拼接视频时，如果采用非转码模式，多个视频中音频的参数必须一致，不然会音视频不同步
            cmdStrBer.append("-c:a libfdk_aac -strict -2 -ar 44100 -ac 2 -vcodec libx264 ");
        } else {
            cmdStrBer.append("-c:a libfdk_aac -strict -2 -vcodec libx264 ");
        }
        cmdStrBer.append("-preset ultrafast ");
        cmdStrBer.append("-crf 5 ");
        // fix :ffmpeg -> Too many packets buffered for output stream error.
        cmdStrBer.append("-max_muxing_queue_size 1024 ");

        if (mGop != 0)
            cmdStrBer.append("-g " + mGop + " ");

        if (mSilenceAudioFilePath != null && FileUtils.checkPath(mSilenceAudioFilePath)) {
            cmdStrBer.append("-shortest ");
        }

        if (mStartTime != -1 && mTotalTime != -1) {
            cmdStrBer.append("-t " + mTotalTime + " ");
            setTotalFrame((int)(mTotalTime * mediaInfo.frame_rate));
        } else {
            setTotalFrame(mediaInfo.total_frame);
        }

        VideoProcessTracer.getInstace().setResolution(mOutputWidth+"x"+mOutputHeight);
        cmdStrBer.append(mOutputPath);

        //在转码过程中截图
        if (mSnapshotPath != null && !"".equals(mSnapshotPath)) {
            if (mStartTime != -1 && mTotalTime != -1) {
                cmdStrBer.append(" -ss " + mStartTime + " ");
                cmdStrBer.append(" -t " + mTotalTime + " ");
            }

            cmdStrBer.append(" -f image2 -r " + mSnapshotFreq + " -b:v 10000k " + "\"" + mSnapshotPath
                    + "" + mSnapshotPrefix + "%3d." + mSnapshotFileType + "\"");
        }

        boolean ret = executeCmd(cmdStrBer.toString());
        YYLog.info(this,Constant.MEDIACODE_TRANSCODE+" ffmpeg transcode cost time "+((float)(System.currentTimeMillis()-begin))/1000);

        if (mSilenceAudioFilePath != null && FileUtils.checkPath(mSilenceAudioFilePath)) {
            FileUtils.deleteDir(mSilenceAudioFilePath);
        }
        return ret;

    }

  public void transcode(){
      ExecutorUtils.getBackgroundExecutor(TAG).execute(new Runnable() {
          @Override
          public void run() {
              execute();
          }
      });
  }

}
