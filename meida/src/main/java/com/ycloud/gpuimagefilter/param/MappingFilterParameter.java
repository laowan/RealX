package com.ycloud.gpuimagefilter.param;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by liuchunyu on 2017/7/27.
 */

public class MappingFilterParameter extends BaseFilterParameter {

    public MappingFilterParameter() {
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
