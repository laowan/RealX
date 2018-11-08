package com.ycloud.gpuimagefilter.param;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.filter.OFGameFilter;
import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jinyongqing on 2018/1/23.
 */

public class OFGameParameter extends BaseFilterParameter {
    public String mGamePath = null;
    public String mEventJson = null;
    public int mOPType = 0;
    public OFGameFilter.GameEventCallBack mCallBack = null;
    public OrangeFilter.OF_FrameData mFrameData = null;

    public OFGameParameter() {
    }

    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_GAME_PATH_PARAM, mGamePath);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] OFBasketBallGameParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mGamePath = jsonObj.getString(FilterJSonKey.KEY_GAME_PATH_PARAM);
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mGamePath = ((OFGameParameter) parameter).mGamePath;
        mFrameData = ((OFGameParameter) parameter).mFrameData;
        mOPType = ((OFGameParameter) parameter).mOPType;
        mCallBack = ((OFGameParameter) parameter).mCallBack;
        mEventJson = ((OFGameParameter)parameter).mEventJson;
    }
}
