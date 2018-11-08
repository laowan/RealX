package com.ycloud.gpuimagefilter.utils;

import com.orangefilter.OrangeFilter;
import com.ycloud.api.common.FilterGroupType;
import com.ycloud.api.common.FilterType;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.ParamUtil;
import com.ycloud.utils.YYLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by Administrator on 2017/7/27.
 */
public class FilterInfo implements FilterDataInterface<FilterInfo> {
    /*filter对应的type类型*/
    public int mFilterType;
    /*filter对应的group type类型*/
    public String mFilterGroupType = FilterGroupType.DEFAULT_FILTER_GROUP;
    /*filter实例对应的id，全局为一*/
    public int mFilterID;
    /*filter实例对应的z-order*/
    public int mZOrder;
    /*filter实例对应的param参数中最大的param index*/
    public int mParamIndex = 0;

    /*filterInfo对应的sessionID，方便在FilterGroup线程中根据sessionID来设置FilterSession中的filter*/
    public int mSessionID;

    /*filterGroup线程透传上来的filter对应的orange filter信息*/
    public OrangeFilter.OF_EffectInfo mEffectInfo = new OrangeFilter.OF_EffectInfo();

    public TreeMap<Integer, BaseFilterParameter> mFilterConfigs = null;
    //public ArrayList<BaseFilterParameter> mFilterConfigs;

    public FilterInfo() {
        mFilterType = FilterType.NOFILTER;
    }

    public FilterInfo(int type, String groupType) {
        mFilterType = type;
        mFilterGroupType = groupType;
    }

    public int getParamIndex() {
        return  mParamIndex;
    }

    public void update(FilterInfo other) {
        mFilterType = other.mFilterType;
        mFilterGroupType = other.mFilterGroupType;
        mFilterID = other.mFilterID;
        mZOrder = other.mZOrder;
        mParamIndex = other.mParamIndex;

        if(other.mFilterConfigs == null || other.mFilterConfigs.isEmpty()) {
            this.mFilterConfigs = null;
            return;
        }

        if(this.mFilterConfigs == null  || this.mFilterConfigs.isEmpty()) {
            this.mFilterConfigs = other.mFilterConfigs;
            return;
        }

        try {
            HashMap<Integer, BaseFilterParameter> tmpMap =new HashMap<>();
            Iterator<Map.Entry<Integer, BaseFilterParameter>> thisIt = mFilterConfigs.entrySet().iterator();
            Iterator<Map.Entry<Integer, BaseFilterParameter>> otherIt = other.mFilterConfigs.entrySet().iterator();

            Map.Entry<Integer, BaseFilterParameter> thisEntry =  thisIt.next();
            Map.Entry<Integer, BaseFilterParameter> otherEntry = otherIt.next();
            while(thisEntry != null && otherEntry != null) {
                if(thisEntry.getKey() < otherEntry.getKey()) {
                    thisIt.remove();
                    thisEntry = thisIt.hasNext()? thisIt.next() : null;
                } else if(thisEntry.getKey() == otherEntry.getKey()) {
                    thisEntry.getValue().assign(otherEntry.getValue());
                    thisEntry = thisIt.hasNext()? thisIt.next() : null;
                    otherEntry = otherIt.hasNext()? otherIt.next() : null;
                } else {
                    //insert into thisIt.
                    tmpMap.put(otherEntry.getKey(), otherEntry.getValue());
                    otherEntry = otherIt.hasNext()? otherIt.next() : null;
                }
            }

            while(thisEntry != null) {
                thisIt.remove();
                thisEntry = thisIt.hasNext()? thisIt.next() : null;
            }

            this.mFilterConfigs.putAll(tmpMap);
            while(otherEntry != null) {
                this.mFilterConfigs.put(otherEntry.getKey(), otherEntry.getValue());
                otherEntry = otherIt.hasNext()? otherIt.next() : null;
            }
        } catch (Exception e) {
            YYLog.error(this, "FilterInfo update param exception:" + e.getMessage());
        }
    }

    public int addFilterParameter(BaseFilterParameter parameter) {
        if (parameter == null)
            return -1;

        if (mFilterConfigs == null) {
            mFilterConfigs = new TreeMap<Integer, BaseFilterParameter>();
        }

        mFilterConfigs.put(parameter.mParameterID, parameter.duplicate());

        mParamIndex++; //每次添加一个filter param, param index加1
        return parameter.mParameterID;
    }

    public BaseFilterParameter getFilterParameter(int paramId) {
        if(mFilterConfigs != null) {
            return  mFilterConfigs.get(paramId);
        }
        return  null;
    }

    public List<BaseFilterParameter> getFilterParameters() {
        if(mFilterConfigs != null) {
            Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterConfigs.entrySet().iterator();
            List<BaseFilterParameter> params = new ArrayList<>();
            while (it.hasNext()) {
                BaseFilterParameter param = it.next().getValue();
                params.add(param);
            }

            return  params;
        }
        return  null;
    }

    public int resetFilterParameter(BaseFilterParameter param) {
        //clear all other parameter.
        if (mFilterConfigs == null) {
            mFilterConfigs = new TreeMap<Integer, BaseFilterParameter>();
        }
        mFilterConfigs.clear();

        if (param == null)
            return -1;

        mFilterConfigs.put(param.mParameterID, param.duplicate());
        return param.mParameterID;
    }

    public void removeFilterParameter(int paramID) {
        if (mFilterConfigs != null) {
            mFilterConfigs.remove(paramID);
        }
    }

    public void removeFilterParameter() {
        if (mFilterConfigs != null) {
            mFilterConfigs.clear();
        }
        //TODO.set the default parameter?
    }

    public boolean modifyFilterParameter(int paramID, BaseFilterParameter param) {
        if (mFilterConfigs != null && mFilterConfigs.get(paramID) != null && param != null) {
            mFilterConfigs.get(paramID).assign(param);
            return true;
        }
        return false;
    }

    public boolean updateFilterParameter(int paramID, BaseFilterParameter param) {
        if (mFilterConfigs != null && mFilterConfigs.get(paramID) != null && param != null) {
            mFilterConfigs.get(paramID).update(param);
            return true;
        }
        return false;
    }

    public void modifyFilterZOrder(int zOrder) {
        mZOrder = zOrder;
    }

    public void marshall(JSONObject jsonObj) {
        try {
            jsonObj.put(FilterJSonKey.KEY_FILTER_TYPE, mFilterType);
            jsonObj.put(FilterJSonKey.KEY_FILTER_ID, mFilterID);
            jsonObj.put(FilterJSonKey.KEY_Z_ORDER, mZOrder);
            jsonObj.put(FilterJSonKey.KEY_PARAMETER_INDEX, mParamIndex);
            jsonObj.put(FilterJSonKey.KEY_FILTER_GROUP_TYPE, mFilterGroupType);
            if (mFilterConfigs != null) {
                JSONArray jArray = new JSONArray();
                Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterConfigs.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, BaseFilterParameter> entry = it.next();
                    JSONObject jObj = new JSONObject();
                    entry.getValue().marshall(jObj);
                    jArray.put(jObj);
                }
                jsonObj.put(FilterJSonKey.KEY_FILTER_CFG, jArray);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        mFilterType = jsonObj.getInt(FilterJSonKey.KEY_FILTER_TYPE);
        mFilterGroupType = jsonObj.getString(FilterJSonKey.KEY_FILTER_GROUP_TYPE);
        mFilterID = jsonObj.getInt(FilterJSonKey.KEY_FILTER_ID);
        mZOrder = jsonObj.getInt(FilterJSonKey.KEY_Z_ORDER);
        mParamIndex = jsonObj.getInt(FilterJSonKey.KEY_PARAMETER_INDEX);
        JSONArray jArray = jsonObj.getJSONArray(FilterJSonKey.KEY_FILTER_CFG);
        if (jArray != null) {
            mFilterConfigs = new TreeMap<Integer, BaseFilterParameter>();
            for (int i = 0; i < jArray.length(); i++) {
                JSONObject jobj = (JSONObject) jArray.get(i);
                BaseFilterParameter parameter = ParamUtil.newParameter(mFilterType);
                if (parameter != null) {
                    parameter.unmarshall(jobj);
                    mFilterConfigs.put(Integer.valueOf(parameter.mParameterID), parameter);
                }
            }
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来, 同时设置生效时长startTime～endTime
     */
    public void unmarshall(JSONObject jsonObj, long startTime, long endTime) throws JSONException {
        mFilterType = jsonObj.getInt(FilterJSonKey.KEY_FILTER_TYPE);
        mFilterGroupType = jsonObj.getString(FilterJSonKey.KEY_FILTER_GROUP_TYPE);
        mFilterID = jsonObj.getInt(FilterJSonKey.KEY_FILTER_ID);
        mZOrder = jsonObj.getInt(FilterJSonKey.KEY_Z_ORDER);
        mParamIndex = jsonObj.getInt(FilterJSonKey.KEY_PARAMETER_INDEX);
        JSONArray jArray = jsonObj.getJSONArray(FilterJSonKey.KEY_FILTER_CFG);
        if (jArray != null) {
            mFilterConfigs = new TreeMap<Integer, BaseFilterParameter>();
            for (int i = 0; i < jArray.length(); i++) {
                JSONObject jobj = (JSONObject) jArray.get(i);
                BaseFilterParameter parameter = ParamUtil.newParameter(mFilterType);
                if (parameter != null) {
                    parameter.unmarshall(jobj, startTime, endTime);
                    mFilterConfigs.put(Integer.valueOf(parameter.mParameterID), parameter);
                }
            }
        }
    }

    /**
     * deep-copy副本FilterInfo
     */
    public FilterInfo duplicateFilterInfo() {
        FilterInfo info = new FilterInfo();
        info.mFilterType = this.mFilterType;
        info.mFilterGroupType = this.mFilterGroupType;
        info.mFilterID = this.mFilterID;
        info.mSessionID = this.mSessionID;
        info.mZOrder = this.mZOrder;
        info.mParamIndex = this.mParamIndex;
        info.mFilterConfigs = new TreeMap<Integer, BaseFilterParameter>();

        if (this.mFilterConfigs != null && !this.mFilterConfigs.isEmpty()) {
            Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterConfigs.entrySet().iterator();
            while (it.hasNext()) {
                try {
                    Map.Entry<Integer, BaseFilterParameter> entry = it.next();
                    BaseFilterParameter other = ParamUtil.newParameter(mFilterType);
                    if (other != null) {
                        other.assign(entry.getValue());
                        info.mFilterConfigs.put(Integer.valueOf(other.mParameterID), other);
                    }
                } catch (Exception e) {
                    YYLog.error(this, "duplicateFilterInfo error, exception msg:" + e.getMessage());
                }
            }
        }

        info.mEffectInfo = duplicateOrangeFilterInfo();

        return info;
    }


    public OrangeFilter.OF_EffectInfo duplicateOrangeFilterInfo() {
        OrangeFilter.OF_EffectInfo ofEffectInfo = new OrangeFilter.OF_EffectInfo();
        ofEffectInfo.duration = this.mEffectInfo.duration;
        return ofEffectInfo;
    }

    /**
     * deep-copy副本 ArrayList<BaseFilterParameter>
     */
    public ArrayList<BaseFilterParameter> duplicateParameter() {
        ArrayList<BaseFilterParameter> params = null;
        if (this.mFilterConfigs != null && !this.mFilterConfigs.isEmpty()) {
            params = new ArrayList<>();

            Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterConfigs.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, BaseFilterParameter> entry = it.next();
                BaseFilterParameter other = ParamUtil.newParameter(mFilterType);
                if (other != null) {
                    other.assign(entry.getValue());
                    params.add(other);
                }
            }
        }

        return params;
    }

    @Override
    public boolean isDupable() {
        return true;
    }

    @Override
    public FilterInfo duplicate() {
        return duplicateFilterInfo();
    }

    //for log
    public String toString() {
        JSONObject jObj = new JSONObject();
        this.marshall(jObj);
        return jObj.toString();
    }
}
