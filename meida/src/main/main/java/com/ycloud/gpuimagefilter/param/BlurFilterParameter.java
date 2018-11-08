package com.ycloud.gpuimagefilter.param;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jinyongqing on 2018/4/22.
 */

public class BlurFilterParameter extends BaseFilterParameter {
    public String mEffectPath = null;
    public float mRatio = 0;

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mEffectPath = ((BlurFilterParameter) parameter).mEffectPath;
        mRatio = ((BlurFilterParameter) parameter).mRatio;
    }

    @Override
    public void marshall(JSONObject jsonObj) {
        super.marshall(jsonObj);
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
    }
}
