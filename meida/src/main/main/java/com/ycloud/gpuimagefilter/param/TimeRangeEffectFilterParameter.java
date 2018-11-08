package com.ycloud.gpuimagefilter.param;

import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.utils.YYLog;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jinyongqing on 2017/8/18.
 */

public class TimeRangeEffectFilterParameter extends  BaseFilterParameter {
    public String mEffectDirectory = null;
    public boolean mNeedRepeatRender = false;

    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_EFFECT_DIR, mEffectDirectory);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] TimeRangeEffectFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    @Override
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mEffectDirectory = jsonObj.getString(FilterJSonKey.KEY_EFFECT_DIR);
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mEffectDirectory = ((TimeRangeEffectFilterParameter)parameter).mEffectDirectory;
        mNeedRepeatRender = ((TimeRangeEffectFilterParameter)parameter).mNeedRepeatRender;
//        YYLog.info(this, "TimeRangeEffectFilterParameter assgine, mStartPtsMs:" + mStartPtsMs +
//                ",mLastPtsMs:" + mLastPtsMs + ",mEffectDirectory" + mEffectDirectory);
    }
}
