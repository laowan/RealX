package com.ycloud.gpuimagefilter.param;

import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * Created by liuchunyu on 2017/7/27.
 */

public class BeautyFaceFilterParameter extends BaseFilterParameter {
    public float mBeautyFaceParam = -1;

    public BeautyFaceFilterParameter() {
    }

    /**
     * filte Parameter信息序列化为json字符串
     */
    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_BEAUTY_FACE_PARAM, mBeautyFaceParam);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] BeautyFaceFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    @Override
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mBeautyFaceParam = (float)jsonObj.getDouble(FilterJSonKey.KEY_BEAUTY_FACE_PARAM);
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mBeautyFaceParam = ((BeautyFaceFilterParameter) parameter).mBeautyFaceParam;
        YYLog.debug(this, "BeautyFaceFilterParameter assgine, mBeautyFaceParam:" + mBeautyFaceParam);
    }

    @Override
    public void updateWithConf(Map.Entry<Integer, Object> conf) {
        super.updateWithConf(conf);
        switch (conf.getKey()) {
            case FilterOPType.OP_CHANGE_INTENSITY:
                mBeautyFaceParam = (float)conf.getValue();
                break;
            default:
                break;
        }
    }
}
