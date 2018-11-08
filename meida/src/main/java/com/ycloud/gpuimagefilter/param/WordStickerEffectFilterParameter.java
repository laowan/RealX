package com.ycloud.gpuimagefilter.param;

import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by liuchunyu on 2017/9/4.
 */

public class WordStickerEffectFilterParameter extends BaseFilterParameter {
    public String mImagePath;
    public int mOriginalX;
    public int mOriginalY;
    public double mStartTime;
    public double mEndTime;

    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_WORDSTICKER_EFFECT_PATH, mImagePath);
            jsonObj.put(FilterJSonKey.KEY_WORDSTICKER_ORIGN_X, mOriginalX);
            jsonObj.put(FilterJSonKey.KEY_WORDSTICKER_ORIGN_Y, mOriginalY);
            jsonObj.put(FilterJSonKey.KEY_WORDSTICKER_START_TIME, mStartTime);
            jsonObj.put(FilterJSonKey.KEY_WORDSTICKER_END_TIME, mEndTime);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] TimeRangeEffectFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    @Override
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mImagePath = jsonObj.getString(FilterJSonKey.KEY_WORDSTICKER_EFFECT_PATH);
        mOriginalX = jsonObj.getInt(FilterJSonKey.KEY_WORDSTICKER_ORIGN_X);
        mOriginalY = jsonObj.getInt(FilterJSonKey.KEY_WORDSTICKER_ORIGN_Y);
        mStartTime = jsonObj.getDouble(FilterJSonKey.KEY_WORDSTICKER_START_TIME);
        mEndTime = jsonObj.getDouble(FilterJSonKey.KEY_WORDSTICKER_END_TIME);
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mImagePath = ((WordStickerEffectFilterParameter) parameter).mImagePath;
        mOriginalX = ((WordStickerEffectFilterParameter) parameter).mOriginalX;
        mOriginalY = ((WordStickerEffectFilterParameter) parameter).mOriginalY;
        mStartTime = ((WordStickerEffectFilterParameter) parameter).mStartTime;
        mEndTime = ((WordStickerEffectFilterParameter) parameter).mEndTime;
    }
}
