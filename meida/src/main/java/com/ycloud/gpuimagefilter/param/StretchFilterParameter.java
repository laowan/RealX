package com.ycloud.gpuimagefilter.param;

import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jinyongqing on 2018/2/12.
 */

public class StretchFilterParameter extends BeautyFaceFilterParameter {
    public String mEffectPath = null;
    public float mLevel;

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mEffectPath = ((StretchFilterParameter) parameter).mEffectPath;
        mLevel = ((StretchFilterParameter) parameter).mLevel;
    }

    @Override
    public void marshall(JSONObject jsonObj) {
        super.marshall(jsonObj);
        try {
            jsonObj.put(FilterJSonKey.KEY_STRETCH_LEVEL, mLevel);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] StretchFilterParameter.marshall: " + e.toString());
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mLevel = (float) jsonObj.getDouble(FilterJSonKey.KEY_STRETCH_LEVEL);
    }
}
