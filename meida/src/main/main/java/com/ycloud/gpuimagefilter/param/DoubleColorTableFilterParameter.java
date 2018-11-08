package com.ycloud.gpuimagefilter.param;

import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * Created by liuchunyu on 2017/8/1.
 */

public class DoubleColorTableFilterParameter extends BaseFilterParameter {
    public String mColorTableParam1 = null;
    public String mColorTableParam2 = null;
    public float mRatio = 0.0f;
    public boolean mIsVertical = true;

    public DoubleColorTableFilterParameter() {
    }

    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_COLOR_TABLE_PARAM1, mColorTableParam1);
            jsonObj.put(FilterJSonKey.KEY_COLOR_TABLE_PARAM2, mColorTableParam2);
            jsonObj.put(FilterJSonKey.KEY_RATIO, mRatio);
            jsonObj.put(FilterJSonKey.KEY_IS_VERTICAL, mIsVertical);

        } catch (JSONException e) {
            YYLog.error(this, "[exception] DoubleColorTableFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        if (!jsonObj.isNull(FilterJSonKey.KEY_COLOR_TABLE_PARAM1)) {
            mColorTableParam1 = jsonObj.getString(FilterJSonKey.KEY_COLOR_TABLE_PARAM1);
        }
        if (!jsonObj.isNull(FilterJSonKey.KEY_COLOR_TABLE_PARAM2)) {
            mColorTableParam2 = jsonObj.getString(FilterJSonKey.KEY_COLOR_TABLE_PARAM2);
        }
        mRatio = (float) jsonObj.getDouble(FilterJSonKey.KEY_RATIO);
        mIsVertical = jsonObj.getBoolean(FilterJSonKey.KEY_IS_VERTICAL);
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mColorTableParam1 = ((DoubleColorTableFilterParameter) parameter).mColorTableParam1;
        mColorTableParam2 = ((DoubleColorTableFilterParameter) parameter).mColorTableParam2;
        mRatio = ((DoubleColorTableFilterParameter) parameter).mRatio;
        mIsVertical = ((DoubleColorTableFilterParameter) parameter).mIsVertical;
    }

    @Override
    public void updateWithConf(Map.Entry<Integer, Object> conf) {
        super.updateWithConf(conf);
        switch (conf.getKey()) {
            case FilterOPType.OP_SET_EFFECT_PATH:
                String[] doubleColorPaths = (String[]) conf.getValue();
                mColorTableParam1 = doubleColorPaths[0];
                mColorTableParam2 = doubleColorPaths[1];
                break;
            case FilterOPType.OP_CHANGE_RATIO:
                mRatio = (float) conf.getValue();
                break;
            case FilterOPType.OP_CHANGE_WITH_VERTICAL:
                mIsVertical = (boolean) conf.getValue();
                break;
        }
    }
}
