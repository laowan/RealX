package com.ycloud.gpuimagefilter.filter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.orangefilter.OrangeFilter;
import com.venus.Venus;
import com.ycloud.api.common.SDKCommonCfg;
import com.ycloud.api.videorecord.IMediaInfoRequireListener;
import com.ycloud.common.OFLoader;
import com.ycloud.facedetection.STMobileFaceDetectionWrapper;
import com.ycloud.facedetection.VenusGestureDetectWrapper;
import com.ycloud.facedetection.VenusSegmentWrapper;
import com.ycloud.gpuimage.adapter.GlTextureImageReader;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.utils.BodiesDetectInfo;
import com.ycloud.gpuimagefilter.utils.FaceDetectWrapper;
import com.ycloud.gpuimagefilter.utils.FacesDetectInfo;
import com.ycloud.gpuimagefilter.utils.FilterClsInfo;
import com.ycloud.gpuimagefilter.utils.FilterConfig;
import com.ycloud.gpuimagefilter.utils.FilterConfigParse;
import com.ycloud.gpuimagefilter.utils.FilterDataStore;
import com.ycloud.gpuimagefilter.utils.FilterInfo;
import com.ycloud.gpuimagefilter.utils.FilterLayout;
import com.ycloud.gpuimagefilter.utils.HumanBodyDetectWrapper;
import com.ycloud.gpuimagefilter.utils.RhythmInfo;
import com.ycloud.gpuimagefilter.utils.SegmentCacheDetectWrapper;
import com.ycloud.mediafilters.AbstractYYMediaFilter;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Kele on 2017/7/27.
 */
public class FilterGroup extends AbstractYYMediaFilter implements FilterCenter.FilterObserverInterface {
    protected static final Integer kFilterStoreID = Integer.valueOf(0);
    protected FilterDataStore<Integer, BaseFilter> mFilterStore = new FilterDataStore<>();
    protected int mSessionID = -1;
    protected AtomicBoolean mStartListen = new AtomicBoolean(false);
    protected long mFilterCenterSnapshotVer = -1;
    protected Looper mLooper = null;
    protected Handler mFilterHandler = null;
    protected FilterLayout mLayout = new FilterLayout();

    protected AtomicReference<String> m_mp4Name = new AtomicReference<String>("null");
    protected AtomicReference<String> m_jsonName = new AtomicReference<String>("null");

    protected int mOFContext = -1;
    protected boolean mInited = false;

    protected VideoFilterSelector mVideoFilterSelector = new VideoFilterSelector(mFilterStore);
    protected  boolean mUseFilterSelector = true;

    protected RhythmInfo mRhythmInfo = new RhythmInfo();
    protected String mRhythmConfPath = null;
    protected int mRhythmStart = 0;

    //是否需要人脸检测数据
    protected boolean mNeedCheckFace = false;
    //是否需要手势检测数据
    protected boolean mNeedCheckGesture = false;
    //是否需要肢体检测数据
    protected boolean mNeedCheckBody = false;
    //是否需要附加video数据
    protected boolean mNeedPlayerVideoData = false;
    //是否需要抠图数据
    protected boolean mNeedSegment = false;
    //是否需要avatar效果的标识，avatar效果指人物做动作会有动物一样做相应的模拟
    protected boolean mNeedAvatar = false;

    //抠图工具类初始化标识
    protected boolean mSegmentInited = false;

    protected VenusGestureDetectWrapper mGestureDetectWrapper = null;

    protected VenusSegmentWrapper mVenusSegmentWrapper = null;

    public HumanBodyDetectWrapper mHumanBodyDetectWrapper = new HumanBodyDetectWrapper();

    public SegmentCacheDetectWrapper mSegmentCacheDetectWrapper = new SegmentCacheDetectWrapper();

    public FaceDetectWrapper mFaceDetectWrapper = new FaceDetectWrapper();

    protected IMediaInfoRequireListener mMediaInfoRequireListener = null;

    protected int mFilterGroupAvatarId = -1;

    //创建filterGroup中共用的outputTexture及fbo资源，在宽高固定的情况下，多数filter没有必要在内部创建独立的texture和fbo资源
    protected int TEXTURE_NUM = 1;
    protected int[] mTextures;
    protected int[] mOriginTextures;
    final protected int FRAMEBUFFER_NUM = 2;
    protected int[] mFrameBuffer;
    protected int[] mFrameBufferTexture;

    private GlTextureImageReader mGlImageReader = null;

    protected void init() {
        mOFContext = OFLoader.createOrangeFilterContext();
        OrangeFilter.setConfigBool(mOFContext, OrangeFilter.OF_ConfigKey_IsMirror, false);

        mHumanBodyDetectWrapper.bodiesDetectInfoList = FilterCenter.getInstance().getBodyDetectInfo();

        mFaceDetectWrapper.facesDetectInfoList = FilterCenter.getInstance().getFaceDetectInfo();

        mSegmentCacheDetectWrapper.segmentCacheDataList = FilterCenter.getInstance().getSegmentCacheInfo();

        mTextures = new int[TEXTURE_NUM];
        //保存mTextures的初始值
        mOriginTextures = new int[TEXTURE_NUM];
        for (int i = 0; i < TEXTURE_NUM; i++) {
            mTextures[i] = OpenGlUtils.createTexture(mOutputWidth, mOutputHeight);
            mOriginTextures[i] = mTextures[i];
        }
        mFrameBuffer = new int[FRAMEBUFFER_NUM];
        mFrameBufferTexture = new int[FRAMEBUFFER_NUM];
        OpenGlUtils.createFrameBuffer(mOutputWidth, mOutputHeight, mFrameBuffer, mFrameBufferTexture, FRAMEBUFFER_NUM);

        YYLog.info(TAG, "init mOFContext = " + mOFContext);
    }

    protected void destroy() {
        mFilterHandler.removeCallbacksAndMessages(null);

        FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilerInfoBySessionID(kFilterStoreID);
        OpenGlUtils.checkGlError("destroy start");
        if (res.mFilterList != null) {
            for (int i = 0; i < res.mFilterList.size(); i++) {
                res.mFilterList.get(i).destroy();
            }
        }
        mFilterStore.removeAllFilter(kFilterStoreID);

        if (mTextures != null) {
            for (int i = 0; i < mTextures.length; i++) {
                OpenGlUtils.deleteTexture(mTextures[i]);
            }
            mTextures = null;
        }

        if (mFrameBufferTexture != null && mFrameBuffer != null) {
            OpenGlUtils.releaseFrameBuffer(FRAMEBUFFER_NUM, mFrameBufferTexture, mFrameBuffer);
            mFrameBufferTexture = null;
            mFrameBuffer = null;
        }

        if (mHumanBodyDetectWrapper != null) {
            mHumanBodyDetectWrapper.bodiesDetectInfoList = null;
            mHumanBodyDetectWrapper = null;
        }

        if (mRhythmInfo != null) {
            mRhythmInfo.rhythmInfoBeatList = null;
            mRhythmInfo.rhythmInfoPcmList = null;
            mRhythmInfo = null;
        }

        if (mSegmentInited && mVenusSegmentWrapper != null) {
            if (!SDKCommonCfg.getUseCpuSegmentMode()) {
                mVenusSegmentWrapper.deInit();
            } else {
                mVenusSegmentWrapper.deInitWithCpu();
            }

            mSegmentCacheDetectWrapper.segmentCacheDataList = null;
            mSegmentCacheDetectWrapper = null;
            mSegmentInited = false;
            mVenusSegmentWrapper = null;
        }

        mMediaInfoRequireListener = null;
    }

    protected void destroyOFContext() {
        YYLog.info(TAG, "destroyOFContext mOFContext = " + mOFContext);
        if (mOFContext != -1) {
            OFLoader.destroyOrangeFilterContext(mOFContext);
            mOFContext = -1;
        }
    }

    public FilterGroup(int sessionID, Looper looper) {
        mSessionID = sessionID;
        mLooper = looper;
    }

    public void startListen() {
        if (!mStartListen.getAndSet(true)) {
            FilterCenter.getInstance().addFilterObserver(this, mLooper, mSessionID);
            mFilterHandler = new Handler(mLooper, null) {
                @Override
                public void handleMessage(Message msg) {
                    //post into sync data task into looper. 这里直接从FilterCenter中取出filterInfo列表，然后添加到gpu FilterGroup中
                    FilterDataStore.OperResult<Integer, FilterInfo> res = FilterCenter.getInstance().getFilterSnapshot(mSessionID);
                    doFilterBatchAdd(res.mFilterList);
                    mFilterCenterSnapshotVer = res.mSnapshotVer;
                }
            };
            mFilterHandler.sendEmptyMessage(100);
        }
    }

    public void setOutFileName(String mp4name, String jsonName) {
        m_mp4Name.set(new String(mp4name));
        m_jsonName.set(new String(jsonName));

        YYLog.info(TAG, "m_mp4Name=" + m_mp4Name + " m_jsonName=" + m_jsonName);
    }

    // 获取参数
    public ArrayList<BaseFilterParameter> getParameter(Integer filterID) {
        return null;
    }


    /*获取对应filterID的filter实例的信息，成功返回对应的配置信息(deep-copy), 否则返回null*/
    public FilterInfo getFilterInfo(Integer filterID) {
        return null;
    }

    /*获取对应filterType为type的所有filter实例的信息列表，成功返回对应的配置信息列表(deep-copy), 否则返回null*/
    public ArrayList<FilterInfo> getFilterInfoByType(int type) {
        ArrayList<FilterInfo> ret = null;

        return ret;
    }

    public String marshall() {
        //编译各个filter, 得到对应的json信息.
        FilterConfig config = new FilterConfig();
        config.setMP4Name(m_mp4Name.get());

        FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilerInfoBySessionID(kFilterStoreID);
        if (res.mFilterList != null) {
            ListIterator<BaseFilter> it = res.mFilterList.listIterator();
            while (it.hasNext()) {
                BaseFilter filter = it.next();
                if (filter.getFilterInfo() != null) {
                    config.addFilterInfo(filter.getFilterInfo());
                }
            }
        }
        String ret = config.marshall();
        YYLog.info(this, "FilterGroup.marshall: " + (ret == null ? "null" : ret));
        return ret;
    }

    public void saveFilterConfig() {
        final String json_cfg = marshall();
        if (json_cfg != null) {
            //TODO, need wait it finish??
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        FileOutputStream file = new FileOutputStream(m_jsonName.get());
                        try {
                            file.write(json_cfg.getBytes());
                            file.flush();
                            file.close();
                        } catch (IOException e) {
                            YYLog.error(this, "[exception] filter group write json:" + e.toString());
                            e.printStackTrace();
                        }
                    } catch (FileNotFoundException e) {
                        YYLog.error(this, "[exception] filter group write json:" + e.toString());
                        e.printStackTrace();
                    }

                    YYLog.info(this, "filter group write json end: " + m_jsonName.get());
                }
            }).start();
        }
    }

    protected BaseFilter createFilter(FilterInfo filterInfo) {
        if (!mInited) {
            YYLog.error(TAG, "createFilter failed, filterGroup not init");
            return null;
        }

        if (filterInfo == null) {
            return null;
        }
        FilterClsInfo clsInfo = FilterCenter.getInstance().getFilterClsInfo(filterInfo.mFilterType);
        if (clsInfo == null) {
            return null;
        }
        BaseFilter newFilter = null;
        try {
            newFilter = (BaseFilter) clsInfo.mFilterCls.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
            YYLog.error(this, "[exception] occur: " + e.toString());
            return null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            YYLog.error(this, "[exception] occur: " + e.toString());
            return null;
        }
        if (newFilter != null && mOFContext > 0) {
            newFilter.init(mOutputWidth, mOutputHeight, false, mOFContext);

            //将filterGroup中共用的outputTexture和FBO资源设置给filter
            newFilter.setOutputTextures(mTextures);
            newFilter.setCacheFBO(mFrameBuffer, mFrameBufferTexture);

            newFilter.setFilterInfo(filterInfo);

            //YYLog.info(this, "[filter] create filter: filter id " + filterInfo.mFilterID + " snapVer: " + mFilterCenterSnapshotVer);
        }
        return newFilter;
    }

    protected void afterFilterAdd(BaseFilter filter) {
        //init filter...
        YYLog.info(this, "FilterGroup.onFilterAdd: filterType:" + filter.getFilterInfo().mFilterType);
    }

    protected void afterFilterRemove(BaseFilter filter) {

    }

    protected void afterFilterModify(BaseFilter filter) {

    }

    public int addParameter(int filterID, BaseFilterParameter parameter) {
        BaseFilter filter = mFilterStore.unSafe_getFilter(filterID, kFilterStoreID);
        if (filter != null) {
            FilterInfo filterInfo = filter.getFilterInfo();
            filterInfo.addFilterParameter(parameter);
            filter.setFilterInfo(filterInfo);
            YYLog.info(TAG, "addParameter filterID=" + filterID + " filterType=" + filterInfo.mFilterType + "parameter:" + parameter.toString());
            return parameter.mParameterID;
        }
        return -1;
    }

    public int resetParameter(int filterID, BaseFilterParameter parameter) {
        BaseFilter filter = mFilterStore.unSafe_getFilter(filterID, kFilterStoreID);
        if (filter != null) {
            FilterInfo filterInfo = filter.getFilterInfo();
            filterInfo.resetFilterParameter(parameter);
            filter.setFilterInfo(filterInfo);
            YYLog.info(TAG, "resetParameter filterID=" + filterID + " filterType=" + filterInfo.mFilterType + "parameter:" + parameter.toString());
            return parameter.mParameterID;
        }
        return -1;
    }


    public boolean modifyParameter(Integer filterID, BaseFilterParameter parameter) {
        BaseFilter filter = mFilterStore.unSafe_getFilter(filterID, kFilterStoreID);
        if (filter != null) {
            FilterInfo filterInfo = filter.getFilterInfo();
            filterInfo.modifyFilterParameter(parameter.mParameterID, parameter);
            filter.setFilterInfo(filterInfo);
            return true;
        }
        return false;
    }


    public void performLayout() {
        FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilerInfoBySessionID(kFilterStoreID);
        mLayout.performLayout(res.mFilterList);
    }

    @Override
    public void onFilterAdd(FilterInfo filterInfo, long verID) {
        if (!checkMsgValid(verID))
            return;

        mFilterCenterSnapshotVer = verID;
        BaseFilter filter = createFilter(filterInfo);
        if (filter != null) {
            mFilterStore.addFilter(Integer.valueOf(filterInfo.mFilterID), filterInfo.mFilterType, filter, kFilterStoreID);
            afterFilterAdd(filter);

            if (mUseFilterSelector) {
                mVideoFilterSelector.addFilterID(filterInfo.mFilterID);
            }
        }
        performLayout();
    }

    protected boolean checkMsgValid(long verID) {
        return (mStartListen.get() && verID > mFilterCenterSnapshotVer);
    }

    @Override
    public void onFilterRemove(Integer filterID, long verID) {
        if (!checkMsgValid(verID))
            return;

        //YYLog.info(this, "[filter] onFilterRemove: filterId " + filterID + " verId" + verID);
        mFilterCenterSnapshotVer = verID;
        FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilter(filterID, kFilterStoreID);
        if (res.mFilter == null)
            return;

        //从filter selector中移除filter id
        if (mUseFilterSelector) {
            mVideoFilterSelector.removeFilterID(filterID);
        }

        mFilterStore.removeFilter(filterID, res.mFilter.getFilterInfo().mFilterType, kFilterStoreID);
        afterFilterRemove(res.mFilter);
        res.mFilter.destroy();

        performLayout();
    }

    @Override
    public void onFilterModify(FilterInfo filterInfo, long verID, boolean isDuplicate) {
        if (!checkMsgValid(verID))
            return;

        mFilterCenterSnapshotVer = verID;
        BaseFilter filter = mFilterStore.unSafe_getFilter(filterInfo.mFilterID, kFilterStoreID);
        if (filter != null) {
            if(isDuplicate) {
                FilterInfo srvFilterInfo = filter.getFilterInfo();
                srvFilterInfo.update(filterInfo);
                filter.setFilterInfo(srvFilterInfo);
            } else {
                filter.setFilterInfo(filterInfo);
            }
        }

        afterFilterModify(filter);
    }

    @Override
    public void onFilterBatchAdd(ArrayList<FilterInfo> filterInfos, long verID) {
        if (!checkMsgValid(verID))
            return;

        doFilterBatchAdd(filterInfos);
        mFilterCenterSnapshotVer = verID;
    }

    public void doFilterBatchAdd(ArrayList<FilterInfo> filterInfos) {

        if (filterInfos == null || filterInfos.isEmpty())
            return;

        ListIterator<FilterInfo> it = filterInfos.listIterator();
        while (it.hasNext()) {
            FilterInfo info = it.next();
            //当前的info是FilterCenter中直接取出来的，如果用它作为CreateFilter的参数，会造成gpu FilterGroup线程与FilterSession线程用同一个info对象，这里duplicate来解决这个问题，add by jyq
            BaseFilter filter = createFilter(info.duplicate());
            if (filter != null) {
                mFilterStore.addFilter(Integer.valueOf(info.mFilterID), info.mFilterType, filter, kFilterStoreID);
                afterFilterAdd(filter);

                if(mUseFilterSelector) {
                    mVideoFilterSelector.addFilterID(info.mFilterID);
                }
            }
        }

        performLayout();
    }

    @Override
    public void onFilterBatchRemove(ArrayList<Integer> filterIDs, long verID) {
        if (!checkMsgValid(verID))
            return;

        mFilterCenterSnapshotVer = verID;
        if (filterIDs == null || filterIDs.isEmpty())
            return;

        ListIterator<Integer> it = filterIDs.listIterator();
        while (it.hasNext()) {
            Integer filterID = it.next();

            //YYLog.info(this, "[filter] onFilterRemoveBatch: filterId " + filterID + " verId" + verID);

            //从filter selector中批量移除filter id
            if (mUseFilterSelector) {
                mVideoFilterSelector.removeFilterID(filterID);
            }

            FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilter(filterID, kFilterStoreID);
            if (res.mFilter == null)
                continue;

            mFilterStore.removeFilter(filterID, res.mFilter.getFilterInfo().mFilterType, kFilterStoreID);
            res.mFilter.destroy();
            afterFilterRemove(res.mFilter);
        }
        performLayout();
    }

    @Override
    public void onFilterBatchModify(ArrayList<FilterInfo> filterInfos, long verID) {
        if (!checkMsgValid(verID))
            return;

        mFilterCenterSnapshotVer = verID;
    }

    @Override
    public void onCacheClear() {
        YYLog.info(TAG, "of clearCachedResource:" + mOFContext);
        OrangeFilter.clearCachedResource(mOFContext);
    }

    public void setRhythmInfo(String rhythmFilePath, int start) {
        if (rhythmFilePath == null) {
            mRhythmInfo = null;
        } else if (!rhythmFilePath.equals(mRhythmConfPath)) {
            mRhythmInfo = FilterConfigParse.parseRhythmConf(rhythmFilePath);
        }

        mRhythmConfPath = rhythmFilePath;
        mRhythmStart = start;
    }

    public void rhythmDetection(YYMediaSample sample) {
        sample.mAudioFrameData.beat = 0;
        sample.mAudioFrameData.loudness = 0;
        if (mRhythmInfo != null) {
            if (mRhythmInfo.rhythmInfoBeatList != null) {
                RhythmInfo.RhythmInfoBeat rhythmInfoBeat = mRhythmInfo.findRhythmInfoBeat(sample.mTimestampMs + mRhythmStart);
                sample.mAudioFrameData.beat = (rhythmInfoBeat == null ? 0 : rhythmInfoBeat.quality);
            }

            if (mRhythmInfo.rhythmInfoPcmList != null) {
                RhythmInfo.RhythmInfoPcm rhythmInfoPcm = mRhythmInfo.findRhythmInfoPcm(sample.mTimestampMs + mRhythmStart);
                sample.mAudioFrameData.loudness = (rhythmInfoPcm == null ? 0 : rhythmInfoPcm.strength_ratio);
                sample.mAudioFrameData.loudnessSmooth = (rhythmInfoPcm == null ? 0 : rhythmInfoPcm.smooth_strength_ratio);
            }
        }
    }

    public HumanBodyDetectWrapper.HumanBodyDetectRes bodyInfoSearch(YYMediaSample sample) {
        //YYLog.info(TAG, "jyq test bodyInfoSearch:" + sample.mTimestampMs);
        //查找当前时间戳对应的肢体信息保存列表中的位置
        HumanBodyDetectWrapper.HumanBodyDetectRes res = mHumanBodyDetectWrapper.findBodiesDetectInsertPos(sample.mTimestampMs);

        if (res.isFound) {
            //当前时间戳对应的body info已经保存的情况
            if (mHumanBodyDetectWrapper.bodiesDetectInfoList.get(res.pos).mBodyDetectInfoList.size() > 0) {
                sample.mBodyFrameDataArr = new OrangeFilter.OF_BodyFrameData[mHumanBodyDetectWrapper.bodiesDetectInfoList.get(res.pos).mBodyDetectInfoList.size()];
                for (int i = 0; i < sample.mBodyFrameDataArr.length; i++) {
                    BodiesDetectInfo.BodyDetectInfo bodyDetectInfo = mHumanBodyDetectWrapper.bodiesDetectInfoList.get(res.pos).mBodyDetectInfoList.get(i);
                    if (bodyDetectInfo != null && bodyDetectInfo.mBodyPointList != null && bodyDetectInfo.mBodyPointsScoreList != null) {
                        sample.mBodyFrameDataArr[i] = new OrangeFilter.OF_BodyFrameData();
                        sample.mBodyFrameDataArr[i].bodyPoints = new float[bodyDetectInfo.mBodyPointList.size()];
                        for (int j = 0; j < sample.mBodyFrameDataArr[i].bodyPoints.length; j++) {
                            sample.mBodyFrameDataArr[i].bodyPoints[j] = bodyDetectInfo.mBodyPointList.get(j);
                        }
                        sample.mBodyFrameDataArr[i].bodyPointsScore = new float[bodyDetectInfo.mBodyPointsScoreList.size()];
                        for (int j = 0; j < sample.mBodyFrameDataArr[i].bodyPointsScore.length; j++) {
                            sample.mBodyFrameDataArr[i].bodyPointsScore[j] = bodyDetectInfo.mBodyPointsScoreList.get(j);
                        }
                    }
                }
            } else {
                sample.mBodyFrameDataArr = null;
            }
        }
        return res;
    }

    public FaceDetectWrapper.FaceDetectRes faceInfoSearch(YYMediaSample sample) {
        //YYLog.info(TAG, "jyq test faceInfoSearch:" + sample.mTimestampMs);
        //查找当前时间戳对应的人脸信息保存列表中的位置
        FaceDetectWrapper.FaceDetectRes res = mFaceDetectWrapper.findFacesDetectInsertPos(sample.mTimestampMs);

        if (res.isFound) {
            //当前时间戳对应的face info已经保存的情况
            if (mFaceDetectWrapper.facesDetectInfoList.get(res.pos).mFaceDetectInfoList.size() > 0) {
                sample.mFaceFrameDataArr = new OrangeFilter.OF_FaceFrameData[mFaceDetectWrapper.facesDetectInfoList.get(res.pos).mFaceDetectInfoList.size()];
                for (int i = 0; i < sample.mFaceFrameDataArr.length; i++) {
                    FacesDetectInfo.FaceDetectInfo faceDetectInfo = mFaceDetectWrapper.facesDetectInfoList.get(res.pos).mFaceDetectInfoList.get(i);
                    if (faceDetectInfo != null && faceDetectInfo.mFacePointList != null) {
                        sample.mFaceFrameDataArr[i] = new OrangeFilter.OF_FaceFrameData();
                        sample.mFaceFrameDataArr[i].facePoints = new float[faceDetectInfo.mFacePointList.size()];
                        for (int j = 0; j < sample.mFaceFrameDataArr[i].facePoints.length; j++) {
                            sample.mFaceFrameDataArr[i].facePoints[j] = faceDetectInfo.mFacePointList.get(j);
                        }
                    }
                }
            } else {
                sample.mFaceFrameDataArr = null;
            }
        }
        return res;
    }

    //GPU抠图缓存数据查询，并还原抠图结果。GPU抠图缓存的是中间数据
    public SegmentCacheDetectWrapper.SegmentCacheDetectRes segmentCacheSearchAndDataRestore(YYMediaSample sample) {
        //查找当前时间戳对应的抠图信息保存列表中的位置
        SegmentCacheDetectWrapper.SegmentCacheDetectRes res = mSegmentCacheDetectWrapper.findSegmentCacheInsertPos(sample.mTimestampMs);

        if (res.isFound) {
            //当前时间戳对应的segment cache已经保存的情况
            SegmentCacheDetectWrapper.SegmentCacheData segmentCacheData = mSegmentCacheDetectWrapper.segmentCacheDataList.get(res.pos);
            if (segmentCacheData != null) {
                /*YYLog.info(TAG, "jyq test updateSegmentDataWithCache with inCacheData:currentPts=" +
                        sample.mTimestampMs + ",findPts=" + segmentCacheData.timestamp + "inputBytes=" + segmentCacheData.bytes);*/
                Venus.VN_SegmentCacheData vnSegmentCacheData = new Venus.VN_SegmentCacheData();
                vnSegmentCacheData.bytes = segmentCacheData.bytes;
                vnSegmentCacheData.timestamp = segmentCacheData.timestamp;
                vnSegmentCacheData.data = segmentCacheData.data;
                mVenusSegmentWrapper.updateSegmentDataWithCache(sample, vnSegmentCacheData, null);
            }
        }
        return res;
    }

    //CPU抠图缓存数据查询，并还原抠图结果。CPU抠图缓存的是抠图的最终结果
    public SegmentCacheDetectWrapper.SegmentCacheDetectRes segmentCacheSearchAndDataRestoreCpu(YYMediaSample sample, int threshold) {
        //查找当前时间戳对应的抠图信息保存列表中的位置
        SegmentCacheDetectWrapper.SegmentCacheDetectRes res = mSegmentCacheDetectWrapper.findSegmentCacheInsertPos(sample.mTimestampMs, threshold);

        if (res.isFound) {
            //当前时间戳对应的segment cache已经保存的情况
            SegmentCacheDetectWrapper.SegmentCacheData segmentCacheData = mSegmentCacheDetectWrapper.segmentCacheDataList.get(res.pos);
            if (segmentCacheData != null) {
//                long start = System.currentTimeMillis();
                mVenusSegmentWrapper.processSegmentDataCpu(sample, segmentCacheData.data, segmentCacheData.width, segmentCacheData.height);
//                long cost = System.currentTimeMillis() - start;
//                YYLog.info(TAG, "jyq test updateSegmentDataWithCache with inCacheData:currentPts=" + sample.mTimestampMs +
//                        ",findPts=" + segmentCacheData.timestamp + ",inputBytes=" + segmentCacheData.bytes + ",cost=" + cost);
            }
        }
        return res;
    }

    //人脸信息检测并保存,当某帧送入人脸检测前pts已经确定了,就可以在检测后直接保存facesDetectInfoList了
    public STMobileFaceDetectionWrapper.FacePointInfo faceInfoDetectAndSave(Context context, YYMediaSample sample, int pos) {
        STMobileFaceDetectionWrapper.FacePointInfo humanBodyPointInfo = bodyInfoDetect(context, sample);
        //保存face info
        FacesDetectInfo facesDetectInfo = new FacesDetectInfo(sample.mTimestampMs);
        if (sample.mFaceFrameDataArr != null && sample.mFaceFrameDataArr.length > 0) {
            for (int i = 0; i < sample.mFaceFrameDataArr.length; i++) {
                FacesDetectInfo.FaceDetectInfo faceDetectInfo = new FacesDetectInfo.FaceDetectInfo();

                for (int j = 0; j < sample.mFaceFrameDataArr[i].facePoints.length; j++) {
                    faceDetectInfo.mFacePointList.add(sample.mFaceFrameDataArr[i].facePoints[j]);
                }

                facesDetectInfo.mFaceDetectInfoList.add(faceDetectInfo);
            }
        }

        if (mFaceDetectWrapper.facesDetectInfoList.size() >= pos) {
            mFaceDetectWrapper.facesDetectInfoList.add(pos, facesDetectInfo);
        }

        return humanBodyPointInfo;
    }


    //肢体信息检测,但并不保存到bodiesDetectInfoList,因为录制过程中在mux前sample的pts不确定,而bodiesDetectInfoList保存的肢体信息是与pts对应的
    //1.检测到肢体信息，sample中到body data填充肢体信息数据
    //2.没有检测到肢体信息, sample中body data填充一个空的body frame data
    public STMobileFaceDetectionWrapper.FacePointInfo bodyInfoDetect(Context context, YYMediaSample sample) {
        STMobileFaceDetectionWrapper.FacePointInfo humanBodyPointInfo = STMobileFaceDetectionWrapper.getInstance(context).getCurrentFacePointInfo();
        if (mNeedCheckBody) {
            sample.mBodyFrameDataArr = new OrangeFilter.OF_BodyFrameData[0];
        }
        if (mNeedCheckFace) {
            sample.mFaceFrameDataArr = new OrangeFilter.OF_FaceFrameData[0];
        }

        if (humanBodyPointInfo != null && humanBodyPointInfo.mFrameData != null) {
            if (humanBodyPointInfo.mBodyCount > 0) {
                sample.mBodyFrameDataArr = humanBodyPointInfo.mFrameData.bodyFrameDataArr;
            }
            if (humanBodyPointInfo.mFaceCount > 0) {
                sample.mFaceFrameDataArr = humanBodyPointInfo.mFrameData.faceFrameDataArr;
            }
        }

        return humanBodyPointInfo;
    }

    //肢体信息检测并保存,当某帧送入肢体检测前pts已经确定了,就可以在检测后直接保存到bodiesDetectInfoList了
    public STMobileFaceDetectionWrapper.FacePointInfo bodyInfoDetectAndSave(Context context, YYMediaSample sample, int pos) {
        STMobileFaceDetectionWrapper.FacePointInfo humanBodyPointInfo = bodyInfoDetect(context, sample);
        //保存body info
        BodiesDetectInfo bodiesDetectInfo = new BodiesDetectInfo(sample.mTimestampMs);
        if (sample.mBodyFrameDataArr != null && sample.mBodyFrameDataArr.length > 0) {
            for (int i = 0; i < sample.mBodyFrameDataArr.length; i++) {
                BodiesDetectInfo.BodyDetectInfo bodyDetectInfo = new BodiesDetectInfo.BodyDetectInfo();

                for (int j = 0; j < sample.mBodyFrameDataArr[i].bodyPointsScore.length; j++) {
                    bodyDetectInfo.mBodyPointsScoreList.add(sample.mBodyFrameDataArr[i].bodyPointsScore[j]);
                }
                for (int j = 0; j < sample.mBodyFrameDataArr[i].bodyPoints.length; j++) {
                    bodyDetectInfo.mBodyPointList.add(sample.mBodyFrameDataArr[i].bodyPoints[j]);
                }
                bodiesDetectInfo.mBodyDetectInfoList.add(bodyDetectInfo);
            }
        }

        if (mHumanBodyDetectWrapper.bodiesDetectInfoList.size() >= pos) {
            mHumanBodyDetectWrapper.bodiesDetectInfoList.add(pos, bodiesDetectInfo);
        }

        return humanBodyPointInfo;
    }

    //初始化抠图
    public void initSegment(Context context) {
        if (!SDKCommonCfg.getUseCpuSegmentMode()) {
            mVenusSegmentWrapper = new VenusSegmentWrapper(context, mOutputWidth, mOutputHeight);
            mVenusSegmentWrapper.init();
        } else {
            STMobileFaceDetectionWrapper.getInstance(context).setNeedCpuSegment(true);
        }
    }

    //获取抠图结果，并保存抠图返回到中间数据，供后续使用
    public void segmentDataDetectAndCacheSave(YYMediaSample sample) {
        Venus.VN_SegmentCacheData outCacheData = new Venus.VN_SegmentCacheData();
        outCacheData.timestamp = sample.mTimestampMs;

        if (mVenusSegmentWrapper != null) {
            mVenusSegmentWrapper.updateSegmentDataWithCache(sample, null, outCacheData);
        }

        //由于venus sdk返回的cache data是异步的，outCacheData在经过抠图sdk前后的pts会被改变，所以插入位置要重新计算
        if (outCacheData.bytes > 0) {
            int pos = mSegmentCacheDetectWrapper.findSegmentCacheInsertPos(outCacheData.timestamp).pos;
            /*YYLog.info(TAG, "jyq test updateSegmentDataWithCache without inCacheData:currentPts=" + sample.mTimestampMs
                    + ",outPts=" + outCacheData.timestamp + ",insertPos=" + pos);*/
            SegmentCacheDetectWrapper.SegmentCacheData cache = new SegmentCacheDetectWrapper.SegmentCacheData();
            cache.bytes = outCacheData.bytes;
            cache.timestamp = outCacheData.timestamp;
            cache.data = outCacheData.data;
            mSegmentCacheDetectWrapper.segmentCacheDataList.add(pos, cache);
        }
    }

    //CPU版本的获取抠图结果，并保存
    public void segmentDataDetectAndCacheSaveCpu(YYMediaSample sample, byte[] imageData, int pos) {
        Venus.VN_SegmentCacheData outCacheData = new Venus.VN_SegmentCacheData();
        outCacheData.timestamp = sample.mTimestampMs;

        if (mVenusSegmentWrapper != null) {
//            long start = System.currentTimeMillis();
            Venus.VN_ImageData vnSegmentData = new Venus.VN_ImageData();
            mVenusSegmentWrapper.updateSegmentDataWithCacheCpu(sample, imageData, vnSegmentData, sample.mWidth, sample.mHeight, VenusSegmentWrapper.SEGMENT_INPUT_RGBA);
            if (pos >= 0) {
                SegmentCacheDetectWrapper.SegmentCacheData segmentCacheData = new SegmentCacheDetectWrapper.SegmentCacheData();
                segmentCacheData.timestamp = sample.mTimestampMs;
                segmentCacheData.channel = vnSegmentData.channel;
                segmentCacheData.width = vnSegmentData.width;
                segmentCacheData.height = vnSegmentData.height;
                segmentCacheData.data = vnSegmentData.data;

                if (vnSegmentData.data.length > 0) {
                    mSegmentCacheDetectWrapper.segmentCacheDataList.add(pos, segmentCacheData);
                }
            }
//            long cost = System.currentTimeMillis() - start;
//            YYLog.info(TAG, "jyq test updateSegmentDataWithCache without inCacheData:currentPts=" + sample.mTimestampMs + ",cost=" + cost);
        }
    }


    //判断当前sample在apply orangeFilter之前需要填充哪些辅助信息，比如face data，body data，audio data等
    public int getRequiredFrameData(YYMediaSample sample) {
        int requiredFrameData = OrangeFilter.OF_RequiredFrameData_None;
        FilterDataStore.OperResult<Integer, BaseFilter> res = mFilterStore.getFilerInfoBySessionID(kFilterStoreID);

        if (res.mFilterList != null && res.mFilterList.size() > 0) {
            //获取实际传给orangeFilter检测的filter个数，用于初始化检测filterId数组大小
            int validFilterNum = 0;
            for (BaseFilter filter : res.mFilterList) {
                if (filter.mFilterId > 0 && sample.mApplyFilterIDs.contains(filter.mFilterInfo.mFilterID)) {
                    validFilterNum++;
                }
            }

            if (validFilterNum > 0) {
                int[] ids = new int[validFilterNum];
                int idIndex = 0;
                for (BaseFilter filter : res.mFilterList) {
                    if (filter.mFilterId > 0 && sample.mApplyFilterIDs.contains(filter.mFilterInfo.mFilterID)) {
                        ids[idIndex++] = filter.mFilterId;
                    }
                }
                requiredFrameData = OrangeFilter.getRequiredFrameData(mOFContext, ids);
            }
        }
        return requiredFrameData;
    }

    public void setMediaInfoRequireListener(IMediaInfoRequireListener listener) {
        mMediaInfoRequireListener = listener;
    }

    public Handler getHandler() {
        return mFilterHandler;
    }

    /**
     * 每次处理一个新的sample，restore filterGroup中的output textured id
     */
    protected void restoreOutputTexture() {
        for (int i = 0; i < TEXTURE_NUM; i++) {
            mTextures[i] = mOriginTextures[i];
        }
    }

    public byte[] getVideoImageData(YYMediaSample sample) {
        if (mGlImageReader == null) {
            //pbo模式异步会导致当前读取来的cpu数据和纹理数据不对应，导致抠图，人脸识别等传递的源数据不对，所以这里不用pbo
            mGlImageReader = new GlTextureImageReader(sample.mWidth, sample.mHeight, false);
        }

        byte[] pixBuf = mGlImageReader.read(sample.mTextureId, sample.mWidth, sample.mHeight);
        return pixBuf;
    }
}
