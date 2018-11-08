package com.ycloud.gpuimagefilter.filter;

import com.ycloud.api.common.FilterGroupType;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.utils.FilterDataStore;
import com.ycloud.gpuimagefilter.utils.FilterInfo;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 用于同一group type下的filter实现互斥逻辑
 * Created by jinyongqing on 2017/8/29.
 */

public class VideoFilterSelector extends BaseFilter {
    private static final Integer kFilterStoreID = Integer.valueOf(0);

    //key:group type标识 value：同一group type下的filter id list
    private Map<String, List<Integer>> mFilterGroups;

    //不属于任一group type的filter id list，不与任何filter互斥
    private List<Integer> mDefaultApplyFilterIds;

    private FilterDataStore<Integer, BaseFilter> mFilterDataStore;

    public VideoFilterSelector(FilterDataStore<Integer, BaseFilter> filterDataStore) {
        super();
        mFilterGroups = new HashMap<>();
        mDefaultApplyFilterIds = new ArrayList<>();
        mFilterDataStore = filterDataStore;
    }


    /**
     * 1.filter id不属于任何filter group，添加filter id到default apply filter id list
     * 2.filter id属于已存在的filter group，添加filter id到filter group
     * 3.filter id的filter group尚未存在，新建filter group，并将当前filter id加入filter group
     *
     * @param filterID
     */
    public void addFilterID(int filterID) {
        BaseFilter filter = mFilterDataStore.unSafe_getFilter(filterID, kFilterStoreID);
        if (filter.getFilterInfo().mFilterGroupType.equals(FilterGroupType.DEFAULT_FILTER_GROUP)) {
            mDefaultApplyFilterIds.add(filterID);
        } else if (mFilterGroups.containsKey(filter.getFilterInfo().mFilterGroupType)) {
            List<Integer> filterIds = mFilterGroups.get(filter.getFilterInfo().mFilterGroupType);
            filterIds.add(filterID);
        } else {
            List<Integer> filterIds = new ArrayList<>();
            filterIds.add(filterID);
            mFilterGroups.put(filter.getFilterInfo().mFilterGroupType, filterIds);
        }
    }

    /**
     * 从default apply filter id list或者某个filter group中移除filterID
     *
     * @param filterID
     */
    public void removeFilterID(int filterID) {
        BaseFilter filter = mFilterDataStore.unSafe_getFilter(filterID, kFilterStoreID);
        if (mFilterGroups.containsKey(filter.getFilterInfo().mFilterGroupType)) {
            List<Integer> filterIds = mFilterGroups.get(filter.getFilterInfo().mFilterGroupType);
            filterIds.remove(Integer.valueOf(filterID));
        }

        if (mDefaultApplyFilterIds.contains(filterID)) {
            mDefaultApplyFilterIds.remove(Integer.valueOf(filterID));
        }
    }

    /**
     * 每组互斥的filter group中取出一个filter,放入当前apply filter id列表中
     *
     * @param sample
     * @param upstream
     * @return
     */
    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        sample.mApplyFilterIDs = new ArrayList<>(getApplyEffectFilterIDs(sample.mTimestampMs, mDefaultApplyFilterIds));

        for (List<Integer> filterIds : mFilterGroups.values()) {
            int applyEffectFilterID = getApplyEffectFilterID(sample.mTimestampMs, filterIds);
            if (applyEffectFilterID != -1) {
                sample.mApplyFilterIDs.add(applyEffectFilterID);
            }
        }
        deliverToDownStream(sample);
        return true;
    }


    /**
     * 判断是否在特效区间内
     */
    private boolean isInEffectArea(long effectStart, long effectEnd, float currentPtsMs) {
        if ((effectStart == -1 && effectEnd == -1) || (currentPtsMs >= effectStart && currentPtsMs <= effectEnd)) {
            return true;
        }
        return false;
    }


    /**
     * 获取当前sample使用的effect id
     *
     * @param pts 当前sample的pts
     * @return 使用的的effect id,没有则返回-1
     */
    public int getApplyEffectFilterID(long pts, List<Integer> filterList) {
        for (int i = filterList.size() - 1; i >= 0; i--) {
            int effectID = filterList.get(i);
            BaseFilter filter = mFilterDataStore.unSafe_getFilter(effectID, kFilterStoreID);
            if (filter != null) {
                FilterInfo filterInfo = filter.getFilterInfo();

                if (filterInfo.mFilterConfigs == null || filterInfo.mFilterConfigs.isEmpty()) {
                    continue;
                }

                Iterator<Map.Entry<Integer, BaseFilterParameter>> it = filterInfo.mFilterConfigs.entrySet().iterator();

                while (it.hasNext()) {
                    BaseFilterParameter param = it.next().getValue();

                    long startPtsMs = param.mStartPtsMs;
                    long endPtsMs = param.mEndPtsMs;
                    boolean visible = param.mVisible;
                    if (visible && isInEffectArea(startPtsMs, endPtsMs, pts)) {
                        return effectID;
                    }
                }
            }
        }
        return -1;
    }


    public List<Integer> getApplyEffectFilterIDs(long pts, List<Integer> filterList) {
        List<Integer> res = new ArrayList<>();
        for (int i = filterList.size() - 1; i >= 0; i--) {
            int effectID = filterList.get(i);
            BaseFilter filter = mFilterDataStore.unSafe_getFilter(effectID, kFilterStoreID);
            if (filter != null) {
                FilterInfo filterInfo = filter.getFilterInfo();

                if (filterInfo.mFilterConfigs == null || filterInfo.mFilterConfigs.isEmpty()) {
                    continue;
                }

                Iterator<Map.Entry<Integer, BaseFilterParameter>> it = filterInfo.mFilterConfigs.entrySet().iterator();

                while (it.hasNext()) {
                    BaseFilterParameter param = it.next().getValue();

                    long startPtsMs = param.mStartPtsMs;
                    long endPtsMs = param.mEndPtsMs;
                    boolean visible = param.mVisible;
                    if (visible && isInEffectArea(startPtsMs, endPtsMs, pts)) {
                        res.add(effectID);
                    }
                }
            }
        }
        return res;
    }
}
