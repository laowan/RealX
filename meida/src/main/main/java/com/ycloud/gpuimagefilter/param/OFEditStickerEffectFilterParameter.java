package com.ycloud.gpuimagefilter.param;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.filter.OFEditStickerEffectFilter;
import com.ycloud.gpuimagefilter.utils.FilterJSonKey;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.YYLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by wangyang on 2017/8/17.
 */

public class OFEditStickerEffectFilterParameter extends BaseFilterParameter {


    //贴纸种类
    public static final int TYPE_NORMAL_STICKER = 0;    //普通贴纸
    public static final int TYPE_PARTICLE_STICKER = 1;  //粒子特效贴纸

    public String mEffectDirectory = null;
    public float[] mCameraArray;
    public OrangeFilter.OF_Transform mTransFormArray;

    public float mTranslateX = 0;
    public float mTranslateY = 0;
    public float mRotation = 0;
    public float mScale = 1.0f;

    public float mColorR, mColorG, mColorB;
    public float mLeft = 0.0F;
    public float mTop = 0.0F;
    public float mWidth = 128.0F;
    public float mHeight = 128.0F;

    public long mFadeoutStartPtsMs = 0;
    public boolean mUseFadeout = false;
    public int mStickerType = TYPE_NORMAL_STICKER;

    public int mTrackerConfigFlag = 1;

    public OrangeFilter.OF_FrameData mFrameData = null;
    public boolean mPresetDataArray = false;
    // 贴纸每帧的位置,of旧的接口通过客户端传入x,y坐标来改变矩阵
    public List<OFEditStickerEffectFilter.FrameTracedDataInfo> mTracedDataInfoList = new ArrayList<>();
    private int mTracedDataInfoListLen = 0;

    //pts对应的uiConf的list，of新的接口一致通过uiConf来接收客户端的改变参数信息，包括x,y坐标，颜色等等
    public List<OFEditStickerEffectFilter.FrameUIConfInfo> mUIConfInfoList = new ArrayList<>();

    public String[] mTexts = null;
    private int mTextsLen = 0;

    public boolean mNeedRepeatRender = false;

    //贴纸相对背景图片（视频帧或者图片数据）的比例
    public float mRatio2background = 0;

    public OFEditStickerEffectFilterParameter() {
    }

    private synchronized void marshallTracedDataList(JSONObject jsonObj) {
        try {
            mTracedDataInfoListLen = mTracedDataInfoList.size();
            for (int i = 0; i < mTracedDataInfoListLen; i++) {
                jsonObj.put("timestampMs" + i, mTracedDataInfoList.get(i).timestampMs);
                jsonObj.put("translateX" + i, mTracedDataInfoList.get(i).translateX);
                jsonObj.put("translateY" + i, mTracedDataInfoList.get(i).translateY);
                jsonObj.put("rotation" + i, mTracedDataInfoList.get(i).rotation);
                jsonObj.put("scale" + i, mTracedDataInfoList.get(i).scale);
                jsonObj.put("x" + i, mTracedDataInfoList.get(i).x);
                jsonObj.put("y" + i, mTracedDataInfoList.get(i).y);
            }

            jsonObj.put("mTracedDataListLen", mTracedDataInfoList.size());
        } catch (JSONException e) {
            YYLog.error(this, "[exception] PressedEffectFilterParameter.marshallTracedDataList: " + e.toString());
            e.printStackTrace();
        }
    }

    private synchronized void unmarshallTracedDataList(JSONObject jsonObj) {
        try {
            mTracedDataInfoListLen = jsonObj.getInt("mTracedDataListLen");
            mTracedDataInfoList.clear();
            for (int i = 0; i < mTracedDataInfoListLen; i++) {
                long timestampMs = jsonObj.getLong("timestampMs" + i);
                float translateX = (float) jsonObj.getDouble("translateX" + i);
                float translateY = (float) jsonObj.getDouble("translateY" + i);
                float rotation = (float) jsonObj.getDouble("rotation" + i);
                float scale = (float) jsonObj.getDouble("scale" + i);
                float x = (float) jsonObj.getDouble("x" + i);
                float y = (float) jsonObj.getDouble("y" + i);


                OFEditStickerEffectFilter.FrameTracedDataInfo frameTracedDataInfo = new OFEditStickerEffectFilter.FrameTracedDataInfo(timestampMs,
                        translateX, translateY, rotation, scale, x, y);
                mTracedDataInfoList.add(frameTracedDataInfo);
            }
        } catch (JSONException e) {
            YYLog.error(this, "[exception] PressedEffectFilterParameter.unmarshallTracedDataList: " + e.toString());
            e.printStackTrace();
        }
    }

    @Override
    public void marshall(JSONObject jsonObj) {
        try {
            super.marshall(jsonObj);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_DIRECTORY, mEffectDirectory);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_CAMERA_ARRAY, mCameraArray);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_TRANSX, mTranslateX);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_TRANSY, mTranslateY);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_SCALE, mScale);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_ROTATION, mRotation);

            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_RECTLEFT, mLeft);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_RECTTOP, mTop);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_RECTWIDTH, mWidth);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_EFFECT_RECTHEIGHT, mHeight);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_TRACKER_CONFIG_FLAG, mTrackerConfigFlag);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_USE_FADEOUT, mUseFadeout);

            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_FADEOUT_START_TIME, mFadeoutStartPtsMs);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_PARTICLE_COLOR_R, mColorR);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_PARTICLE_COLOR_G, mColorG);
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_PARTICLE_COLOR_B, mColorB);

            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_RATION2BACKGROUND, mRatio2background);

            marshallTracedDataList(jsonObj);

            //序列化FrameUIConf list
            jsonObj.put(FilterJSonKey.KEY_EDITSTICKER_UICONF_LIST, new Gson().toJson(mUIConfInfoList));

            if (mTexts != null) {
                mTextsLen = mTexts.length;
                for (int i = 0; i < mTextsLen; i++) {
                    jsonObj.put("mTexts" + i, mTexts[i]);
                }
            }
            jsonObj.put("mTextsLen", mTextsLen);
        } catch (Exception e) {
            YYLog.error(this, "[exception] PressedEffectFilterParameter.marshall: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * filter实例的parmater信息从json字符串中反序列化来
     */
    public void unmarshall(JSONObject jsonObj) throws JSONException {
        super.unmarshall(jsonObj);

        mPresetDataArray = true;
        mEffectDirectory = jsonObj.getString(FilterJSonKey.KEY_EDITSTICKER_EFFECT_DIRECTORY);

        mTranslateX = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_EFFECT_TRANSX);
        mTranslateY = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_EFFECT_TRANSY);
        mRotation = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_EFFECT_ROTATION);
        mScale = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_EFFECT_SCALE);

        mLeft = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_EFFECT_RECTLEFT);
        mTop = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_EFFECT_RECTTOP);
        mWidth = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_EFFECT_RECTWIDTH);
        mHeight = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_EFFECT_RECTHEIGHT);
        mTrackerConfigFlag = jsonObj.getInt(FilterJSonKey.KEY_EDITSTICKER_TRACKER_CONFIG_FLAG);
        mFadeoutStartPtsMs = jsonObj.getLong(FilterJSonKey.KEY_EDITSTICKER_FADEOUT_START_TIME);
        mUseFadeout = jsonObj.getBoolean(FilterJSonKey.KEY_EDITSTICKER_USE_FADEOUT);
        mColorR = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_PARTICLE_COLOR_R);
        mColorG = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_PARTICLE_COLOR_G);
        mColorB = (float) jsonObj.getDouble(FilterJSonKey.KEY_EDITSTICKER_PARTICLE_COLOR_B);

        mRatio2background = (float) jsonObj.optDouble(FilterJSonKey.KEY_EDITSTICKER_RATION2BACKGROUND, 0);

        unmarshallTracedDataList(jsonObj);

        //反序列化FrameUIConfInfo list
        Type listType = new TypeToken<ArrayList<OFEditStickerEffectFilter.FrameUIConfInfo>>() {}.getType();
        mUIConfInfoList = new Gson().fromJson(jsonObj.getString(FilterJSonKey.KEY_EDITSTICKER_UICONF_LIST), listType);

        mTextsLen = jsonObj.getInt("mTextsLen");
        if (mTextsLen > 0) {
            mTexts = new String[mTextsLen];
            for (int i = 0; i < mTextsLen; i++) {
                mTexts[i] = jsonObj.getString("mTexts" + i);
            }
        }
    }

    @Override
    public void assign(BaseFilterParameter parameter) {
        super.assign(parameter);
        OFEditStickerEffectFilterParameter editParam = (OFEditStickerEffectFilterParameter) parameter;
        mEffectDirectory = editParam.mEffectDirectory;
        mFrameData = editParam.mFrameData;
        mCameraArray = editParam.mCameraArray;
        mTranslateX = editParam.mTranslateX;
        mTranslateY = editParam.mTranslateY;
        mRotation = editParam.mRotation;
        mScale = editParam.mScale;
        mLeft = editParam.mLeft;
        mTop = editParam.mTop;
        mWidth = editParam.mWidth;
        mHeight = editParam.mHeight;
        mParameterID = editParam.mParameterID;
        mTrackerConfigFlag = editParam.mTrackerConfigFlag;
        mFadeoutStartPtsMs = editParam.mFadeoutStartPtsMs;
        mUseFadeout = editParam.mUseFadeout;
        mStickerType = editParam.mStickerType;
        mColorR = editParam.mColorR;
        mColorG = editParam.mColorG;
        mColorB = editParam.mColorB;

        mRatio2background = editParam.mRatio2background;

        mTracedDataInfoList = editParam.mTracedDataInfoList;

        if (editParam.mTexts != null && editParam.mTexts.length > 0) {
            mTextsLen = editParam.mTexts.length;
            mTexts = new String[mTextsLen];
            for (int i = 0; i < mTextsLen; i++) {
                mTexts[i] = editParam.mTexts[i];
            }
        }
        mNeedRepeatRender = editParam.mNeedRepeatRender;
        mUIConfInfoList = editParam.mUIConfInfoList;
    }

    @Override
    public void update(BaseFilterParameter parameter) {
        OFEditStickerEffectFilterParameter editParam = (OFEditStickerEffectFilterParameter) parameter;
        mUIConfInfoList = editParam.mUIConfInfoList;
    }

    @Override
    public void updateWithConf(Map.Entry<Integer, Object> conf) {
        super.updateWithConf(conf);
        switch (conf.getKey()) {
            case FilterOPType.OP_SET_EFFECT_PATH:
                mEffectDirectory = (String) conf.getValue();
                break;
            case FilterOPType.OP_CHANGE_TIME:
                long[] times = (long[]) conf.getValue();
                mStartPtsMs = times[0];
                mEndPtsMs = times[1];
                break;
            case FilterOPType.OP_SET_REPEAT_RENDER:
                mNeedRepeatRender = (boolean) conf.getValue();
                break;
            case FilterOPType.OP_CHANGE_MATRIX:
                float[] coordinate = (float[]) conf.getValue();
                mTranslateX = coordinate[0];
                mTranslateY = coordinate[1];
                break;
            case FilterOPType.OP_CHANGE_SCALE:
                mScale = (float) conf.getValue();
                break;
            case FilterOPType.OP_CHANGE_ROTATION:
                mRotation = (float) conf.getValue();
                break;
            case FilterOPType.OP_CHANGE_RATIO_TO_BACKGROUND:
                mRatio2background = (float) conf.getValue();
                break;
            case FilterOPType.OP_ADD_TEXT:
                mTexts = (String[]) conf.getValue();
                break;
        }
    }
}
