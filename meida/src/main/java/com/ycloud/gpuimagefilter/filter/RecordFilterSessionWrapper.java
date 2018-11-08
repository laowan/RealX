package com.ycloud.gpuimagefilter.filter;

import com.ycloud.api.common.FilterGroupType;
import com.ycloud.api.common.FilterType;
import com.ycloud.common.Constant;
import com.ycloud.facedetection.STMobileFaceDetectionWrapper;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.BeautyFaceFilterParameter;
import com.ycloud.gpuimagefilter.param.DoubleColorTableFilterParameter;
import com.ycloud.gpuimagefilter.param.EffectFilterParameter;
import com.ycloud.gpuimagefilter.param.OFBasketBallGameParameter;
import com.ycloud.gpuimagefilter.param.OFEditStickerEffectFilterParameter;
import com.ycloud.gpuimagefilter.param.OFGameParameter;
import com.ycloud.gpuimagefilter.param.ParamUtil;
import com.ycloud.gpuimagefilter.param.StretchFilterParameter;
import com.ycloud.gpuimagefilter.param.ThinFaceFilterParameter;
import com.ycloud.gpuimagefilter.utils.FilterConfigParse;
import com.ycloud.gpuimagefilter.utils.FilterIDManager;
import com.ycloud.gpuimagefilter.utils.FilterLayout;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.gpuimagefilter.utils.GameFilterCmdType;
import com.ycloud.mediafilters.MediaFilterContext;
import com.ycloud.mediarecord.RecordConfig;
import com.ycloud.mediarecord.audio.AudioVoiceChangerToolbox;
import com.ycloud.utils.YYLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by liuchunyu on 2017/8/16.
 */

public class RecordFilterSessionWrapper {
    private String TAG = "RecordFilterSessionWrapper";

    private FilterSession mFilterSession;
    private ArrayList<Integer> mEditFilterTypes;

    private int mBeautyFilterID = FilterIDManager.NO_ID;
    private BeautyFaceFilterParameter mBeautyFilterParam;

    private int mThinFaceFilterID = FilterIDManager.NO_ID;
    private ThinFaceFilterParameter mThinFaceFilterParam;

    private int mDoubleColorTableFilterID = FilterIDManager.NO_ID;
    private DoubleColorTableFilterParameter mDoubleColorTableFilterParam;

    private int mEffectFilterID = FilterIDManager.NO_ID;
    private EffectFilterParameter mEffectFilterParam;

    //篮球
    private int mBasketBallFilterID = FilterIDManager.NO_ID;
    private OFBasketBallGameParameter mOFBasketBallGameParameter;

    private MediaFilterContext mVideoFilterContext = null;
    private  RecordConfig mRecordConfig = null;

    private RecordFilterGroup mRecordFilterGroup = null;

    public RecordFilterSessionWrapper(RecordConfig recordConfig, MediaFilterContext mediaFilterContext) {
        mFilterSession = FilterCenter.getInstance().createFilterSession();
        mRecordConfig = recordConfig;
        mVideoFilterContext = mediaFilterContext;
    }

    public int getSessionID() {
        return mFilterSession.getSessionID();
    }

    public void setEditFilterTypes(ArrayList<Integer> filterTypes) {
        mEditFilterTypes = filterTypes;
    }

    private int getZOrderIDByFilterType(int filterType) {
        int zOrderID = FilterLayout.generateZOrderID();

        if (mEditFilterTypes != null && mEditFilterTypes.size() != 0) {
            for (int i = 0; i < mEditFilterTypes.size(); i++) {
                if (filterType == mEditFilterTypes.get(i)) {
                    zOrderID = FilterLayout.resetPathFlag(zOrderID, FilterLayout.kEncodePathFlag);
                    YYLog.info(TAG, "getZOrderIDByFilterType find filterType=" + filterType);
                    break;
                }
            }
        }

        return zOrderID;
    }

    /*---------------------------------------------------*/

    /**
     * 获取filter的配置信息，用于下一个filtergroup进行初始化
     *
     * @return
     */
    public String getFilterConfig() {
        return mFilterSession.getFilterConfigByFilterType(mEditFilterTypes);
    }

    /*---------------------------------------------------*/

    /**
     * 添加美颜
     */
    public void addBeauty() {
        if (FilterIDManager.NO_ID == mBeautyFilterID) {
            mBeautyFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_BEAUTYFACE, FilterGroupType.DEFAULT_FILTER_GROUP, getZOrderIDByFilterType(FilterType.GPUFILTER_BEAUTYFACE));
            mBeautyFilterParam = new BeautyFaceFilterParameter();
        }
    }

    /**
     * 设置美颜参数
     *
     * @param intensity
     */
    public void changeBeauty(float intensity) {
        if (mBeautyFilterID != FilterIDManager.NO_ID) {
            mBeautyFilterParam.mBeautyFaceParam = intensity;
            mFilterSession.resetFilterParameter(mBeautyFilterID, mBeautyFilterParam);
        }
    }

    /**
     * 移除美颜
     */
    public void removeBeauty() {
        if (mBeautyFilterID != FilterIDManager.NO_ID) {
            mFilterSession.removeFilterByFilterID(mBeautyFilterID);
            mBeautyFilterID = FilterIDManager.NO_ID;
        }
    }

    /**
     * 是否存在美颜
     *
     * @return
     */
    public boolean existBeauty() {
        return mBeautyFilterID == FilterIDManager.NO_ID ? false : true;
    }

    /*---------------------------------------------------*/

    /**
     * 添加瘦脸
     */
    public void addThinFace() {
        if (FilterIDManager.NO_ID == mThinFaceFilterID) {
            mThinFaceFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_THINFACE, FilterGroupType.DEFAULT_FILTER_GROUP, getZOrderIDByFilterType(FilterType.GPUFILTER_THINFACE));
            mThinFaceFilterParam = new ThinFaceFilterParameter();
        }
    }

    /**
     * 设置瘦脸参数
     *
     * @param intensity
     */
    public void changeThinFace(float intensity) {
        if (mThinFaceFilterID != FilterIDManager.NO_ID) {
            mThinFaceFilterParam.mThinFaceParam = intensity;
            mFilterSession.resetFilterParameter(mThinFaceFilterID, mThinFaceFilterParam);
        }
    }

    /**
     * 移除瘦脸
     */
    public void removeThinFace() {
        if (mThinFaceFilterID != FilterIDManager.NO_ID) {
            mFilterSession.removeFilterByFilterID(mThinFaceFilterID);
            mThinFaceFilterID = FilterIDManager.NO_ID;
        }
    }

    /**
     * 是否存在瘦脸
     *
     * @return
     */
    public boolean existThinFace() {
        return mThinFaceFilterID == FilterIDManager.NO_ID ? false : true;
    }

    /*---------------------------------------------------*/

    /**
     * 添加拉伸
     *
     * @param path
     */
    public int addStretchEffect(String path) {
        int effectID = mFilterSession.addFilter(FilterType.GPUFILTER_STRETCH, FilterGroupType.DEFAULT_FILTER_GROUP);

        StretchFilterParameter stretchFilterParameter = new StretchFilterParameter();
        stretchFilterParameter.mEffectPath = path;
        stretchFilterParameter.mOPType = FilterOPType.OP_SET_EFFECT_PATH;

        int paramID = mFilterSession.addFilterParameter(effectID, stretchFilterParameter);
        YYLog.info(TAG, "addStretchEffect, path=" + path + ",effectId=" + effectID + ",paramId=" + paramID);
        return effectID;
    }

    /**
     * 设置拉伸参数
     *
     * @param effectId
     * @param level
     */
    public void changeStretchEffectLevel(int effectId, float level) {
        if (effectId != FilterIDManager.NO_ID) {
            List<BaseFilterParameter> params = mFilterSession.getFilterParameters(effectId);
            for (BaseFilterParameter param : params) {
                if (param != null) {
                    ((StretchFilterParameter) param).mLevel = level;
                    ((StretchFilterParameter) param).mOPType = FilterOPType.OP_CHANGE_LEVEL;
                    mFilterSession.modifyFilterParameter(effectId, param.mParameterID, param);
                }
            }
        }
    }

    /**
     * 移除拉伸效果
     *
     * @param effectId
     */
    public void removeStretchEffect(int effectId) {
        if (effectId != FilterIDManager.NO_ID) {
            mFilterSession.removeFilterByFilterID(effectId);
        }
    }

    /*---------------------------------------------------*/

    /**
     * 添加色表
     */
    public void addLut() {
        if (FilterIDManager.NO_ID == mDoubleColorTableFilterID) {
            mDoubleColorTableFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_DOUBLE_COLORTABLE, FilterGroupType.DEFAULT_FILTER_GROUP, getZOrderIDByFilterType(FilterType.GPUFILTER_DOUBLE_COLORTABLE));
            mDoubleColorTableFilterParam = new DoubleColorTableFilterParameter();
        }
    }

    /**
     * 设置色表参数
     *
     * @param file1
     * @param file2
     */
    public void changeLutWithLutFile(String file1, String file2) {
        if (mDoubleColorTableFilterID != FilterIDManager.NO_ID) {
            mDoubleColorTableFilterParam.mColorTableParam1 = file1;
            mDoubleColorTableFilterParam.mColorTableParam2 = file2;
            mFilterSession.resetFilterParameter(mDoubleColorTableFilterID, mDoubleColorTableFilterParam);
        }
    }

    /**
     * 修改色表通过色表比例
     *
     * @param ratio
     */
    public void changeLutWithRatio(float ratio) {
        if (mDoubleColorTableFilterID != FilterIDManager.NO_ID) {
            mDoubleColorTableFilterParam.mRatio = ratio;
            mFilterSession.resetFilterParameter(mDoubleColorTableFilterID, mDoubleColorTableFilterParam);
        }
    }

    /**
     * 修改色表通过是否是竖屏滑动
     *
     * @param isVertical
     */
    public void changeLutWithIsVertical(boolean isVertical) {
        if (mDoubleColorTableFilterID != FilterIDManager.NO_ID) {
            mDoubleColorTableFilterParam.mIsVertical = isVertical;
            mFilterSession.resetFilterParameter(mDoubleColorTableFilterID, mDoubleColorTableFilterParam);
        }
    }

    /**
     * 移除色表
     */
    public void removeLut() {
        if (mDoubleColorTableFilterID != FilterIDManager.NO_ID) {
            mFilterSession.removeFilterByFilterID(mDoubleColorTableFilterID);
            mDoubleColorTableFilterID = FilterIDManager.NO_ID;
        }
    }

    /**
     * 是否存在色表
     *
     * @return
     */
    public boolean existLut() {
        return mDoubleColorTableFilterID == FilterIDManager.NO_ID ? false : true;
    }

    /*---------------------------------------------------*/
    private void initEmojiWithDirecory(String directory) {
        FilterConfigParse.FilterConfigInfo filterConfigInfo = FilterConfigParse.parseFilterConf(directory);
        if (filterConfigInfo != null) {
            if (mRecordConfig != null && mVideoFilterContext != null) {
                mRecordConfig.setVoiceChangeMode(filterConfigInfo.mVoiceChangeMode);
                mVideoFilterContext.setRecordConfig(mRecordConfig);
            }

            //step 1:先添加表情
            if (filterConfigInfo.mEmojiEffectPath != null) {
                mEffectFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_EFFECT, FilterGroupType.DEFAULT_FILTER_GROUP, getZOrderIDByFilterType(FilterType.GPUFILTER_EFFECT));
                mEffectFilterParam = new EffectFilterParameter();
                mEffectFilterParam.mEffectParam = filterConfigInfo.mEmojiEffectPath;
                mEffectFilterParam.mSurportSeeking = filterConfigInfo.mSupportSeeking;
                mFilterSession.resetFilterParameter(mEffectFilterID, mEffectFilterParam);
            }

            //step 2:当前表情自带滤镜，进行滤镜替换
            if (filterConfigInfo.mColorTableEffectPath != null) {
                removeLut();

                if (filterConfigInfo.mColorTableEffectPath != null) {
                    mDoubleColorTableFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_DOUBLE_COLORTABLE, FilterGroupType.DEFAULT_FILTER_GROUP, getZOrderIDByFilterType(FilterType.GPUFILTER_DOUBLE_COLORTABLE));
                    mDoubleColorTableFilterParam = new DoubleColorTableFilterParameter();
                    mDoubleColorTableFilterParam.mColorTableParam1 = filterConfigInfo.mColorTableEffectPath;
                    mDoubleColorTableFilterParam.mColorTableParam2 = null;
                    mDoubleColorTableFilterParam.mIsVertical = false;
                    mDoubleColorTableFilterParam.mRatio = 1;
                    mFilterSession.resetFilterParameter(mDoubleColorTableFilterID, mDoubleColorTableFilterParam);
                }
            } else {
                //step 3:当前表情不带滤镜，如果原先设置了滤镜，需要改变滤镜的z-order
                if (mDoubleColorTableFilterID != FilterIDManager.NO_ID) {
                    int zOrder = FilterLayout.generateZOrderID();
                    mFilterSession.modifyFilterZOrder(mDoubleColorTableFilterID, zOrder);
                }
            }
        }
    }

    /**
     * 添加表情
     *
     * @param directory
     */
    public void addEmojiWithDirecory(String directory) {
        if (mEffectFilterID != FilterIDManager.NO_ID) {
            return;
        }

//        removeLut();
        initEmojiWithDirecory(directory);
    }

    /**
     * @param directory
     */
    public void changeEmojiWithDirectory(String directory) {
        if (FilterIDManager.NO_ID == mEffectFilterID) {
            return;
        }

        removeEmoji();
        initEmojiWithDirecory(directory);
    }

    /**
     * 移除表情，如果有对应的色表，也会随之移除
     */
    public void removeEmoji() {
        // 移除表情
        if (mEffectFilterID != FilterIDManager.NO_ID) {
            mFilterSession.removeFilterByFilterID(mEffectFilterID);
            mEffectFilterID = FilterIDManager.NO_ID;

            // 移除配置文件中加载的色表
//            removeLut();

            if(mRecordConfig != null && mVideoFilterContext != null) {
                mRecordConfig.setVoiceChangeMode(AudioVoiceChangerToolbox.VeoNone);
                mVideoFilterContext.setRecordConfig(mRecordConfig);
                YYLog.info(TAG, "set voice change mode:" + AudioVoiceChangerToolbox.VeoNone);
            }
        }
    }

    /**
     * 设置色表是否可见
     * @param isVisible
     */
    public void setColorTableVisibility(boolean isVisible) {
        if (mDoubleColorTableFilterID != FilterIDManager.NO_ID) {
            List<BaseFilterParameter> params = mFilterSession.getFilterParameters(mDoubleColorTableFilterID);
            for (BaseFilterParameter param : params) {
                if (param != null) {
                    param.mVisible = isVisible;

                    mFilterSession.modifyFilterParameter(mDoubleColorTableFilterID, param.mParameterID, param);
                }
            }
        }
    }

    /**
     * 是否存在表情
     *
     * @return
     */
    public boolean existEmoji() {
        return mEffectFilterID != FilterIDManager.NO_ID? true : false;
    }

    /**
     * 告诉表情，现在要启动录制。如果表情支持seek，那么此时是重新播放表情
     *
     */
    public void notifyEmojiStartRecord() {
        if (mEffectFilterID != FilterIDManager.NO_ID) {
            mEffectFilterParam.mStartRecordFlag++;
            mEffectFilterParam.mStartRecordFlag %= 2; // 0、1
            mFilterSession.resetFilterParameter(mEffectFilterID, mEffectFilterParam);
        }
    }

    /**
     * 表情可以seek到某个时间点，从断点继续播放
     *
     * @param startTime seek的时间点，单位ms
     */
    public void notifyEmojiStartRecord(int startTime) {
        mEffectFilterParam.mStartPtsMs = startTime;
        notifyEmojiStartRecord();
    }



    /*---------------------------------------------------*/
    /**
     * 设置游戏的路径，获取某个effectID对应的paramID
     * @param path
     */
    public int setGamePath(String path) {
        removeLut();

        int gameFilterID;
        String directory = path.substring(0, path.lastIndexOf(File.separator));
        FilterConfigParse.FilterConfigInfo filterConfigInfo = FilterConfigParse.parseFilterConf(directory);
        if (filterConfigInfo != null) {
            if (filterConfigInfo.mColorTableEffectPath != null) {
                mDoubleColorTableFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_DOUBLE_COLORTABLE, FilterGroupType.DEFAULT_FILTER_GROUP, getZOrderIDByFilterType(FilterType.GPUFILTER_DOUBLE_COLORTABLE));
                mDoubleColorTableFilterParam = new DoubleColorTableFilterParameter();
                mDoubleColorTableFilterParam.mColorTableParam1 = filterConfigInfo.mColorTableEffectPath;
                mDoubleColorTableFilterParam.mColorTableParam2 = null;
                mDoubleColorTableFilterParam.mIsVertical = false;
                mDoubleColorTableFilterParam.mRatio = 1;
                mFilterSession.resetFilterParameter(mDoubleColorTableFilterID, mDoubleColorTableFilterParam);
            }
        }

        gameFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_GAME, FilterGroupType.DEFAULT_FILTER_GROUP);
        if (gameFilterID >= 0) {
            OFGameParameter gameParameter = new OFGameParameter();
            gameParameter.mGamePath = path;
            gameParameter.mOPType = FilterOPType.OP_SET_EFFECT_PATH;

            int paramID = mFilterSession.addFilterParameter(gameFilterID, gameParameter);
            YYLog.info(TAG, "setGamePath success, effectID=" + gameFilterID + ",paramID=" + paramID);
        } else {
            YYLog.error(TAG, "addGame error id = " + gameFilterID);
            return FilterIDManager.NO_ID;
        }
        return gameFilterID;
    }

    /**
     * 设置游戏控制指令
     * @param gameEffectID
     * @param ctrlCmd
     */
    public void setGameCtrlCmd(int gameEffectID, int ctrlCmd) {
        YYLog.info(TAG, "setGameCtrlCmd begin, gameEffectID=" + gameEffectID + ",ctrlCmd=" + ctrlCmd);
        if (gameEffectID != FilterIDManager.NO_ID) {
            List<BaseFilterParameter> gameParameters = mFilterSession.getFilterParameters(gameEffectID);

            if (gameParameters != null && gameParameters.get(0) != null) {
                OFGameParameter gameParameter = (OFGameParameter) gameParameters.get(0);
                switch (ctrlCmd) {
                    case GameFilterCmdType.CMD_START:
                        gameParameter.mOPType = FilterOPType.OP_START_GAME;
                        break;
                    case GameFilterCmdType.CMD_PAUSE:
                        gameParameter.mOPType = FilterOPType.OP_PAUSE_GAME;
                        break;
                    case GameFilterCmdType.CMD_RESUME:
                        gameParameter.mOPType = FilterOPType.OP_RESUME_GAME;
                        break;
                    case GameFilterCmdType.CMD_STOP:
                        gameParameter.mOPType = FilterOPType.OP_STOP_GAME;
                        break;
                }

                if (gameParameter.mOPType > 0) {
                    mFilterSession.modifyFilterParameter(gameEffectID, gameParameter.mParameterID, gameParameter);
                }
            }

        } else {
            YYLog.error(TAG, "setGameCtrlCmd error");
        }
    }


    /**
     * 设置game event的回调
     * @param gameEffectID
     * @param callback
     */
    public void setGameEventCallback(int gameEffectID, final OFGameFilter.GameEventCallBack callback) {
        YYLog.info(TAG, "setGameEventCallback effectID =" + gameEffectID);
        if (gameEffectID != FilterIDManager.NO_ID) {
            List<BaseFilterParameter> gameParameters = mFilterSession.getFilterParameters(gameEffectID);

            if (gameParameters != null && gameParameters.get(0) != null) {
                OFGameParameter gameParameter = (OFGameParameter) gameParameters.get(0);
                gameParameter.mOPType = FilterOPType.OP_SET_GAME_CALLBACK;
                gameParameter.mCallBack = callback;

                mFilterSession.modifyFilterParameter(gameEffectID, gameParameter.mParameterID, gameParameter);
            }
        } else {
            YYLog.error(TAG, "setGameEventCallback error");
        }
    }

    /**
     * 通过json字符串改变游戏参数
     * @param gameEffectID
     * @param json
     */
    public void setGameWithJson(int gameEffectID, String json) {
        YYLog.info(TAG, "setGameWithJson effectID =" + gameEffectID);
        if (gameEffectID != FilterIDManager.NO_ID) {
            List<BaseFilterParameter> gameParameters = mFilterSession.getFilterParameters(gameEffectID);

            if (gameParameters != null && gameParameters.get(0) != null) {
                OFGameParameter gameParameter = (OFGameParameter) gameParameters.get(0);
                gameParameter.mOPType = FilterOPType.OP_SEND_GAME_EVENT;
                gameParameter.mEventJson = json;

                mFilterSession.modifyFilterParameter(gameEffectID, gameParameter.mParameterID, gameParameter);
            }
        } else {
            YYLog.error(TAG, "setGameWithJson error");
        }
    }

    /**
     * 移除游戏
     * @param gameEffectID
     */
    public void removeGame(int gameEffectID) {
        YYLog.info(TAG, "removeGame effectID =" + gameEffectID);
        if (gameEffectID != FilterIDManager.NO_ID) {
            mFilterSession.removeFilterByFilterID(gameEffectID);

            // 移除配置文件中加载的色表
            removeLut();
        }
    }
    /*---------------------------------------------------*/

    /**
     * 开始游戏
     * @param paramId
     */
    public void startBasketballGame(int paramId) {
        if (FilterIDManager.NO_ID != mBasketBallFilterID && paramId >= 0) {
            OFBasketBallGameParameter mOFBasketBallGameParameter = (OFBasketBallGameParameter) mFilterSession
                    .getFilterParameter(mBasketBallFilterID, paramId);
            mOFBasketBallGameParameter.mOPType = FilterOPType.OP_START_GAME;

            mFilterSession.modifyFilterParameter(mBasketBallFilterID, paramId, mOFBasketBallGameParameter);
        } else {
            YYLog.error("", "editTickerEffectID error id = " + mOFBasketBallGameParameter + " paramId=" + paramId);
        }

    }

    /**
     * 暂停游戏
     * @param paramId
     */
    public void pauseBasketballGame(int paramId) {
        if (FilterIDManager.NO_ID != mBasketBallFilterID && paramId >= 0) {
            OFBasketBallGameParameter mOFBasketBallGameParameter = (OFBasketBallGameParameter) mFilterSession
                    .getFilterParameter(mBasketBallFilterID, paramId);
            mOFBasketBallGameParameter.mOPType = FilterOPType.OP_PAUSE_GAME;

            mFilterSession.modifyFilterParameter(mBasketBallFilterID, paramId, mOFBasketBallGameParameter);
        } else {
            YYLog.error("", "editTickerEffectID error id = " + mOFBasketBallGameParameter + " paramId=" + paramId);
        }
    }

    /**
     * 恢复游戏
     * @param paramId
     */
    public void resumeBasketballGame(int paramId) {
        if (FilterIDManager.NO_ID != mBasketBallFilterID && paramId >= 0) {
            OFBasketBallGameParameter mOFBasketBallGameParameter = (OFBasketBallGameParameter) mFilterSession
                    .getFilterParameter(mBasketBallFilterID, paramId);
            mOFBasketBallGameParameter.mOPType = FilterOPType.OP_RESUME_GAME;

            mFilterSession.modifyFilterParameter(mBasketBallFilterID, paramId, mOFBasketBallGameParameter);
        } else {
            YYLog.error("", "editTickerEffectID error id = " + mOFBasketBallGameParameter + " paramId=" + paramId);
        }
    }

    /**
     * 设置分数
     * @param score
     * @param paramId
     */
    public void setBasketBallData(final int score, int paramId) {
        if (FilterIDManager.NO_ID != mBasketBallFilterID && paramId >= 0) {
            OFBasketBallGameParameter mOFBasketBallGameParameter = (OFBasketBallGameParameter) mFilterSession
                    .getFilterParameter(mBasketBallFilterID, paramId);
            mOFBasketBallGameParameter.mOPType |= FilterOPType.OP_SEND_GAME_EVENT;
            mOFBasketBallGameParameter.mInitScore = score;
            mFilterSession.modifyFilterParameter(mBasketBallFilterID, paramId, mOFBasketBallGameParameter);
        } else {
            YYLog.error("", "editTickerEffectID error id = " + mOFBasketBallGameParameter + " paramId=" + paramId);
        }

    }

    /**
     * 设置回调
     * @param callBack
     * @param paramId
     */
    public void setBasketballCallBack(final OFBasketBallGameFilter.BasketBallGameCallBack callBack, int paramId) {
        if (FilterIDManager.NO_ID != mBasketBallFilterID && paramId >= 0) {
            OFBasketBallGameParameter mOFBasketBallGameParameter = (OFBasketBallGameParameter) mFilterSession
                    .getFilterParameter(mBasketBallFilterID, paramId);
            mOFBasketBallGameParameter.mOPType = FilterOPType.OP_SET_GAME_CALLBACK;
            mOFBasketBallGameParameter.mCallBack = callBack;
            mFilterSession.modifyFilterParameter(mBasketBallFilterID, paramId, mOFBasketBallGameParameter);
        } else {
            YYLog.error("", "editTickerEffectID error id = " + mOFBasketBallGameParameter + " paramId=" + paramId);
        }

    }

    /**
     * 停止游戏
     * @param paramId
     */
    public void stopBasketballGame(int paramId) {
        if (FilterIDManager.NO_ID != mBasketBallFilterID && paramId >= 0) {
            OFBasketBallGameParameter mOFBasketBallGameParameter = (OFBasketBallGameParameter) mFilterSession
                    .getFilterParameter(mBasketBallFilterID, paramId);
            mOFBasketBallGameParameter.mOPType = FilterOPType.OP_STOP_GAME;

            mFilterSession.modifyFilterParameter(mBasketBallFilterID, paramId, mOFBasketBallGameParameter);
        } else {
            YYLog.error("", "editTickerEffectID error id = " + mOFBasketBallGameParameter + " paramId=" + paramId);
        }

    }

    /**
     * 销毁
     * @param paramId
     */
    public void destoryBasketballGame(int paramId) {
        if (FilterIDManager.NO_ID != mBasketBallFilterID && paramId >= 0) {
            OFBasketBallGameParameter mOFBasketBallGameParameter = (OFBasketBallGameParameter) mFilterSession
                    .getFilterParameter(mBasketBallFilterID, paramId);
            mOFBasketBallGameParameter.mOPType = FilterOPType.OP_DESTROY_GAME;

            mFilterSession.modifyFilterParameter(mBasketBallFilterID, paramId, mOFBasketBallGameParameter);
            STMobileFaceDetectionWrapper.getInstance(mVideoFilterContext.getAndroidContext()).resetFaceLimit();
        } else {
            YYLog.error("", "editTickerEffectID error id = " + mOFBasketBallGameParameter + " paramId=" + paramId);
        }

    }

    /**
     * 添加游戏
     * @param path
     * @return
     */
    public int addBasketballEffect(final String path) {
        mBasketBallFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_BASKETBALLGAME, FilterGroupType.DEFAULT_FILTER_GROUP);
        if (mBasketBallFilterID >= 0) {
            int paramID;
            OFBasketBallGameParameter mOFBasketBallGameParameter = new OFBasketBallGameParameter();
            mOFBasketBallGameParameter.mBasketBallPathParam = path;
            mOFBasketBallGameParameter.mOPType |= FilterOPType.OP_SET_EFFECT_PATH;
            paramID = mFilterSession.addFilterParameter(mBasketBallFilterID, mOFBasketBallGameParameter);
            if(paramID > -1){
                STMobileFaceDetectionWrapper.getInstance(mVideoFilterContext.getAndroidContext()).setFaceLimit(1);
            }
            return paramID;
        } else {
            YYLog.error(TAG, "addBasketballEffect error id = " + mBasketBallFilterID);
            return FilterIDManager.NO_ID;
        }
    }

    /**
     *
     */
    private String getLutNameFromEffectFile(final String path){
        JSONObject jObject = readConfigFile(path);
        String lutName = "";

        try{
            int filterCount = jObject.getInt("filter_count");
            JSONArray filterList = jObject.getJSONArray("filter_list");

            for (int i = filterCount-1 ; i >=0 ; i--) {
                JSONObject filter = filterList.getJSONObject(i);

                if (!filter.isNull("ext_data")) {
                    JSONObject extData = filter.getJSONObject("ext_data");
                    if (!extData.isNull("LUTPath")){
                        lutName = extData.getString("LUTPath");
                        if(lutName.length() > 0){
                            break;
                        }
                    }
                }
            }
        }catch (JSONException e){
            YYLog.error(this, Constant.MEDIACODE_CAP+"effect json exception:"+e.toString());
            e.printStackTrace();
        }

        return lutName;
    }

    private JSONObject readConfigFile(final String configFile){
        JSONObject jObject = new JSONObject();
        try {
            FileInputStream inputStream = new FileInputStream(configFile);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String json;
            StringBuilder jsonSb = new StringBuilder();
            while ((json = bufferedReader.readLine()) != null) {
                jsonSb.append(json);
            }

            inputStream.close();
            inputStreamReader.close();
            bufferedReader.close();

            try{
                jObject = new JSONObject(jsonSb.toString());
            }catch (JSONException e){
                YYLog.error(this, Constant.MEDIACODE_CAP+"config json exception:"+e.toString());
                e.printStackTrace();
            }

        }catch (IOException e) {
            YYLog.error(this, Constant.MEDIACODE_CAP+"IO exception:"+e.toString());
            e.printStackTrace();
        }

        return jObject;
    }

    public String getCurrentLutName(){
        String lutName = "";
        if(mDoubleColorTableFilterParam == null){
            return lutName;
        }
        if ( mDoubleColorTableFilterParam.mRatio == 1.0f && mDoubleColorTableFilterParam.mColorTableParam1 != null) {
            lutName = getLutNameFromEffectFile(mDoubleColorTableFilterParam.mColorTableParam1);
        } else if (mDoubleColorTableFilterParam.mColorTableParam2 != null) {
            lutName = getLutNameFromEffectFile(mDoubleColorTableFilterParam.mColorTableParam2);
        }
        return lutName;
    }

    public float getBeautyIntensity(){
        if (mBeautyFilterParam != null){
            return mBeautyFilterParam.mBeautyFaceParam;
        }
        return 0.0f;
    }


      /*---------------------------------------------------*/

    public int addEditStickerEffect() {
        YYLog.info(TAG, "addEditStickerEffect");
        int editTickerEffectFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_EDIT_STICKER_EFFECT, FilterGroupType.DEFAULT_FILTER_GROUP);
        if (editTickerEffectFilterID >= 0) {
            YYLog.info(TAG, "addEditStickerEffect editTickerEffectFilterID = "+editTickerEffectFilterID);
            return editTickerEffectFilterID;
        } else {
            return FilterIDManager.NO_ID;
        }

    }

    private int  mEditTickerEffectFilterID;
    private int mPrmID;

    /**
     * 添加编辑贴纸特效
     *
     * @param path        贴纸路径
     * @param trackConfig 贴纸配置参数  贴纸配置参数 取值是0和1，0表示不允许随跟踪算法缩放旋转，1表示可以
     * @return paramID  贴纸参数id
     */
    public synchronized int setEditStickerEffectPath(String path, int editTickerEffectFilterID, int trackConfig) {
        YYLog.info(TAG, "setEditStickerEffectPath path  = " + path +"editTickerEffectFilterID="+ editTickerEffectFilterID +" trackConfig="+trackConfig);
        int paramID = -1;
        if (editTickerEffectFilterID >= 0) {
            OFEditStickerEffectFilterParameter mEffectFilterParameter = new OFEditStickerEffectFilterParameter();
            mEffectFilterParameter.mEffectDirectory = path;
            mEffectFilterParameter.mOPType = FilterOPType.OP_SET_EFFECT_PATH;
            mEffectFilterParameter.mParameterID = 0;
            mEffectFilterParameter.mTrackerConfigFlag = trackConfig;
            mEffectFilterParameter.mOPTypeSave = mEffectFilterParameter.mOPType;
            paramID = mFilterSession.addFilterParameter(editTickerEffectFilterID, mEffectFilterParameter);
            YYLog.info(TAG, "setEditStickerEffectPath paramID = "+paramID);
            mPrmID = paramID;

        } else {
            YYLog.error(TAG, "editTickerEffectID error id = " + editTickerEffectFilterID);
        }
        return paramID;
    }

    /**
     * 改变编辑贴纸特效偏移缩放和旋转
     *
     * @param paramId addEditStickerEffect 返回的值
     */
    public synchronized void changeEditStickerEffectParam(float translateX, float translateY, float rotation, float scale, int paramId, int editTickerEffectFilterID) {
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramId >= 0) {
            BaseFilterParameter param = mFilterSession.getFilterParameter(editTickerEffectFilterID, paramId);
            if (param == null) {
                return;
            }
            OFEditStickerEffectFilterParameter mEffectFilterParameter = (OFEditStickerEffectFilterParameter) param;
            mEffectFilterParameter.mTranslateX = translateX;
            mEffectFilterParameter.mTranslateY = translateY;
            mEffectFilterParameter.mRotation = rotation;
            mEffectFilterParameter.mScale = scale;
            mEffectFilterParameter.mParameterID = paramId;
            mEffectFilterParameter.mOPType = FilterOPType.OP_CHANGE_MATRIX;
            mEffectFilterParameter.mOPTypeSave |= mEffectFilterParameter.mOPType;
            mFilterSession.modifyFilterParameter(editTickerEffectFilterID, paramId, mEffectFilterParameter);

        } else {
            YYLog.error(TAG, "editTickerEffectID error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }

    /**
     * 改变编辑贴纸特效时间
     *
     * @param startTime 贴纸生效开始时间
     * @param endtime   贴纸生效结束时间
     */
    public void changeEditStickerEffectParam(long startTime, long endtime, int paramId, int editTickerEffectFilterID) {
        YYLog.info(TAG, "changeEditStickerEffectParam startTime=" + startTime + " endtime=" + endtime +
                " editTickerEffectFilterID=" + editTickerEffectFilterID + " paramId=" + paramId);
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramId >= 0) {
            BaseFilterParameter param = mFilterSession.getFilterParameter(editTickerEffectFilterID, paramId);
            if (param == null) {
                return;
            }
            OFEditStickerEffectFilterParameter mEffectFilterParameter = (OFEditStickerEffectFilterParameter) param;
            mEffectFilterParameter.mStartPtsMs = startTime;
            mEffectFilterParameter.mEndPtsMs = endtime;
            mEffectFilterParameter.mOPType = FilterOPType.OP_CHANGE_TIME;
            mEffectFilterParameter.mParameterID = paramId;
            mEffectFilterParameter.mOPTypeSave |= mEffectFilterParameter.mOPType;
            mFilterSession.modifyFilterParameter(editTickerEffectFilterID, paramId, mEffectFilterParameter);
        } else {
            YYLog.error(TAG, "editTickerEffectID error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }

    /**
     * 定住编辑贴纸效果
     *
     * @param paramId addEditStickerEffect 返回的值
     */
    public void traceEditSticker(float left, float top, float width, float height, int paramId, int editTickerEffectFilterID) {
        YYLog.info(TAG, "traceEditSticker  editTickerEffectFilterID=" + editTickerEffectFilterID + " paramId=" + paramId);
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramId >= 0) {
            BaseFilterParameter param = mFilterSession.getFilterParameter(editTickerEffectFilterID, paramId);
            if (param == null) {
                return;
            }
            OFEditStickerEffectFilterParameter mEffectFilterParameter = (OFEditStickerEffectFilterParameter) param;
            mEffectFilterParameter.mLeft = left;
            mEffectFilterParameter.mTop = top;
            mEffectFilterParameter.mWidth = width;
            mEffectFilterParameter.mHeight = height;
            mEffectFilterParameter.mOPType = FilterOPType.OP_ADD_TRACK;
            mEffectFilterParameter.mParameterID = paramId;
            mEffectFilterParameter.mOPTypeSave |= mEffectFilterParameter.mOPType;
            mFilterSession.modifyFilterParameter(editTickerEffectFilterID, paramId, mEffectFilterParameter);
        } else {
            YYLog.error(TAG, "editTickerEffectID error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }

    /**
     * 移除编辑贴纸效果
     */
    public void removeEditSticker(int paramId, int editTickerEffectFilterID) {
        YYLog.info(TAG, "removeEditSticker  editTickerEffectFilterID=" + editTickerEffectFilterID + " paramId=" + paramId);
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramId >= 0) {
//            EditStickerEffectFilterParameter mEffectFilterParameter = (EditStickerEffectFilterParameter)mFilterSession
//                    .getFilterParameter(editTickerEffectFilterID, paramId);
//            mEffectFilterParameter.opType = 3;
//            mEffectFilterParameter.mParameterID = paramId;
//            mFilterSession.modifyFilterParameter(editTickerEffectFilterID, paramId, mEffectFilterParameter);
            mFilterSession.removeFilterByFilterID(editTickerEffectFilterID);
        } else {
            YYLog.error(TAG, "editTickerEffectID error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }

    /**
     * 移除所有编辑贴纸效果
     */
    public void removeEditStickerByType() {
        YYLog.info(TAG, "removeEditStickerByType ");
        mFilterSession.removeFilterByFilterType(FilterType.GPUFILTER_EDIT_STICKER_EFFECT);
        mFilterSession.removeFilterByFilterType(FilterType.GPUFILTER_WORD_STICKER_EFFECT); // 文字贴纸也跟着移除
    }

    /*---------------------------------------------------*/

    /**
     * 设置filter中包含的mp4路径
     *
     * @param mp4Path
     */
    public void openVideo(String mp4Path) {
        YYLog.info(TAG, "openVideo path:" + mp4Path);
        mRecordFilterGroup.getHandler().sendMessage(mRecordFilterGroup.getHandler().obtainMessage(RecordFilterGroup.GL_OPEN_VIDEO, mp4Path));
    }

    /**
     * 启动filter中的mp4解码
     */
    public void startVideo() {
        YYLog.info(TAG, "startVideo");
        mRecordFilterGroup.getHandler().sendMessage(mRecordFilterGroup.getHandler().obtainMessage(RecordFilterGroup.GL_START_VIDEO));
    }

    /**
     * 暂停filter中的mp4解码
     */
    public void pauseVideo() {
        YYLog.info(TAG, "pauseVideo");
        mRecordFilterGroup.getHandler().sendMessage(mRecordFilterGroup.getHandler().obtainMessage(RecordFilterGroup.GL_PAUSE_VIDEO));
    }

    /**
     * 停止filter中的mp4解码
     */
    public void stopVideo() {
        YYLog.info(TAG, "stopVideo");
        mRecordFilterGroup.getHandler().sendMessage(mRecordFilterGroup.getHandler().obtainMessage(RecordFilterGroup.GL_STOP_VIDEO));
    }

    /**
     * seek到mp4中的某个位置开始解码
     *
     * @param timeMs
     */
    public void seekVideoTo(int timeMs) {
        YYLog.info(TAG, "seekVideoTo: " + timeMs);
        mRecordFilterGroup.getHandler().sendMessage(mRecordFilterGroup.getHandler().obtainMessage(RecordFilterGroup.GL_SEEK_VIDEO, timeMs));
    }

    /**
     * 设置mp4解码的速度
     *
     * @param speed
     */
    public void setVideoSpeed(float speed) {
        YYLog.info(TAG, "setVideoSpeed: " + speed);
        mRecordFilterGroup.getHandler().sendMessage(mRecordFilterGroup.getHandler().obtainMessage(RecordFilterGroup.GL_SET_VIDEO_SPEED, speed));
    }

    /**
     * 设置视频播放结束后是否自动循环
     *
     * @param loop
     */
    public void setVideoLoopPlayback(boolean loop) {
        YYLog.info(TAG, "setVideoLoopPlayback: " + loop);
        mRecordFilterGroup.getHandler().sendMessage(mRecordFilterGroup.getHandler().obtainMessage(RecordFilterGroup.GL_VIDEO_AUTO_LOOP, loop));
    }

    /*---------------------------------------------------*/

    public void setRecordFilterGroup(RecordFilterGroup recordFilterGroup) {
        mRecordFilterGroup = recordFilterGroup;
    }



    /*--------------new interface of of effect-----------------*/

    /**
     * 添加filter
     *
     * @param filterType      filter类型
     * @param filterGroupType filterGroup类型
     */
    public int addFilter(int filterType, String filterGroupType) {
        //如果group传的是null，按照default filter group处理
        filterGroupType = (filterGroupType == null ? FilterGroupType.DEFAULT_FILTER_GROUP : filterGroupType);

        int filterId = mFilterSession.addFilter(filterType, filterGroupType);
        BaseFilterParameter param = ParamUtil.newParameter(filterType);
        int paramId = mFilterSession.addFilterParameter(filterId, param);

        if (filterId >= 0) {
            YYLog.info(TAG, "addFilter filterId=" + filterId + ",paramId=" + paramId + ",filterType=" + filterType + ",filterGroupType=" + filterGroupType);
            return filterId;
        } else {
            return FilterIDManager.NO_ID;
        }
    }

    /**
     * 更新filter的参数
     *
     * @param filterId   filter的标识
     * @param filterConf filter需要更新的参数：key：参数类型 value：参数具体值，可以为任意类型或者集合
     *                   map支持多个entry,表明一次性更新多个参数
     */
    public void updateFilterConf(int filterId, Map<Integer, Object> filterConf) {
        if (filterId == FilterIDManager.NO_ID || mFilterSession == null) {
            YYLog.error(TAG, "updateFilterConf error, filterId is invalid");
            return;
        }

        List<BaseFilterParameter> paramLilst = mFilterSession.getFilterParameters(filterId);
        if (paramLilst == null) {
            YYLog.error(TAG, "updateFilterConf error, paramLilst is null");
            return;
        }
        //目前只支持一个filter对应一个param的情况
        BaseFilterParameter param = paramLilst.get(0);
        if (param == null) {
            YYLog.error(TAG, "updateFilterConf error, param is null");
            return;
        }

        //更新param的对应变量
        param.mOPType = FilterOPType.NO_OP;
        for (Map.Entry<Integer, Object> entry : filterConf.entrySet()) {
            param.updateWithConf(entry);
        }
        mFilterSession.modifyFilterParameter(filterId, param.mParameterID, param);
    }

    /**
     * 删除filter
     *
     * @param filterId filter对应的id
     */
    public void removeFilter(int filterId) {
        YYLog.info(TAG, Constant.MEDIACODE_PLAYER_FILTER + "removeFilter, filterId=" + filterId);

        if (filterId != FilterIDManager.NO_ID) {
            mFilterSession.removeFilterByFilterID(filterId);
        }
    }

    /*--------------new interface of of effect-----------------*/
}
