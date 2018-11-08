package com.ycloud.gpuimagefilter.param;

import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by liuchunyu on 2017/8/12.
 */

public class FadeBlendFilterParameter extends BaseFilterParameter {
    public float mTweenFactor = 0.5f; // 默认参数暂时

    public FadeBlendFilterParameter() {
    }

    /**
     * filte Parameter信息序列化为json字符串
     */
    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put("mTweenFactor", mTweenFactor);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] FadeBlendFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    @Override
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mTweenFactor = (float)jsonObj.getDouble("mBeautyFaceParam");
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mTweenFactor = ((FadeBlendFilterParameter) parameter).mTweenFactor;
        YYLog.info(this, "FadeBlendFilterParameter assgine, mTweenFactor:" + mTweenFactor);
    }
}
