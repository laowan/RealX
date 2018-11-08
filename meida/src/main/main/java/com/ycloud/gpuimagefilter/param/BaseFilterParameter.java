package com.ycloud.gpuimagefilter.param;

import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2017/7/27.
 */

public class BaseFilterParameter {
    public static final String TAG = "BaseFilterParameter";
    /*param ID*/
    public int mParameterID = -1;
    /*param起始作用时间*/
    public long mStartPtsMs = -1;
    /*param结束生效时间*/
    public long mEndPtsMs = -1;  //-1 means to end of video
    /*param持续时长*/
    public long mLastPtsMs = -1;
    /*是否可见*/
    public boolean mVisible = true;
    /*param的本次修改类型，比如SET_TIME,SET_PATH*/
    public int mOPType = 0;
    /*param在一次session里的修改集合*/
    public int mOPTypeSave = 0;
    /*支持任意类型的of filter从客户端传递config到of*/
    public Map<String, Object> mUIConf = new HashMap<>();

    public BaseFilterParameter() {
    }

    /**
     * filte Parameter信息序列化为json字符串
     */
    public void marshall(JSONObject jsonObj) {
        try {
            jsonObj.put(FilterJSonKey.KEY_START_PTS, mStartPtsMs);
            jsonObj.put(FilterJSonKey.KEY_END_PTS, mEndPtsMs);
            jsonObj.put(FilterJSonKey.KEY_LAST_PTS, mLastPtsMs);
            jsonObj.put(FilterJSonKey.KEY_PARAMETER_ID, mParameterID);
            jsonObj.put(FilterJSonKey.KEY_OP_TYPE, mOPTypeSave);

            marshallUIConfMap(jsonObj);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void marshallUIConfMap(JSONObject jsonObject) {
        try {
            jsonObject.put(FilterJSonKey.KEY_UICONF_LENGTH, mUIConf.size());
            int i = 0;
            for (Map.Entry<String, Object> entry : mUIConf.entrySet()) {
                jsonObject.put(FilterJSonKey.KEY_UICONF_KEY + i, entry.getKey());
                jsonObject.put(FilterJSonKey.KEY_UICONF_VALUE + i, entry.getValue());
                i++;
            }
        } catch (JSONException e) {
            YYLog.error(TAG, "marshallUIConfMap error:" + e.getMessage());
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        mStartPtsMs = jsonObj.getLong(FilterJSonKey.KEY_START_PTS);
        mEndPtsMs = jsonObj.getLong(FilterJSonKey.KEY_END_PTS);
        mLastPtsMs = jsonObj.getLong(FilterJSonKey.KEY_LAST_PTS);
        mParameterID = jsonObj.getInt(FilterJSonKey.KEY_PARAMETER_ID);
        mOPType = jsonObj.getInt(FilterJSonKey.KEY_OP_TYPE);
        mOPTypeSave = mOPType;

        unmarshallUIConfMap(jsonObj);
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来, 同时设置生效时长startTime～endTime
     */
    public void unmarshall(JSONObject jsonObj, long startTime, long endTime) throws JSONException {
        //mStartPtsMs = jsonObj.getLong(FilterJSonKey.KEY_START_PTS);
        //mEndPtsMs = jsonObj.getLong(FilterJSonKey.KEY_END_PTS);
        mStartPtsMs = startTime;
        mEndPtsMs = endTime;
        mParameterID = jsonObj.getInt(FilterJSonKey.KEY_PARAMETER_ID);
        mOPType = jsonObj.getInt(FilterJSonKey.KEY_OP_TYPE);

        unmarshallUIConfMap(jsonObj);
    }

    private void unmarshallUIConfMap(JSONObject jsonObject) {
        try {
            mUIConf.clear();
            int lenConf = jsonObject.getInt(FilterJSonKey.KEY_UICONF_LENGTH);
            for (int i = 0; i < lenConf; i++) {
                String confKey = jsonObject.getString(FilterJSonKey.KEY_UICONF_KEY + i);
                Object value = jsonObject.get(FilterJSonKey.KEY_UICONF_VALUE + i);
                mUIConf.put(confKey, value);
            }
        } catch (JSONException e) {
            YYLog.error(TAG, "unmarshallUIConfMap error:" + e.getMessage());
        }
    }

    /**
     * deep-copy副本
     */
    public void assign(BaseFilterParameter parameter) {
        mParameterID = parameter.mParameterID;
        mStartPtsMs = parameter.mStartPtsMs;
        mEndPtsMs = parameter.mEndPtsMs;
        mLastPtsMs = parameter.mLastPtsMs;
        mVisible = parameter.mVisible;
        mOPType = parameter.mOPType;
        mOPTypeSave = parameter.mOPTypeSave;

        mUIConf = new HashMap<>(parameter.mUIConf);

    }

    /**
     * 更新param中的部分变量，在子类中实现，以指定具体更新哪些变量
     */
    public void update(BaseFilterParameter parameter) {

    }

    /**
     * 根据业务的配置更新param的变量。通用配置（uiConf等）在父类实现，非通用配置（具体param相关）在子类实现
     *
     * @param conf key:更新的配置类型，value：配置的具体值
     */
    public void updateWithConf(Map.Entry<Integer, Object> conf) {
        mOPType |= conf.getKey();

        switch (conf.getKey()) {
            case FilterOPType.OP_SET_UICONFIG:
            case FilterOPType.OP_SET_UICONFIG_NOT_TRACE:
                mUIConf = (Map) conf.getValue();
                break;
        }
    }

    public BaseFilterParameter duplicate() {
        Class c = null;
        try {
            c = Class.forName(this.getClass().getCanonicalName());
            try {
                BaseFilterParameter parameter = (BaseFilterParameter) c.newInstance();
                parameter.assign(this);
                YYLog.debug(this, "BaseFilterParameter duplicated................");
                return parameter;
            } catch (InstantiationException e) {
                e.printStackTrace();
                YYLog.error(this, "[exception] occur: " + e.toString());
                return null;
            } catch (IllegalAccessException e) {
                YYLog.error(this, "[exception] occur: " + e.toString());
                e.printStackTrace();
                return null;
            }
        } catch (ClassNotFoundException e) {
            YYLog.error(this, "[exception] occur: " + e.toString());
            e.printStackTrace();
            return null;
        }
    }

    public String toString() {
        JSONObject jsonObject = new JSONObject();
        this.marshall(jsonObject);
        return jsonObject.toString();
    }
}
