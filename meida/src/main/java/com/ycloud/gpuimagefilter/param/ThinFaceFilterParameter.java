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

public class ThinFaceFilterParameter extends BaseFilterParameter {
    public float mThinFaceParam = -1;
    public OrangeFilter.OF_FrameData mFrameData = null;

    public ThinFaceFilterParameter() {
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mThinFaceParam = ((ThinFaceFilterParameter)parameter).mThinFaceParam;
    }

    @Override
    public void marshall(JSONObject jsonObj) {
        super.marshall(jsonObj);
        try {
            jsonObj.put(FilterJSonKey.KEY_THIN_FACE_PARAM, mThinFaceParam);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] ThinFaceFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mThinFaceParam = (float)jsonObj.getDouble(FilterJSonKey.KEY_THIN_FACE_PARAM);
    }

    @Override
    public void updateWithConf(Map.Entry<Integer, Object> conf) {
        super.updateWithConf(conf);
        switch (conf.getKey()) {
            case FilterOPType.OP_CHANGE_INTENSITY:
                mThinFaceParam = (float)conf.getValue();
                break;
            default:
                break;
        }
    }
}
