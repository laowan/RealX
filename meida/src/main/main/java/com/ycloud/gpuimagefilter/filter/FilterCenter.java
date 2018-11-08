package com.ycloud.gpuimagefilter.filter;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.ycloud.api.common.FilterType;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.BeautyFaceFilterParameter;
import com.ycloud.gpuimagefilter.param.BlurFilterParameter;
import com.ycloud.gpuimagefilter.param.ColorTableFilterParameter;
import com.ycloud.gpuimagefilter.param.DoubleColorTableFilterParameter;
import com.ycloud.gpuimagefilter.param.EffectFilterParameter;
import com.ycloud.gpuimagefilter.param.FadeBlendFilterParameter;
import com.ycloud.gpuimagefilter.param.OFBasketBallGameParameter;
import com.ycloud.gpuimagefilter.param.OFEditStickerEffectFilterParameter;
import com.ycloud.gpuimagefilter.param.OFGameParameter;
import com.ycloud.gpuimagefilter.param.PuzzleFilterParameter;
import com.ycloud.gpuimagefilter.param.StretchFilterParameter;
import com.ycloud.gpuimagefilter.param.ThinFaceFilterParameter;
import com.ycloud.gpuimagefilter.param.TimeRangeEffectFilterParameter;
import com.ycloud.gpuimagefilter.param.WordStickerEffectFilterParameter;
import com.ycloud.gpuimagefilter.utils.BodiesDetectInfo;
import com.ycloud.gpuimagefilter.utils.FacesDetectInfo;
import com.ycloud.gpuimagefilter.utils.FilterClsInfo;
import com.ycloud.gpuimagefilter.utils.FilterConfig;
import com.ycloud.gpuimagefilter.utils.FilterDataStore;
import com.ycloud.gpuimagefilter.utils.FilterIDManager;
import com.ycloud.gpuimagefilter.utils.FilterInfo;
import com.ycloud.gpuimagefilter.utils.FilterInfoDataStore;
import com.ycloud.gpuimagefilter.utils.FilterLayout;
import com.ycloud.gpuimagefilter.utils.IFilterInfoListener;
import com.ycloud.gpuimagefilter.utils.SegmentCacheDetectWrapper;
import com.ycloud.utils.CommonUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Filter中打多包含gpu资源，android系统中gpu资源与线程关系密切，本类只是维护着filter的信息,
 * 而不真正的创建具体的filter实例，filter实例的创建由FilterCenter的Observer业务来实现.
 */
public class FilterCenter {
    private String TAG = "FilterCenter";
    private static volatile FilterCenter singleton;

    private FilterCenter() {
        registerFilterClsInfo(FilterType.GPUFILTER_BEAUTYFACE, OFBeautyFaceFilter.class, BeautyFaceFilterParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_THINFACE, OFThinFaceFilter.class, ThinFaceFilterParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_COLORTABLE, OFColorTableFilter.class, ColorTableFilterParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_EFFECT, OFEffectFilter.class, EffectFilterParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_DOUBLE_COLORTABLE, OFDoubleColorTableFilter.class, DoubleColorTableFilterParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_FADE_BLEND, FadeBlendFilter.class, FadeBlendFilterParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_TIMERANGE_EFFECT, OFTimeRangeEffectFilter.class, TimeRangeEffectFilterParameter.class);
		registerFilterClsInfo(FilterType.GPUFILTER_EDIT_STICKER_EFFECT, OFEditStickerEffectFilter.class, OFEditStickerEffectFilterParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_BASKETBALLGAME, OFBasketBallGameFilter.class, OFBasketBallGameParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_PUZZLE, OFPuzzleFilter.class, PuzzleFilterParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_GAME, OFGameFilter.class, OFGameParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_STRETCH, OFStretchFilter.class, StretchFilterParameter.class);
        registerFilterClsInfo(FilterType.GPUFILTER_BLUR_EFFECT, OFBlurFilter.class, BlurFilterParameter.class);
        //registerFilterClsInfo(FilterType.GPUFILTER_MAPPING, OFBeautyFaceFilter.class, BeautyFaceFilterParameter.class);
        //registerFilterClsInfo(FilterType.GPUFILTER_BEAUTYFACE, OFBeautyFaceFilter.class, BeautyFaceFilterParameter.class);
    }

    public static FilterCenter getInstance() {
        if (singleton == null) {
            synchronized (FilterCenter.class) {
                if (singleton == null) {
                    singleton = new FilterCenter();
                }
            }
        }
        return singleton;
    }

    private static final int MSG_FILTER_ADD = 0x01;
    private static final int MSG_FILTER_REMOVE = 0x02;
    private static final int MSG_FILTER_MODIFY = 0x03;
    private static final int MSG_FILTER_BATCH_ADD = 0x04;
    private static final int MSG_FILTER_BATCH_REMOVE = 0x05;
    private static final int MSG_FILTER_BATCH_MODIFY = 0x06;
    private static final int MSG_FILTER_MODIFY_NO_COPY = 0x07;
    private static final int MSG_CACHE_CLEAR = 0x08;

    public static interface FilterObserverInterface {
        public void onFilterAdd(final FilterInfo filterInfo, long verID);

        public void onFilterRemove(final Integer filterID, long verID);

        public void onFilterModify(final FilterInfo filterInfo, long verID, boolean isDuplicate);

        public void onFilterBatchAdd(final ArrayList<FilterInfo> filterInfos, long verID);

        public void onFilterBatchRemove(final ArrayList<Integer> filterIDs, long verID);

        public void onFilterBatchModify(final ArrayList<FilterInfo> filterInfos, long verID);

        public void onCacheClear();
    }

    public static class FilterObserver {
        FilterObserverInterface mObserver = null;
        Handler mObserverHandler = null;

        public FilterObserver(FilterObserverInterface observer) {
            mObserver = observer;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;

            if (obj == null || getClass() != obj.getClass())
                return false;

            return (this.mObserver == ((FilterObserver) obj).mObserver);
        }
    }

    private ConcurrentHashMap<Integer, CopyOnWriteArrayList<FilterObserver>> mSession2FilterObservers = new ConcurrentHashMap<Integer, CopyOnWriteArrayList<FilterObserver>>();
    private FilterInfoDataStore<Integer, FilterInfo> mDataStore = new FilterInfoDataStore();
    private ConcurrentHashMap<Integer, FilterClsInfo> mFilterClsInfos = new ConcurrentHashMap<Integer, FilterClsInfo>();

    public boolean registerFilterClsInfo(int type, Class filterCls, Class parameterCls) {
        /* TODO. 判断是否是BaseFilter的子类.
        if(!filterCls.isAssignableFrom(BaseFilter.class))
            return false;

        if(!parameterCls.isAssignableFrom(BaseFilterParameter.class))
            return false;
            */

        FilterClsInfo clsInfo = new FilterClsInfo();
        clsInfo.mFilterCls = filterCls;
        clsInfo.mFilterParameterCls = parameterCls;
        return (mFilterClsInfos.put(type, clsInfo) != null);
    }

    public FilterClsInfo getFilterClsInfo(int type) {
        return mFilterClsInfos.get(type);
    }

    public FilterSession createFilterSession() {
        FilterSession session = new FilterSession(FilterIDManager.getSessionID());
        return session;
    }

    public FilterSession createFilterSession(int sid) {
        FilterSession session = new FilterSession(sid);
        return session;
    }

    public void destroyFilterSession(FilterSession session) {
        //destory session
        removeAllFilter(session.getSessionID());
        //notify the session will be destroy.
    }

    public void removeFilterObserver(final FilterObserverInterface observer, int sessionID) {
        if (observer != null) {
            Integer key = Integer.valueOf(sessionID);
            CopyOnWriteArrayList<FilterObserver> filterObservers = mSession2FilterObservers.get(key);
            if (filterObservers != null) {
                synchronized (this) {
                    //CopyOnWriteArrayList<FilterObserver> filter
                    filterObservers.remove(new FilterObserver(observer));
                    //filterObserves是CopyOnWriteArrayList,　可能读取到脏的数据，所以在判断是否要删除对应的session
                    //时候，需要锁住.
                    if (filterObservers.isEmpty()) {
                        mSession2FilterObservers.remove(key);
                    }
                }
            }
        }
    }

    /**
     * looper为null，则filter相关事件会抛给主线程
     */
    public void addFilterObserver(final FilterObserverInterface observerInterface, Looper looper, int sessionID) {
        if (observerInterface == null)
            return;

        final FilterObserver observer = new FilterObserver(observerInterface);

        Looper myLooper = looper != null ? looper : Looper.getMainLooper();
        observer.mObserverHandler = new Handler(myLooper, null) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_FILTER_ADD:
                        observer.mObserver.onFilterAdd((FilterInfo) msg.obj, CommonUtil.LongFrom(msg.arg2, msg.arg1));
                        break;
                    case MSG_FILTER_REMOVE:
                        observer.mObserver.onFilterRemove((Integer) msg.obj, CommonUtil.LongFrom(msg.arg2, msg.arg1));
                        break;
                    case MSG_FILTER_MODIFY:
                        observer.mObserver.onFilterModify((FilterInfo) msg.obj, CommonUtil.LongFrom(msg.arg2, msg.arg1), true);
                        break;
                    case MSG_FILTER_MODIFY_NO_COPY:
                        observer.mObserver.onFilterModify((FilterInfo) msg.obj, CommonUtil.LongFrom(msg.arg2, msg.arg1), false);
                        break;
                    case MSG_FILTER_BATCH_ADD:
                        observer.mObserver.onFilterBatchAdd((ArrayList<FilterInfo>) msg.obj, CommonUtil.LongFrom(msg.arg2, msg.arg1));
                        break;
                    case MSG_FILTER_BATCH_MODIFY:
                        observer.mObserver.onFilterBatchModify((ArrayList<FilterInfo>) msg.obj, CommonUtil.LongFrom(msg.arg2, msg.arg1));
                        break;
                    case MSG_FILTER_BATCH_REMOVE:
                        observer.mObserver.onFilterBatchRemove((ArrayList<Integer>) msg.obj, CommonUtil.LongFrom(msg.arg2, msg.arg1));
                        break;
                    case MSG_CACHE_CLEAR:
                        observer.mObserver.onCacheClear();
                        break;
                }
            }
        };
        Integer key = Integer.valueOf(sessionID);
        CopyOnWriteArrayList<FilterObserver> filterObservers = mSession2FilterObservers.get(key);

        synchronized (this) {
            if (filterObservers == null) {
                filterObservers = mSession2FilterObservers.get(key);
                if (filterObservers == null) {
                    filterObservers = new CopyOnWriteArrayList<FilterObserver>();
                    mSession2FilterObservers.put(key, filterObservers);
                }
            }
            //filterObserves是CopyOnWriteArrayList,　可能读取到脏的数据，所以在判断是否要删除对应的session
            //时候，需要锁住.
            filterObservers.add(observer);
        }
    }

    /**
     * 通知filterGroup线程清除of context下的缓存
     */
    public void notifyCacheClear(Integer sessionID) {
        CopyOnWriteArrayList<FilterObserver> list = mSession2FilterObservers.get(sessionID);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                FilterObserver observer = list.get(i);
                observer.mObserverHandler.sendEmptyMessage(MSG_CACHE_CLEAR);
            }
        }
    }

    public synchronized void notifyFilterAdd(FilterInfo filterInfo, Integer sessionID, long verID) {
        //TODO. Copy-on-write iterator
        CopyOnWriteArrayList<FilterObserver> list = mSession2FilterObservers.get(sessionID);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                FilterObserver observer = list.get(i);
                observer.mObserverHandler.sendMessage(observer.mObserverHandler.obtainMessage(MSG_FILTER_ADD,
                        CommonUtil.getHighQuad(verID), CommonUtil.getLowQuad(verID), filterInfo));
            }
        }
    }

    public synchronized void notifyFilterRemove(int filterID, Integer sessionID, long verID) {
        CopyOnWriteArrayList<FilterObserver> list = mSession2FilterObservers.get(sessionID);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                FilterObserver observer = list.get(i);
                observer.mObserverHandler.sendMessage(observer.mObserverHandler.obtainMessage(MSG_FILTER_REMOVE,
                    CommonUtil.getHighQuad(verID), CommonUtil.getLowQuad(verID), Integer.valueOf(filterID)));
        }
        }
    }

    public synchronized void notifyFilterModify(FilterInfo filterInfo, Integer sessionID, long verID) {
        CopyOnWriteArrayList<FilterObserver> list = mSession2FilterObservers.get(sessionID);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                FilterObserver observer = list.get(i);
                observer.mObserverHandler.sendMessage(observer.mObserverHandler.obtainMessage(MSG_FILTER_MODIFY,
                        CommonUtil.getHighQuad(verID), CommonUtil.getLowQuad(verID), filterInfo));
            }
        }
    }

    public synchronized void notifyFilterModifyWithoutCopy(FilterInfo filterInfo, Integer sessionID, long verID) {
        CopyOnWriteArrayList<FilterObserver> list = mSession2FilterObservers.get(sessionID);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                FilterObserver observer = list.get(i);
                observer.mObserverHandler.sendMessage(observer.mObserverHandler.obtainMessage(MSG_FILTER_MODIFY_NO_COPY,
                        CommonUtil.getHighQuad(verID), CommonUtil.getLowQuad(verID), filterInfo));
            }
        }
    }

    public synchronized void notifyFilterBatchAdd(ArrayList<FilterInfo> filterInfos, Integer sessionID, long verID) {
        CopyOnWriteArrayList<FilterObserver> list = mSession2FilterObservers.get(sessionID);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                FilterObserver observer = list.get(i);
                observer.mObserverHandler.sendMessage(observer.mObserverHandler.obtainMessage(MSG_FILTER_BATCH_ADD,
                        CommonUtil.getHighQuad(verID), CommonUtil.getLowQuad(verID), filterInfos));
                ;
            }
        }
    }

    public synchronized void notifyFilterBatchRemove(ArrayList<Integer> fieldIDs, Integer sessionID, long verID) {
        CopyOnWriteArrayList<FilterObserver> list = mSession2FilterObservers.get(sessionID);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                FilterObserver observer = list.get(i);
                observer.mObserverHandler.sendMessage(observer.mObserverHandler.obtainMessage(MSG_FILTER_BATCH_REMOVE,
                        CommonUtil.getHighQuad(verID), CommonUtil.getLowQuad(verID), fieldIDs));
            }
        }
    }

    public synchronized void notifyFilterBatchModify(ArrayList<FilterInfo> filterInfos, Integer sessionID, long verID) {
        CopyOnWriteArrayList<FilterObserver> list = mSession2FilterObservers.get(sessionID);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                FilterObserver observer = list.get(i);
                observer.mObserverHandler.sendMessage(observer.mObserverHandler.obtainMessage(MSG_FILTER_BATCH_MODIFY,
                        CommonUtil.getHighQuad(verID), CommonUtil.getLowQuad(verID), filterInfos));
            }
        }
    }

    public void setFilterInfoListener(IFilterInfoListener listener, int sessionID) {
        Integer sessionKey = Integer.valueOf(sessionID);
        mDataStore.setFilterInfoListener(listener, sessionKey);
    }

    public IFilterInfoListener getFilterInfoListener(int sessionKey) {
        return mDataStore.getFilterInfoListener(sessionKey);
    }

    /**
     * 清空sessionID对应的filterGroup下的of context缓存，以及时释放gpu memory
     */
    public void clearCachedResource(int sessionID) {
        Integer sessKey = Integer.valueOf(sessionID);
        notifyCacheClear(sessKey);
    }

    /**
     * 添加一个FilterType类型的filter, filter为默认参数，返回filter实例对应的id号
     */
    public int addFilter(int type, String groupType, int zOrderID, int sessionID) {
        Integer sessKey = Integer.valueOf(sessionID);
        FilterInfo info = new FilterInfo(type, groupType);
        info.mZOrder = zOrderID;
        info.mFilterID = FilterIDManager.getFilterID();
        info.mSessionID = sessionID;

        long verID = mDataStore.addFilter(Integer.valueOf(info.mFilterID), info.mFilterType, info, sessKey);
        notifyFilterAdd(info.duplicate(), sessKey, verID);
        return info.mFilterID;
    }

    /**
     * 添加一个FilterType类型的filter, filter参数为parameter，返回filter实例对应的id号
     */
    public int addFilter(int type, String groupType, BaseFilterParameter parameter, int zOrderID, int sessionID) {

        Integer sessKey = Integer.valueOf(sessionID);

        FilterInfo info = new FilterInfo(type, groupType);
        info.mZOrder = zOrderID;
        info.mFilterID = FilterIDManager.getFilterID();
        info.mSessionID = sessionID;
        if (parameter != null) {
            BaseFilterParameter dup = parameter.duplicate();
            dup.mParameterID = FilterIDManager.getParamID();
            info.addFilterParameter(dup);
        }

        long verID = mDataStore.addFilter(Integer.valueOf(info.mFilterID), info.mFilterType, info, sessKey);
        notifyFilterAdd(info.duplicate(), sessKey, verID);
        return info.mFilterID;
    }

    /**
     * 添加一个FilterType类型的filter, filter为默认参数，返回filter实例对应的id号
     */
    public int addFilter(int type, String groupType, int sessionID) {
        Integer sessKey = Integer.valueOf(sessionID);
        FilterInfo info = new FilterInfo(type, groupType);
        info.mZOrder = FilterLayout.generateZOrderID();
        info.mFilterID = FilterIDManager.getFilterID();
        info.mSessionID = sessionID;
        long verID = mDataStore.addFilter(Integer.valueOf(info.mFilterID), info.mFilterType, info, sessKey);
        notifyFilterAdd(info.duplicate(), sessKey, verID);
        return info.mFilterID;
    }

    /**
     * 添加一个FilterType类型的filter, filter参数为parameter，返回filter实例对应的id号
     */
    public int addFilter(int type, String groupType, BaseFilterParameter parameter, int sessionID) {

        Integer sessKey = Integer.valueOf(sessionID);

        FilterInfo info = new FilterInfo(type, groupType);
        info.mZOrder = FilterLayout.generateZOrderID();
        info.mFilterID = FilterIDManager.getFilterID();
        info.mSessionID = sessionID;
        if (parameter != null) {
            BaseFilterParameter dup = parameter.duplicate();
            dup.mParameterID = FilterIDManager.getParamID();
            info.addFilterParameter(dup);
        }

        long verID = mDataStore.addFilter(Integer.valueOf(info.mFilterID), info.mFilterType, info, sessKey);
        notifyFilterAdd(info.duplicate(), sessKey, verID);
        return info.mFilterID;
    }

    /*加载一个json_cfg配置文件中的filter, 返回配置文件中对应的filter实例的ID列表*/
    public ArrayList<Integer> addFilter(String json_cfg, int sessionID, boolean isUpdateId) {
        FilterConfig cfg = new FilterConfig();
        cfg.unmarshall(json_cfg);

        //TODO. 预防json中的filter_id不能直接算做加入的id, filter_id, z-order等需要重新分配.
        if (cfg.mFilterInfos == null && cfg.mFilterInfos.isEmpty())
            return null;

        Integer sessKey = Integer.valueOf(sessionID);
        Comparator<FilterInfo> comparator = new Comparator<FilterInfo>() {
            public int compare(FilterInfo f1, FilterInfo f2) {
                if (f1 != null && f2 != null) {
                    return f1.mZOrder - f2.mZOrder;
                } else if (f1 != null) {
                    return 1;
                } else {
                    return -1;
                }
            }
        };
        Collections.sort(cfg.mFilterInfos, comparator);

        long dataVersion = -1;
        ArrayList<FilterInfo> notifyList = new ArrayList<>();
        ArrayList<Integer> ret = new ArrayList<>();
        Iterator<FilterInfo> it = cfg.mFilterInfos.iterator();
        while (it.hasNext()) {
            FilterInfo filterInfo = it.next();
            if (isUpdateId) {
                filterInfo.mZOrder = FilterLayout.generateZOrderID();
                filterInfo.mFilterID = FilterIDManager.getFilterID();
            } else {
                //通过json还原后filter保持原id不变的情况
                FilterLayout.updateZOrderID(filterInfo.mZOrder + 1);
                FilterIDManager.updateFilterID(filterInfo.mFilterID + 1);
            }

            dataVersion = mDataStore.addFilter(filterInfo.mFilterID, filterInfo.mFilterType, filterInfo, sessKey);
            notifyList.add(filterInfo);
            ret.add(filterInfo.mFilterID);
        }
        if (!notifyList.isEmpty()) {
            notifyFilterBatchAdd(notifyList, sessKey, dataVersion);
        }
        return ret;
    }

    /*加载一个json_cfg配置文件中的filter, 返回配置文件中对应的filter实例的ID列表，同时设置生效时长startTime～endTime*/
    public ArrayList<FilterInfo> addFilter(String json_cfg, long startTime, long endTime, int sessionID) {
        FilterConfig cfg = new FilterConfig();
        cfg.unmarshall(json_cfg, startTime, endTime);

        //TODO. 预防json中的filter_id不能直接算做加入的id, filter_id, z-order等需要重新分配.
        if (cfg.mFilterInfos == null && cfg.mFilterInfos.isEmpty())
            return null;

        Integer sessKey = Integer.valueOf(sessionID);
        Comparator<FilterInfo> comparator = new Comparator<FilterInfo>() {
            public int compare(FilterInfo f1, FilterInfo f2) {
                if (f1 != null && f2 != null) {
                    return f1.mZOrder - f2.mZOrder;
                } else if (f1 != null) {
                    return 1;
                } else {
                    return -1;
                }
            }
        };
        Collections.sort(cfg.mFilterInfos, comparator);

        long dataVersion = -1;
        ArrayList<FilterInfo> notifyList = new ArrayList<>();
        ArrayList<FilterInfo> ret = new ArrayList<>();
        Iterator<FilterInfo> it = cfg.mFilterInfos.iterator();
        while (it.hasNext()) {
            FilterInfo filterInfo = it.next();
            filterInfo.mZOrder = FilterLayout.generateZOrderID();
            filterInfo.mFilterID = FilterIDManager.getFilterID();

            dataVersion = mDataStore.addFilter(filterInfo.mFilterID, filterInfo.mFilterType, filterInfo, sessKey);
            notifyList.add(filterInfo.duplicate());
            ret.add(filterInfo.duplicate());
        }
        if (!notifyList.isEmpty()) {
            notifyFilterBatchAdd(notifyList, sessKey, dataVersion);
        }
        return ret;
    }

    /*删除对应filterID的filter， 成功返回true， 否则返回false*/
    public boolean removeFilterByFilterID(int filterID, int sessionID) {
        Integer sessKey = Integer.valueOf(sessionID);
        Integer id = Integer.valueOf(filterID);
        Long verID = Long.valueOf(0);

        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.getFilter(id, sessionID);
        if (res.mFilter == null) {
            return false;
        }

        res = mDataStore.removeFilter(id, res.mFilter.mFilterType, sessKey);
        if (res.mSuccess) {
            notifyFilterRemove(filterID, sessKey, res.mSnapshotVer);
        }
        return res.mSuccess;
    }

    /*删除FilterType为type的所有filter实例， 成功返回true， 否则返回false*/
    public boolean removeFilterByFilterType(int type, int sessionID) {
        Integer sessKey = Integer.valueOf(sessionID);
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.removeFilter(type, sessKey);
        if (res.mFilterIDList != null) {
            notifyFilterBatchRemove(res.mFilterIDList, sessKey, res.mSnapshotVer);
            return !res.mFilterIDList.isEmpty();
        }
        return false;
    }

    /*删除所有filter实例， 成功返回true， 否则返回false*/
    public void removeAllFilter(int sessionID) {
        //sync..
        Integer sessKey = Integer.valueOf(sessionID);
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.removeAllFilter(sessKey);
        if (res.mFilterIDList != null) {
            notifyFilterBatchRemove(res.mFilterIDList, sessKey, res.mSnapshotVer);
        }
    }

    /*删除所有filter实例， 成功返回true， 否则返回false*/
    public int addFilterParameter(int filterID, BaseFilterParameter parameter, int sessionID) {
        //TODO.先不入Center dataStore, 预先调试一下看
        int paramID = FilterIDManager.getParamID();
        parameter.mParameterID = paramID;
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.addFilterParameter(filterID, parameter, sessionID);
        if (res.mSuccess && res.mFilter != null) {
            //notify the modify.
            notifyFilterModify(res.mFilter, sessionID, res.mSnapshotVer);
            return paramID;
        }
        return -1;
    }

    public int resetFilterParameter(int filterID, BaseFilterParameter parameter, int sessionID) {
        int paramID = FilterIDManager.getParamID();
        parameter.mParameterID = paramID;
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.resetFilterParameter(filterID, parameter, sessionID);
        if (res.mSuccess && res.mFilter != null) {
            //notify the modify.
            notifyFilterModify(res.mFilter, sessionID, res.mSnapshotVer);
            return paramID;
        }
        return -1;
    }

    /**
     * 通过修改filterSession中的param，触发filterGroup中param更新
     * 1.修改对应sessionId对应的filterDataStore中的param
     * 2.修改成功后，duplicate filter info并修改filterGroup中的param
     */
    public boolean modifyFilterParameter(int filterID, int paramID, BaseFilterParameter parameter, int sessionID, boolean isDuplicate) {
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.modifyFilterParameter(filterID, paramID, parameter, sessionID, isDuplicate);
        if (res.mSuccess && res.mFilter != null) {
            //notify the modify.

            if(isDuplicate) {
                notifyFilterModify(res.mFilter, sessionID, res.mSnapshotVer);
            } else {
                notifyFilterModifyWithoutCopy(res.mFilter, sessionID, res.mSnapshotVer);
            }

            return true;
        }
        return false;
    }

    /**
     * filterGroup中param更新后，同步修改filterSession中对应的param
     */
    public boolean updateFilterParameter(int filterID, int paramID, BaseFilterParameter parameter, int sessionID) {
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.updateFilterParameter(filterID, paramID, parameter, sessionID);
        if (res.mSuccess && res.mFilter != null) {
            return true;
        }
        return false;
    }

    public boolean modifyFilterZOrder(int filterID, int zOrder, int sessionID) {
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.modifyFilterZOrder(filterID, zOrder, sessionID);
        if (res.mSuccess && res.mFilter != null) {
            return true;
        }
        return false;
    }

    public void removeFilterParameter(int filterID, int paramID, int sessionID) {
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.removeFilterParameter(filterID, paramID, sessionID);
        if (res.mSuccess && res.mFilter != null) {
            //notify the modify.
            notifyFilterModify(res.mFilter, sessionID, res.mSnapshotVer);
        }
    }

    public void removeFilterParameter(int filterID, int sessionID) {
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.removeFilterParameter(filterID, sessionID);
        if (res.mSuccess && res.mFilter != null) {
            //notify the modify.
            notifyFilterModify(res.mFilter, sessionID, res.mSnapshotVer);
        }
    }

    /*获取对应filterID的filter实例的信息，成功返回对应的配置信息(deep-copy), 否则返回null*/
    public FilterInfo getFilterInfo(Integer filterID, int sessionID) {
        Integer id = Integer.valueOf(filterID);
        Integer sessKey = Integer.valueOf(sessionID);

        //deep copy for FilterInfo.
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.getFilter(id, sessKey);
        return res.mFilter;
    }

    public void modifyFilterInfo(Integer filterID, int sessionID, FilterInfo filterInfo) {
        Integer id = Integer.valueOf(filterID);
        Integer sessKey = Integer.valueOf(sessionID);

        mDataStore.setFilter(id, sessKey, filterInfo);
    }

    /*获取对应filterType为type的所有filter实例的信息列表，成功返回对应的配置信息列表(deep-copy), 否则返回null*/
    public CopyOnWriteArrayList<FilterInfo> getFilterInfoByType(int type, int sessionID) {
        //for FilterInfo, it is deep-copy.
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.getFilterInfoByType(type, sessionID);
        return res.mFilterCopyOnWriteList;
    }

    public ArrayList<FilterInfo> getFilerInfoBySessionID(int sessionID) {
        //for FilterInfo, it is deep copy
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.getFilerInfoBySessionID(Integer.valueOf(sessionID));
        return res.mFilterList;
    }

    FilterDataStore.OperResult<Integer, FilterInfo> getFilterSnapshot(int sessionID) {
        FilterDataStore.OperResult<Integer, FilterInfo> res = mDataStore.getFilerInfoBySessionID(Integer.valueOf(sessionID));
        return res;
    }


    public List<BodiesDetectInfo> getBodyDetectInfo() {
        return mDataStore.getBodyDetectInfo();
    }

    public List<FacesDetectInfo> getFaceDetectInfo() {
        return mDataStore.getFaceDetectInfo();
    }

    public List<SegmentCacheDetectWrapper.SegmentCacheData> getSegmentCacheInfo() {
        return mDataStore.getSegmentCacheInfo();
    }
}