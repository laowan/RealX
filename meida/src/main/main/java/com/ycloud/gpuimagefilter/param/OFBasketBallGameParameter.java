package com.ycloud.gpuimagefilter.param;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.filter.OFBasketBallGameFilter;
import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by wangyang on 2017/7/27.
 */

public class OFBasketBallGameParameter extends BaseFilterParameter {
    public String mBasketBallPathParam = null;
    public int  mInitScore = 0;
    public OFBasketBallGameFilter.BasketBallGameCallBack mCallBack= null;
    //framedata暂时不写入json文件，太大了，
    public OrangeFilter.OF_FrameData mFrameData = null;

    public OFBasketBallGameParameter() {
    }

    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_BASKETBALL_PATH_PARAM, mBasketBallPathParam);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] OFBasketBallGameParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mBasketBallPathParam = jsonObj.getString(FilterJSonKey.KEY_BASKETBALL_PATH_PARAM);
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mBasketBallPathParam =  ((OFBasketBallGameParameter)parameter).mBasketBallPathParam;
        mFrameData = ((OFBasketBallGameParameter)parameter).mFrameData;
        mCallBack = ((OFBasketBallGameParameter)parameter).mCallBack;
        mInitScore =  ((OFBasketBallGameParameter)parameter).mInitScore;
    }
}
