package com.ycloud.mediaprocess;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaFormat;
import android.os.Build;

import com.ycloud.VideoProcessTracer;
import com.ycloud.api.common.SampleType;
import com.ycloud.api.config.RecordContants;
import com.ycloud.api.process.IMediaListener;
import com.ycloud.api.process.MediaInfo;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.common.FileUtils;
import com.ycloud.common.GlobalConfig;
import com.ycloud.datamanager.AudioDataManager;
import com.ycloud.datamanager.VideoDataManager;
import com.ycloud.gpuimagefilter.filter.FFmpegFilterSessionWrapper;
import com.ycloud.gpuimagefilter.filter.FilterCenter;
import com.ycloud.gpuimagefilter.filter.MediaExportGpuFilterGroup;
import com.ycloud.mediacodec.MeidacodecConfig;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediafilters.AbstractInputFilter;
import com.ycloud.mediafilters.AbstractYYMediaFilter;
import com.ycloud.mediafilters.AudioFileMixer;
import com.ycloud.mediafilters.AudioFilterContext;
import com.ycloud.mediafilters.IMediaSession;
import com.ycloud.mediafilters.MP4InputFilter;
import com.ycloud.mediafilters.MediaFilterContext;
import com.ycloud.mediafilters.MediaBufferQueue;
import com.ycloud.mediafilters.MediaFormatAdapterFilter;
import com.ycloud.mediafilters.MediaMuxerFilter;
import com.ycloud.mediafilters.MemInputFilter;
import com.ycloud.mediafilters.RawMp4Dumper;
import com.ycloud.mediafilters.TimeEffectFilter;
import com.ycloud.mediafilters.VideoDecoderGroupFilter;
import com.ycloud.mediafilters.VideoEncoderGroupFilter;
import com.ycloud.mediafilters.VideoEndPointFilter;
import com.ycloud.mediafilters.YYMediaFilterListener;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.DeviceUtil;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Administrator on 2018/1/2.
 */

public class MediaExportSession implements IMediaSession, YYMediaFilterListener
{
    private String TAG = MediaExportSession.class.getSimpleName();
    protected Context mContext = null;

    //private FilterSession mFilterSession = null;
    private FFmpegFilterSessionWrapper mExportGpuFilterSessionWrapper = null;
    private MediaExportGpuFilterGroup mExportGpuFilterGroup = null;

    VideoEncoderGroupFilter mVideoEncodeFilter = null;
    VideoDecoderGroupFilter mVideoDecoderFilter = null;
    VideoEndPointFilter mVideoEndPointFilter = null;

    MediaFilterContext mVideoFilterContext = null;
    AudioFilterContext mAudioFilterContext = null;

    MediaMuxerFilter mMediaMuxerFilter = null;
    AbstractInputFilter mInputFilter = null;

    MediaBufferQueue<YYMediaSample> mDecoderVideoBufferQueue = null;

    AudioFileMixer mAudioFileMixer = null;
    MediaFormatAdapterFilter mMediaFormatAdapterFilter = null;
    TimeEffectFilter mTimeEffectFilter = null;
    protected RecordConfig mRecordConfig;

    private int mFilterErrorCnt = 0; //error count from filter error notify
    private long mVideoDurationUs = 0;
    private int mMuxerProcessFrameCnt = 0;
    private String mMp4VideoPath;

    private AtomicBoolean mIsRecord      = new AtomicBoolean(false);
    private AtomicBoolean   mRelease        = new AtomicBoolean(false);

    //释放MediaMuxFilter的标识符，只有gl线程和audio线程都推出后才能释放
    private AtomicBoolean   mReleaseMuxFilter = new AtomicBoolean(false);

    //private NewVideoRecordSession.EventHandler mEventHandler;
    //private Object mRecordLock = new Object();
    protected AtomicReference<IMediaListener> mMediaListener = new AtomicReference<>(null);
    boolean mShouldDumpRawMp4File = false;
    private long startTime = 0;

    public MediaExportSession(Context context, String mp4File, String targeFileName) {

        YYLog.info(this, "MediaExportSession begin, mp4file="+mp4File + " targeFile: " +targeFileName);
        FilterCenter.getInstance();
        //初始化本次record session的config参数
        mRecordConfig = new RecordConfig();
        mContext = context;
        mTimeEffectFilter = new TimeEffectFilter();
        mAudioFilterContext = new AudioFilterContext();
        mVideoFilterContext = new MediaFilterContext(context);

        mVideoFilterContext.getMediaStats().setBeginTimeStamp(System.currentTimeMillis());

        mVideoFilterContext.setRecordConfig(mRecordConfig);
        mAudioFilterContext.setRecordConfig(mRecordConfig);

        mRecordConfig.setOutputPath(targeFileName);
        //profile

        YYLog.info(TAG,"GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY " + GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY);

        String cacheDir = FileUtils.getDiskCacheDir(context) + File.separator;
        mShouldDumpRawMp4File = new File("/sdcard/dumpsodamp4.txt").exists();

        mAudioFileMixer = new AudioFileMixer(cacheDir, mAudioFilterContext);
        mMp4VideoPath = mp4File;
        mAudioFileMixer.enableDumpRawMp4(mShouldDumpRawMp4File);

        if(GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY != 1) {
            mInputFilter = new MP4InputFilter(mp4File, mVideoFilterContext);
        } else {
            YYLog.info(TAG, "use Mem Input filter ...");
            mInputFilter = new MemInputFilter(mVideoFilterContext);
        }
        mInputFilter.setMediaSession(this);

        mExportGpuFilterSessionWrapper = new FFmpegFilterSessionWrapper();
        mExportGpuFilterGroup = new MediaExportGpuFilterGroup(context,mExportGpuFilterSessionWrapper.getSessionID(),
                                                                mVideoFilterContext.getGLManager().getLooper(),
                                                                mVideoFilterContext.getMediaStats());
        mVideoEncodeFilter = new VideoEncoderGroupFilter(mVideoFilterContext, false);
        mVideoEndPointFilter = new VideoEndPointFilter(mVideoFilterContext);

        mVideoDecoderFilter = new VideoDecoderGroupFilter(mVideoFilterContext, this);

        mMediaMuxerFilter  = new MediaMuxerFilter(mVideoFilterContext, false);
        mMediaMuxerFilter.setVideoAudioSync(false);
        mMediaMuxerFilter.setSingleStreamOfEndMode(false);
        mMediaMuxerFilter.init();

        mMediaFormatAdapterFilter = new MediaFormatAdapterFilter(mVideoFilterContext);
        if (mAudioFileMixer != null) {
            mAudioFileMixer.setMediaMuxer(mMediaMuxerFilter);
        }
        mMediaFormatAdapterFilter.setNAL3ValidNAL4(false);

        mVideoFilterContext.getGLManager().registerFilter(mExportGpuFilterGroup);
        mVideoFilterContext.getGLManager().registerFilter(mVideoEncodeFilter);
        mVideoFilterContext.getGLManager().registerFilter(mVideoEndPointFilter);
        mVideoFilterContext.getGLManager().registerFilter(mMediaMuxerFilter);

        //link the input filter and decode filter with media buffer queue.
        mDecoderVideoBufferQueue = new MediaBufferQueue(3, 16, SampleType.VIDEO);
        mInputFilter.setVideoOutputQueue(mDecoderVideoBufferQueue);
        mVideoDecoderFilter.setInputBufferQueue(mDecoderVideoBufferQueue);
        //mInputFilter.addDownStream(mVideoDecoderFilter);
        mVideoDecoderFilter.getOutputFilter().addDownStream(mExportGpuFilterGroup);
        mExportGpuFilterGroup.setFilterGroupOutPath(mVideoEncodeFilter);  //group的输出节点.

        mVideoEncodeFilter.getOutputFilter().addDownStream(mMediaFormatAdapterFilter.addDownStream(mTimeEffectFilter.addDownStream(mMediaMuxerFilter)));

        //TODO[lsh] add audio decoder.
        //mInputFilter.addDownStream(mAudioProcessFilter.addDownStream(mAudioEncoderFilter.addDownStream(mMediaMuxerFilter)));
        mVideoFilterContext.getGLManager().setMediaSession(this);
        mAudioFilterContext.getAudioManager().setMediaSession(this);
        MeidacodecConfig.loadConfig(mContext);

        mInputFilter.setFilterListener(this);
        mMediaMuxerFilter.setFilterListener(this);
        mVideoDecoderFilter.setFilterListener(this);
        StateMonitor.instance().setFilterListener(this);
        StateMonitor.instance().setMediaFilterContext(mVideoFilterContext);
        StateMonitor.instance().setErrorTimeOut(10);    // mMediaMuxerFilter 10 秒未收到数据，超时报错

        YYLog.info(this, "[tracer] MediaExportSession end 2.8.2feature.====swdecoder===, phone model:"+ DeviceUtil.getPhoneModel());
    }

    public void startExport() {
        if (mRelease.get()) {
            YYLog.info(this, "MediaExportSession is released");
            return;
        }

        StateMonitor.instance().start();
        startTime = System.currentTimeMillis();

        mTimeEffectFilter.init();
        mMediaFormatAdapterFilter.init();
        String videoPath = null;
        String cacheDir = FileUtils.getDiskCacheDir(mContext) + File.separator;
        double audioDuration = 0;
        boolean useVideoDuration = true;
        if (GlobalConfig.getInstance().getRecordConstant().STORE_DATA_IN_MEMORY == 1) {
            String path = cacheDir + "pureAudio.mp4";
            if (AudioDataManager.instance().exportAudioToMp4(path) != -1) {
                videoPath = path;
            }
            audioDuration = VideoDataManager.instance().getDuration();
            audioDuration = audioDuration / 1000000;
            useVideoDuration = false;
        } else {
            videoPath = mMp4VideoPath;
            useVideoDuration = true;
        }

        String pureAudioPath = null;
        if (videoPath != null) {
            MediaInfo mediaInfo = MediaUtils.getMediaInfo(videoPath);
            if (mediaInfo != null) {
                if (mediaInfo.audio_codec_name != null) {
                    pureAudioPath = cacheDir + "pureAudio.wav";
                    AudioProcessInternal audioProcessInternal = new AudioProcessInternal();
                    //extract audio from recorded video
                    boolean ret = audioProcessInternal.extractAudioTrack(videoPath, pureAudioPath);
                    if (!ret) {
                        pureAudioPath = null;
                    }
                }
                if (useVideoDuration) {
                    audioDuration = mediaInfo.video_duration;
                }else {
                    audioDuration = mediaInfo.audio_duration;
                }
            }
        }
        mAudioFileMixer.setDuration(audioDuration);
        mAudioFileMixer.setPureAudioPath(pureAudioPath);

        YYLog.info(this, "MediaExportSession.startExport");
        mAudioFilterContext.getAudioManager().post(new Runnable() {
            @Override
            public void run() {
                if (mAudioFileMixer != null) {
                    mAudioFileMixer.mix();
                }
            }
        });

        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                OpenGlUtils.checkGlError("MediaExportSession.startExport begin");
                mMediaMuxerFilter.init();
                mVideoEncodeFilter.init();
                mVideoEncodeFilter.startEncode(mVideoFilterContext.getVideoEncoderConfig());
                OpenGlUtils.checkGlError("MediaExportSession.startExport end");
            }
        });

        mInputFilter.start();
    }

    public void stopExport() {
        //首先把所处理流程中的各个节点，全部都不处理.
        mInputFilter.stop();  //
        mVideoDecoderFilter.stopDecode(); //InputFilter, stop掉输入源后， videoDecoderFilter中不会有任何其他线程的输入信号输入了.
        mExportGpuFilterGroup.stop();  //set a stop flag

        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                OpenGlUtils.checkGlError("MediaExportSession.startExport begin");
                mVideoEncodeFilter.stopEncode();  //stop the thread, stopEncode should thread safe with processMediaSample.
                OpenGlUtils.checkGlError("MediaExportSession.startExport end");
            }
        });
    }

    public void cancel() {
        //TODO...
        release();
    }

    public void setMediaListener(IMediaListener listener) {
        mMediaListener = new AtomicReference<>(listener);
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener listener) {
        if (mExportGpuFilterGroup != null) {
            mExportGpuFilterGroup.setMediaInfoRequireListener(listener);
        }
    }

    public void setVideoEncodeConfig(final VideoEncoderConfig config) {

        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                //sdk内部的适配参数.
                if(config.encodeParameterEmpty()) {
                    //TODO [lsh] 服务器下发转码硬编码参数.
                    //config.setEncodeParam(RecordDynamicParam.getInstance().getHardEncodeParameters());
                }
                mVideoFilterContext.setVideoEncodeConfig(config);
                YYLog.info(this, "setEncoderConfig:"+config.toString());

                updateVideoMetaData(config);
            }
        });
    }

    //通过encodeConfig的设置更新meta data值
    private void updateVideoMetaData(VideoEncoderConfig config) {
        VideoProcessTracer.getInstace().setPreset("yyveryfast");
        VideoProcessTracer.getInstace().setFrameRate(config.mFrameRate);
        VideoProcessTracer.getInstace().setCrf((int) config.mQuality);
        VideoProcessTracer.getInstace().setResolution(config.getEncodeWidth() + "x" + config.getEncodeHeight());

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        Date curDate = new Date(System.currentTimeMillis());
        String timeStr = formatter.format(curDate);
        VideoProcessTracer.getInstace().setExportTime(timeStr);
    }

    public void setVideoEncodeHighQuality() {
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                mVideoFilterContext.getVideoEncoderConfig().setBitRate(GlobalConfig.getInstance().getRecordConstant().EXPORT_HQ_BITRATE);
                mVideoFilterContext.getVideoEncoderConfig().setHighQuality(true);
            }
        });
    }

    public void setMaxExportBitrate(final int bitrate) {
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                mVideoFilterContext.getVideoEncoderConfig().setBitRate(bitrate);
            }
        });
    }

    public void setVideoQuality(final float quality) {
        mVideoFilterContext.getGLManager().post(new Runnable() {
            @Override
            public void run() {
                mVideoFilterContext.getVideoEncoderConfig().setQuality(quality);
            }
        });
    }

    public void setBgmMusicPath(String bgmMusicPath, float volume) {
        if (mAudioFileMixer != null) {
            mAudioFileMixer.setBgmMusicPath(bgmMusicPath, volume);
        }
    }

    public void setBgmMusicRhythmInfo(String path, int start) {
        mExportGpuFilterGroup.setRhythmInfo(path, start);
    }

    public void setMagicAudioPath(String magicAudioPath) {
        if (mAudioFileMixer != null) {
            mAudioFileMixer.setMagicAudioPath(magicAudioPath);
        }
    }

    public void setVideoVolume(float videoVolume) {
        if (mAudioFileMixer != null) {
            mAudioFileMixer.setVideoVolume(videoVolume);
        }
    }

    @Override
    public void glMgrCleanup() {
        if (mRelease.get()) {
            mVideoEncodeFilter = null;
            mVideoEndPointFilter = null;
            mVideoFilterContext = null;
            mVideoDecoderFilter = null;
            mInputFilter = null;

            if(mReleaseMuxFilter.get()) {
                YYLog.info(TAG, "glMgrCleanup set MediaMuxFilter null");
                mMediaMuxerFilter = null;
                mReleaseMuxFilter.set(false);
            } else {
                mReleaseMuxFilter.set(true);
            }

            mContext = null;
            YYLog.info(TAG, "MediaExportSession glMgrCleanup");
        }
    }

    @Override
    public void audioMgrCleanup() {
        if(mRelease.get()) {
            //mAudioProcessFilter = null;
            //mAudioEncoderFilter = null;

            if(mReleaseMuxFilter.get()) {
                YYLog.info(TAG, "audioMgrCleanup set MediaMuxFilter null");
                mMediaMuxerFilter = null;
                mReleaseMuxFilter.set(false);
            } else {
                mReleaseMuxFilter.set(true);
            }

            YYLog.info(TAG, "MediaExportSession audioMgrCleanup");
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void setInputVideoFormat(final MediaFormat mediaFromat) {
        if(mediaFromat != null && mediaFromat.containsKey(MediaFormat.KEY_DURATION)) {
            mVideoDurationUs = mediaFromat.getLong(MediaFormat.KEY_DURATION);
        }

        if(mediaFromat != null && mExportGpuFilterGroup != null) {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    OpenGlUtils.checkGlError("MediaExportSession.setInputVideoFormat");
                    mExportGpuFilterGroup.init(mediaFromat);
                    mExportGpuFilterGroup.startListen();
                }
            });
        }
    }

    @Override
    public void setInputAudioFormat(MediaFormat mediaFormat) {

    }

    public void release() {
        //release all the resource.
        if(mRelease.getAndSet(true)) {
            YYLog.info(this, "[tracer] release already!!");
            return;
        }
        StateMonitor.instance().stop();

        YYLog.info(this, "[tracer] export release begin");
        stopExport();  //确保了mediamuxer不会再收到数据, Video stopEncoder会等待 encoder线程的stop

        if(mVideoFilterContext != null) {
            mVideoFilterContext.getGLManager().post(new Runnable() {
                @Override
                public void run() {
                    mExportGpuFilterGroup.destroy();
                    FilterCenter.getInstance().removeFilterObserver(mExportGpuFilterGroup, mExportGpuFilterSessionWrapper.getSessionID());
                    mExportGpuFilterGroup = null;
                    mExportGpuFilterSessionWrapper = null;
                }
            });

            mVideoFilterContext.getGLManager().quit();
            mVideoFilterContext = null;
        }

        mAudioFilterContext.getAudioManager().quit();
        mAudioFilterContext = null;

        mMediaMuxerFilter.deInit();

        mRecordConfig.setRecordListener(null);
        mRecordConfig.setAudioRecordListener(null);
        mRecordConfig = null;
        YYLog.info(this, "[tracer] MediaExportSession release end !!");

        MeidacodecConfig.unLoadConfig();

    }

    public void restartVideoStream() {
        if(mInputFilter != null) {
            mInputFilter.videoSeekTo(0);
        }
    }

    public FFmpegFilterSessionWrapper getmExportGpuFilterSessionWrapper() {
        return mExportGpuFilterSessionWrapper;
    }

    //thread-safe
    @Override
    public void onFilterInit(AbstractYYMediaFilter filter) {
        //donothing..
    }

    //thread-safe
    @Override
    public void onFilterDeInit(AbstractYYMediaFilter filter) {
        //donoghing.
    }

    //thread-safe
    @Override
    public void onFilterEndOfStream(AbstractYYMediaFilter filter) {
        if(filter instanceof MediaMuxerFilter) {
            mVideoFilterContext.getMediaStats().setmEndTimeStamp(System.currentTimeMillis());
            mVideoFilterContext.getMediaStats().dump();
            YYLog.info(this, "MediaExportSession finished!!! Cost Time : " + (System.currentTimeMillis()-startTime));

            IMediaListener listener = mMediaListener.get();

            if (mShouldDumpRawMp4File) {
                RawMp4Dumper musicFileFilter = new RawMp4Dumper();
                String rawMp4Path = "/sdcard/raw.mp4";
                musicFileFilter.exportAVFromMemToMp4(rawMp4Path);
                FileUtils.copyFile(mRecordConfig.getRecordFilePath(), "/sdcard/soda.mp4");
                //FileUtils.copyFile(rawMp4Path, mRecordConfig.getRecordFilePath());
            }
            if(listener != null) {
                listener.onEnd();
            }
        }
    }

    //thread-safe
    @Override
    public void onFilterProcessMediaSample(AbstractYYMediaFilter filter, SampleType sampleType, long ptsMs) {
        if(mVideoDurationUs == 0 || sampleType != SampleType.VIDEO)
            return;

        if(filter instanceof MediaMuxerFilter) {
            //notify every 30 video frame..
            if(mMuxerProcessFrameCnt++ % 30 != 0) {
                return;
            }
            float percent = ((float)ptsMs*1000*100)/mVideoDurationUs/90;
            percent = (percent>=1.0)? 1.0f : percent;
            YYLog.info(this, "========================percent:"+percent);
            IMediaListener listener = mMediaListener.get();
            if(listener != null) {
                listener.onProgress(percent);
            }
        }
    }

    //thread-safe
    @Override
    public void onFilterError(AbstractYYMediaFilter filter, final String errMsg) {
        YYLog.info(this, "onFilterError:"+errMsg);
        final IMediaListener listener = mMediaListener.get();
        if(listener != null) {
            try {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        listener.onError(-1, errMsg);
                    }
                });

            } catch (Exception e) {
                YYLog.error(TAG, "Exception: " + e.getMessage() );
            }
        }
    }
}
