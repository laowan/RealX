package com.ycloud.gpuimagefilter.param;

import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jinyongqing on 2017/12/6.
 */

public class PuzzleFilterParameter extends BaseFilterParameter {
    public String mPuzzleDirectory = null;
    public int mParameterIndex = -1; //param添加到filter中的顺序

    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_PARAM_INDEX, mParameterIndex);
            jsonObj.put(FilterJSonKey.KEY_PARAM_PUZZLE_DIR, mPuzzleDirectory);
        } catch (JSONException e) {
            YYLog.error(this, "[exception] PuzzleFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    @Override
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);
        mParameterIndex = jsonObj.getInt(FilterJSonKey.KEY_PARAM_INDEX);
        mPuzzleDirectory = jsonObj.getString(FilterJSonKey.KEY_PARAM_PUZZLE_DIR);
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        mParameterIndex = ((PuzzleFilterParameter) parameter).mParameterIndex;
        mPuzzleDirectory = ((PuzzleFilterParameter) parameter).mPuzzleDirectory;
        YYLog.info(this, "PuzzleFilterParameter assgine, mStartPtsMs:" + mStartPtsMs +
                ",mLastPtsMs:" + mLastPtsMs + ",mPuzzleDirectory" + mPuzzleDirectory);
    }
}
