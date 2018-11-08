package com.ycloud.gpuimagefilter.param;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * Created by liuchunyu on 2017/7/27.
 */

public class EffectFilterParameter extends BaseFilterParameter {
    public String mEffectParam = null;
    //framedata暂时不写入json文件，太大了，
    public OrangeFilter.OF_FrameData mFrameData = null;

    public int mSurportSeeking;
    public int mStartRecordFlag = -1; // 录制模块告诉filter要启动录制，此参数不写入json
    public int mSeekTimeOffset = 0;

    public EffectFilterParameter() {
    }

    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_EFFECT_PARAM, mEffectParam);
            jsonObj.put(FilterJSonKey.KEY_SURPORT_SEEKING, mSurportSeeking);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] EffectFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mEffectParam = jsonObj.getString(FilterJSonKey.KEY_EFFECT_PARAM);
        mSurportSeeking = jsonObj.getInt(FilterJSonKey.KEY_SURPORT_SEEKING);
        mSeekTimeOffset = jsonObj.getInt(FilterJSonKey.KEY_SEEK_TIME_OFFSET);
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mEffectParam =  ((EffectFilterParameter)parameter).mEffectParam;
        mFrameData = ((EffectFilterParameter)parameter).mFrameData;
        mSurportSeeking = ((EffectFilterParameter)parameter).mSurportSeeking;
        mStartRecordFlag = ((EffectFilterParameter)parameter).mStartRecordFlag;
        mSeekTimeOffset = ((EffectFilterParameter)parameter).mSeekTimeOffset;
    }

    @Override
    public void updateWithConf(Map.Entry<Integer, Object> conf) {
        super.updateWithConf(conf);
        switch (conf.getKey()) {
            case FilterOPType.OP_SET_EFFECT_PATH:
                mEffectParam = (String) conf.getValue();
                break;
            case FilterOPType.OP_SET_SUPPORT_SEEK:
                mSurportSeeking = (int) conf.getValue();
                break;
            case FilterOPType.OP_SET_SEEK_TIME_OFFSET:
                mSeekTimeOffset = (int) conf.getValue();
                break;
            default:
                break;
        }
    }

}
