package com.ycloud.gpuimagefilter.param;

import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by liuchunyu on 2017/7/27.
 */

public class ColorTableFilterParameter extends BaseFilterParameter {
    public String mColorTableParam = null;

    public ColorTableFilterParameter() {
    }

    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_COLOR_TABLE_PARAM, mColorTableParam);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] ColorTableFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mColorTableParam = jsonObj.getString(FilterJSonKey.KEY_COLOR_TABLE_PARAM);
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mColorTableParam = ((ColorTableFilterParameter)parameter).mColorTableParam;
    }
}
