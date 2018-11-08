package com.ycloud.mediaprocess;

import com.ycloud.api.config.RecordContants;
import com.ycloud.common.GlobalConfig;
import com.ycloud.mediafilters.AbstractYYMediaFilter;
import com.ycloud.mediafilters.MediaFilterContext;
import com.ycloud.mediafilters.YYMediaFilterListener;
import com.ycloud.svplayer.MediaPlayer;
import com.ycloud.utils.YYLog;
import java.util.Timer;
import java.util.TimerTask;

import static com.ycloud.svplayer.MediaConst.MEDIA_TYPE_AUDIO;
import static com.ycloud.svplayer.MediaConst.MEDIA_TYPE_VIDEO;

/**
 * Created by Administrator on 2018/2/3.
 * This class is only used to statics the MediaExportSession state of the video/audio,DO NOT affect the normal flow of the MediaExport.
 * For Example: How many video/audio frame processed in every module (input, decoder, GPU, encoder, MediaMuxer)
 */

public class StateMonitor extends AbstractYYMediaFilter {
    private static final String TAG = "[StateMonitor]";
    private static StateMonitor mInstance = null;
    private long mTimerInterval = 1000;   // 1秒
    private int mMaxErrorCount = 5;       // MediaMuxer模块 5秒未收到数据
    private static final byte[] SYNC_FLAG = new byte[1];
    private Timer mTimer = null;
    private TimerTask mTimerTask = null;
    private MediaFilterContext mediaFilterContext = null;
    private static final int videoIndex = 0;
    private static final int audioIndex = 1;
    private int curErrorCount = 0;
    private long mInputCount[]          = new long[6];
    private long mDecodeCount[]         = new long[6];
    private long mGPUCount[]            = new long[6];
    private long mEncodeCount[]         = new long[6];
    private long mFormatAdapterCount[]  = new long[6];
    private long mMeidaMuxerCount[]     = new long[6];
    private boolean started = false;

    private boolean videoInputEnd = false;
    private boolean videoDecoderEnd =false;
    private boolean videoGPUEnd =false;
    private boolean videoFormatAdapterEnd =false;
    private boolean videoMeidaMuxerEnd =false;

    private boolean audioInputEnd = false;
    private boolean audioDecoderEnd =false;
    private boolean audioGPUEnd =false;
    private boolean audioFormatAdapterEnd =false;
    private boolean audioMeidaMuxerEnd =false;

    public static int NO_ERROR = -1;
    public static int ERROR_VIDEO_INPUT = 0;
    public static int ERROR_VIDEO_DECODE = 2;
    public static int ERROR_VIDEO_GPU = 4;
    public static int ERROR_VIDEO_FormatAdapter = 8;
    public static int ERROR_VIDEO_Muxer = 16;
    public static int ERROR_AUDIO_Muxer = 32;

    private int CurErrorType = NO_ERROR;


    public StateMonitor() {
        curErrorCount = 0;
        YYLog.info(TAG, "construct.");
    }

    public void NotifyInput(int type, long pts) {
        if (!started) {
            return;
        }
        int index = type == MEDIA_TYPE_VIDEO ? videoIndex : audioIndex;
        mInputCount[index]++;   // cur second
        mInputCount[index+2]++; // total count
        mInputCount[index+4] = pts; // cur pts
    }

    public void NotifyInputEnd(int type) {
        YYLog.info(TAG, "input end, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
        if (type == MEDIA_TYPE_AUDIO) {
            audioInputEnd = true;
        } else if (type == MEDIA_TYPE_VIDEO) {
            videoInputEnd = true;
        }
    }

    public void NotifyInputStart(int type) {
        YYLog.info(TAG, "input start, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
    }

    public void NotifyDecoder(int type, long pts) {
        if (!started) {
            return;
        }
        int index = type == MEDIA_TYPE_VIDEO ? videoIndex : audioIndex;
        mDecodeCount[index]++;
        mDecodeCount[index+2]++;
        mDecodeCount[index+4] = pts;
    }

    public void NotifyDecoderEnd(int type) {
        YYLog.info(TAG, "Decoder end, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
        if (type == MEDIA_TYPE_AUDIO) {
            audioDecoderEnd = true;
        } else if (type == MEDIA_TYPE_VIDEO) {
            videoDecoderEnd = true;
        }
    }

    public void NotifyDecoderStart(int type) {
        mDecodeCount[audioIndex + 2] = 0;
        mDecodeCount[videoIndex + 2] = 0;
        YYLog.info(TAG, "Decoder start, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
    }

    public void NotifyGPU(int type, long pts) {
        if (!started) {
            return;
        }
        int index = type == MEDIA_TYPE_VIDEO ? videoIndex : audioIndex;
        mGPUCount[index]++;
        mGPUCount[index+2]++;
        mGPUCount[index+4] = pts;
    }

    public void NotifyGPUEnd(int type) {
       YYLog.info(TAG, "GPU end, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
       if (type == MEDIA_TYPE_VIDEO) {
           videoGPUEnd = true;
       }
    }

    public void NotifyGPUStart(int type) {
        mGPUCount[audioIndex + 2] = 0;
        mGPUCount[videoIndex + 2] = 0;
        YYLog.info(TAG, "GPU start, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
    }


    public void NotifyEncode(int type, long pts) {
        if (!started) {
            return;
        }
        int index = type == MEDIA_TYPE_VIDEO ? videoIndex : audioIndex;
        mEncodeCount[index]++;
        mEncodeCount[index+2]++;
        mEncodeCount[index+4] = pts;
    }

    public void NotifyEncodeEnd(int type) {
        YYLog.info(TAG, "Encode end, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
    }
    public void NotifyEncodeStart(int type) {
        mEncodeCount[audioIndex + 2] = 0;
        mEncodeCount[videoIndex + 2] = 0;
        YYLog.info(TAG, "Encode start, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
    }

    public void NotifyFormatAdapter(int type, long pts) {
        if (!started) {
            return;
        }
        int index = type == MEDIA_TYPE_VIDEO ? videoIndex : audioIndex;
        mFormatAdapterCount[index]++;
        mFormatAdapterCount[index+2]++;
        mFormatAdapterCount[index+4] = pts;
    }

    public void NotifyFormatAdapterEnd(int type) {
        YYLog.info(TAG, "FormatAdapter end, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
    }

    public void NotifyFormatAdapterStart(int type) {
        YYLog.info(TAG, "FormatAdapter start, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
    }

    public void NotifyMeidaMuxer(int type, long pts) {
        if (!started) {
            return;
        }
        int index = type == MEDIA_TYPE_VIDEO ? videoIndex : audioIndex;
        mMeidaMuxerCount[index]++;
        mMeidaMuxerCount[index+2]++;
        mMeidaMuxerCount[index+4] = pts;
    }

    public void NotifyMediaMuxerEnd(int type) {
        YYLog.info(TAG, "MediaMuxer end, type " + (type == MEDIA_TYPE_VIDEO?"video":"audio"));
        if (type == MEDIA_TYPE_AUDIO) {
            audioMeidaMuxerEnd = true;
        } else if (type == MEDIA_TYPE_VIDEO) {
            videoMeidaMuxerEnd = true;
        }
    }

    public void NotifyMediaMuxerStart(int type) {
        mMeidaMuxerCount[audioIndex + 2] = 0;
        mMeidaMuxerCount[videoIndex + 2] = 0;
        YYLog.info(TAG, "MediaMuxer start, type " + (type == MEDIA_TYPE_VIDEO ? "video" : "audio"));
    }

    public void resetCurTime() {
        int index = videoIndex;
        mInputCount[index] = 0L;
        mDecodeCount[index] = 0L;
        mGPUCount[index] = 0L;
        mEncodeCount[index] = 0L;
        mFormatAdapterCount[index] = 0L;
        mMeidaMuxerCount[index] = 0L;

        index = audioIndex;
        mInputCount[index] = 0L;
        mDecodeCount[index] = 0L;
        mGPUCount[index] = 0L;
        mEncodeCount[index] = 0L;
        mFormatAdapterCount[index] = 0L;
        mMeidaMuxerCount[index] = 0L;
    }

    public void setMediaFilterContext(MediaFilterContext filterContext) {
        mediaFilterContext = filterContext;
        curErrorCount = 0;
        videoInputEnd = false;
        videoDecoderEnd = false;
        videoMeidaMuxerEnd = false;
        audioMeidaMuxerEnd = false;
        CurErrorType = NO_ERROR;
    }

    public void setErrorTimeOut(int timemout) {
        mMaxErrorCount = timemout;
        curErrorCount = 0;
    }

    public void NotifyErrorIfNeed() {

        int error = NO_ERROR;
        String eStr = "";
        if(!videoInputEnd && mInputCount[videoIndex] <= 0) {
            error = ERROR_VIDEO_INPUT;
            eStr = "video export failed.";
        }

        if(!videoDecoderEnd && mDecodeCount[videoIndex] <= 0) {
            error = ERROR_VIDEO_DECODE;
            eStr = "video export failed.";
    }

        if(!videoMeidaMuxerEnd && mMeidaMuxerCount[videoIndex] <= 0) {
            error = ERROR_VIDEO_Muxer;
            eStr = "video export failed.";
        }

        if(!audioMeidaMuxerEnd && mMeidaMuxerCount[audioIndex] <= 0) {
            if (mediaFilterContext.getRecordConfig().getEnableAudioRecord()) {
                error = ERROR_AUDIO_Muxer;
                eStr = "video export failed.";
            }
        }

        if(error != NO_ERROR) {
            curErrorCount++;
            if (curErrorCount <= mMaxErrorCount || GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 0) { // 本地导入超长视频，非内存模式
                YYLog.info(TAG,"curErrorCount " + curErrorCount + " error " + error);
                return;
            }
            YYMediaFilterListener listener = mFilterListener.get();
            if(listener != null) {
                YYLog.error(TAG, "Error : " + error + " " + eStr);
                if (CurErrorType != error) {
                    listener.onFilterError(this, eStr);
                    CurErrorType = error;
                    curErrorCount = 0;
                }
            }
        }
    }


    public void printFrameRateVideo() {
        YYLog.info(TAG,"Input[video]          [" + mInputCount[videoIndex] +         "]  total " + mInputCount[videoIndex+2] + " pts " + mInputCount[videoIndex+4]);
        YYLog.info(TAG,"Decoder[video]        [" + mDecodeCount[videoIndex] +        "]  total " + mDecodeCount[videoIndex+2] +" pts " + mDecodeCount[videoIndex+4]);
        YYLog.info(TAG,"GPU[video]            [" + mGPUCount[videoIndex] +           "]  total " + mGPUCount[videoIndex+2] +" pts "    + mGPUCount[videoIndex+4]);
        YYLog.info(TAG,"Encode[video]         [" + mEncodeCount[videoIndex] +        "]  total " + mEncodeCount[videoIndex+2] +" pts " + mEncodeCount[videoIndex+4]);
        YYLog.info(TAG,"FormatAdapter[video]  [" + mFormatAdapterCount[videoIndex] + "]  total " + mFormatAdapterCount[videoIndex+2] +" pts "+mFormatAdapterCount[videoIndex+4]);
        YYLog.info(TAG,"MeidaMuxer[video]     [" + mMeidaMuxerCount[videoIndex] +    "]  total " + mMeidaMuxerCount[videoIndex+2] +" pts " +mMeidaMuxerCount[videoIndex+4]);
    }

    public void printFrameRateAudio() {
        YYLog.info(TAG,"MeidaMuxer[audio]     [" + mMeidaMuxerCount[audioIndex] +    "]  total " + mMeidaMuxerCount[audioIndex+2] +" pts " +mMeidaMuxerCount[audioIndex+4]);
    }

    public void start() {
        resetCurTime();
        startTimer();
        started = true;
    }

    public void stop() {
        printFrameRateVideo();
        printFrameRateAudio();
        stopTimer();
    }

    private void startTimer() {
        if (mTimer == null) {
            YYLog.info(TAG,"[timer] startTimer.....");
            mTimer = new Timer();
            mTimerTask = new TimerTask() {
                public void run() {
                if (started) {
                    if (CurErrorType == NO_ERROR) {
                        printFrameRateVideo();
                        printFrameRateAudio();
                        NotifyErrorIfNeed();
                        resetCurTime();
                    }
                }
                }
            };
            mTimer.schedule(mTimerTask, 1000, mTimerInterval);
        }
    }

    public void stopTimer() {
        YYLog.info(TAG,  "[timer] stopTimer.....");
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        if (mTimerTask != null) {
            mTimerTask.cancel();
            mTimerTask = null;
        }
    }


    public static StateMonitor instance() {
        if (mInstance == null) {
            synchronized (SYNC_FLAG) {
                if (mInstance == null) {
                    mInstance = new StateMonitor();
                }
            }
        }
        return mInstance;
    }
}
