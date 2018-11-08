package com.ycloud.gpuimagefilter.filter;

import com.ycloud.api.common.FilterGroupType;
import com.ycloud.api.common.FilterType;
import com.ycloud.common.Constant;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.OFEditStickerEffectFilterParameter;
import com.ycloud.gpuimagefilter.param.ParamUtil;
import com.ycloud.gpuimagefilter.param.PuzzleFilterParameter;
import com.ycloud.gpuimagefilter.param.TimeEffectParameter;
import com.ycloud.gpuimagefilter.param.WordStickerEffectFilterParameter;
import com.ycloud.gpuimagefilter.utils.FilterIDManager;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.gpuimagefilter.utils.IFilterInfoListener;
import com.ycloud.mediarecord.utils.MediaUtils;
import com.ycloud.utils.YYLog;

import java.util.List;
import java.util.Map;

/**
 * Created by jinyongqing on 2017/8/19.
 */

public class PlayerFilterSessionWrapper {
    private static final String TAG = PlayerFilterSessionWrapper.class.getSimpleName();
    private FilterSession mFilterSession;

    public PlayerFilterSessionWrapper() {
        mFilterSession = FilterCenter.getInstance().createFilterSession();
    }

    public int getSessionID() {
        return mFilterSession.getSessionID();
    }


    /**
     * 设置配置filter json
     *
     * @param filterConfigs 每个视频对应的filter json
     * @param videoPaths 每个视频对用的视频路径
     */
    public void setFilterConfig(String[] filterConfigs, String[] videoPaths) {
        if (null == filterConfigs || null == videoPaths || filterConfigs.length != videoPaths.length) {

            long step = 0;
            for (int i = 0; i < filterConfigs.length; i++) {
                String filterConifg = filterConfigs[i];
                long videoTime = (long) MediaUtils.getMediaInfo(videoPaths[i]).video_duration;
                long startTime = 0;
                long endTime = videoTime;


                startTime += step;
                endTime += step;
                step += videoTime;

                mFilterSession.addFilter(filterConifg, startTime, endTime);
            }
        }
    }

    /*---------------------------------------------------*/

    /**
     * 添加文字贴纸
     *
     * @param imagePath 图片路径
     * @param originalX 起始x坐标
     * @param originalY 起始y坐标
     * @param startTime 贴图的开始时间 单位毫秒
     * @param endTime   贴图的结束时间
     */
    public void addWordSticker(String imagePath, int originalX, int originalY, double startTime, double endTime) {
        int filterID = mFilterSession.addFilter(FilterType.GPUFILTER_WORD_STICKER_EFFECT, FilterGroupType.DEFAULT_FILTER_GROUP);
        WordStickerEffectFilterParameter param = new WordStickerEffectFilterParameter();
        param.mImagePath = imagePath;
        param.mOriginalX = originalX;
        param.mOriginalY = originalY;
        param.mStartTime = startTime;
        param.mEndTime = endTime;

        mFilterSession.addFilterParameter(filterID, param);
        YYLog.info(TAG, Constant.MEDIACODE_PLAYER_FILTER + "addWordSticker imagePath=" + imagePath + " originalX=" + originalX + " originalY=" +
                originalY + " startTime=" + startTime + " endTime=" + endTime);
    }

    public int addEditStickerEffect() {
        return addEditStickerEffect(FilterGroupType.DEFAULT_FILTER_GROUP);
    }

    public int addEditStickerEffect(String groupType) {
        //如果group传的是null，按照default filter group处理
        groupType = (groupType == null ? FilterGroupType.DEFAULT_FILTER_GROUP : groupType);

        int editTickerEffectFilterID = mFilterSession.addFilter(FilterType.GPUFILTER_EDIT_STICKER_EFFECT, groupType);
        if (editTickerEffectFilterID >= 0) {
            YYLog.info(TAG, "addEditStickerEffect success:groupType=" + groupType + ",effectId=" + editTickerEffectFilterID);
            return editTickerEffectFilterID;
        } else {
            YYLog.error(TAG, "addEditStickerEffect error:groupType=" + groupType);
            return FilterIDManager.NO_ID;
        }
    }

    /**
     * 添加编辑贴纸特效
     *
     * @param path                     贴纸路径
     * @param editTickerEffectFilterID 贴纸filterId
     *
     * @return paramID                 贴纸参数id
     */
    public synchronized int setEditStickerEffectPath(String path, int editTickerEffectFilterID) {
        return setEditStickerEffectPath(path, editTickerEffectFilterID, false);
    }

    /**
     * 添加编辑贴纸特效
     * @param path                     贴纸路径
     * @param editTickerEffectFilterID 贴纸filter id
     * @param needRepeatRender         是否需要重复渲染动画（如果添加完粒子贴纸需要特效始终在同一帧上渲染，则为true，否则为false）
     *
     * @return                         贴纸参数id
     */
    public synchronized int setEditStickerEffectPath(String path, int editTickerEffectFilterID, boolean needRepeatRender) {
        int paramID = -1;
        if (editTickerEffectFilterID >= 0) {
            OFEditStickerEffectFilterParameter mEffectFilterParameter = new OFEditStickerEffectFilterParameter();
            mEffectFilterParameter.mEffectDirectory = path;
            mEffectFilterParameter.mNeedRepeatRender = needRepeatRender;
            mEffectFilterParameter.mOPType = FilterOPType.OP_SET_EFFECT_PATH;
            mEffectFilterParameter.mParameterID = 0;
            mEffectFilterParameter.mOPTypeSave = mEffectFilterParameter.mOPType;
            paramID = mFilterSession.addFilterParameter(editTickerEffectFilterID, mEffectFilterParameter);
            YYLog.info(TAG, "setEditStickerEffectPath success:path= " + path + ",filterId=" + editTickerEffectFilterID + " needRepeatRender=" + needRepeatRender);
        } else {
            YYLog.error(TAG, "setEditStickerEffectPath error:path=" + path + ",filterId=" + editTickerEffectFilterID);
        }
        return paramID;
    }

    public synchronized int setEditStickerEffectPath(String path, int editTickerEffectFilterID, float translateX,
                                                     float translateY, float rotation, float scale, boolean needRepeatRender) {
        int paramID = -1;
        if (editTickerEffectFilterID >= 0) {
            OFEditStickerEffectFilterParameter mEffectFilterParameter = new OFEditStickerEffectFilterParameter();
            mEffectFilterParameter.mEffectDirectory = path;
            mEffectFilterParameter.mNeedRepeatRender = needRepeatRender;
            mEffectFilterParameter.mOPType = FilterOPType.OP_SET_EFFECT_PATH;
            mEffectFilterParameter.mParameterID = 0;
            mEffectFilterParameter.mOPTypeSave |= FilterOPType.OP_SET_EFFECT_PATH;

            mEffectFilterParameter.mOPType |= FilterOPType.OP_CHANGE_MATRIX;
            mEffectFilterParameter.mTranslateX = translateX;
            mEffectFilterParameter.mTranslateY = translateY;
            mEffectFilterParameter.mRotation = rotation;
            mEffectFilterParameter.mScale = scale;
            mEffectFilterParameter.mOPTypeSave |= FilterOPType.OP_CHANGE_MATRIX;

            //保存初始点的轨迹
            mEffectFilterParameter.mOPType |= FilterOPType.OP_KEEP_MATRIX;

            paramID = mFilterSession.addFilterParameter(editTickerEffectFilterID, mEffectFilterParameter);
            YYLog.info(TAG, "setEditStickerEffectPath success:path= " + path + ",filterId=" + editTickerEffectFilterID + " needRepeatRender=" + needRepeatRender);
        } else {
            YYLog.error(TAG, "setEditStickerEffectPath error:path=" + path + ",filterId=" + editTickerEffectFilterID);
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
            YYLog.error(TAG, "changeEditStickerEffectParam error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }

    /**
     * 为贴纸添加文字
     *
     * @param texts 文字数组
     * @param paramId
     * @param editTickerEffectFilterID
     */
    public synchronized void changeEditStickerEffectParam(String[] texts, int paramId, int editTickerEffectFilterID) {
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramId >= 0) {
            BaseFilterParameter param = mFilterSession.getFilterParameter(editTickerEffectFilterID, paramId);
            if (param == null) {
                return;
            }

            OFEditStickerEffectFilterParameter mEffectFilterParameter = (OFEditStickerEffectFilterParameter) param;
            mEffectFilterParameter.mTexts = texts;
            mEffectFilterParameter.mOPType = FilterOPType.OP_ADD_TEXT;
            mEffectFilterParameter.mOPTypeSave |= mEffectFilterParameter.mOPType;
            mEffectFilterParameter.mParameterID = paramId;
            mFilterSession.modifyFilterParameter(editTickerEffectFilterID, paramId, mEffectFilterParameter);
        } else {
            YYLog.error(TAG, "changeEditStickerEffectParam error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }


    /**
     * 修改贴纸的位置
     *
     * @param translateX
     * @param translateY
     * @param rotation
     * @param scale
     * @param paramId
     * @param editTickerEffectFilterID
     * @param shouldKeepPos
     */
    public synchronized void changeEditStickerEffectParam(float translateX, float translateY, float rotation, float scale, int paramId,
                                                          int editTickerEffectFilterID, boolean shouldKeepPos) {
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
            mEffectFilterParameter.mStickerType = OFEditStickerEffectFilterParameter.TYPE_PARTICLE_STICKER;

            if(shouldKeepPos) {
                mEffectFilterParameter.mOPType |= FilterOPType.OP_KEEP_MATRIX;
            }
            mFilterSession.modifyFilterParameterWithoutCopy(editTickerEffectFilterID, paramId, mEffectFilterParameter);
        } else {
            YYLog.error(TAG, "changeEditStickerEffectParam error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }

    /**
     * 改变编辑贴纸特效时间
     *
     * @param startTime 贴纸生效开始时间
     * @param endtime   贴纸生效结束时间
     */
    public synchronized void changeEditStickerEffectParam(long startTime, long endtime, int paramId, int editTickerEffectFilterID) {
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
            YYLog.error(TAG, "changeEditStickerEffectParam error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }

    /**
     * 改变编辑特效时间及淡出效果
     *
     */
    public synchronized void changeEditStickerEffectParam(long startTime, long endTime, long fadeoutDuration, int paramId, int editTickerEffectFilterID) {
        YYLog.info(TAG, "changeEditStickerEffectParam startTime=" + startTime + " endTime=" + endTime + " fadeoutDuration=" + fadeoutDuration +
                " editTickerEffectFilterID=" + editTickerEffectFilterID);
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramId >= 0) {
            BaseFilterParameter param = mFilterSession.getFilterParameter(editTickerEffectFilterID, paramId);
            if (param == null) {
                return;
            }

            //如果有时间特效，需要改变of特效作用的
            if (TimeEffectParameter.instance().IsExistTimeEffect()) {
                startTime = (long) TimeEffectParameter.instance().audioPtsToVideoPts(startTime);
                endTime = (long) TimeEffectParameter.instance().audioPtsToVideoPts(endTime);
            }

            OFEditStickerEffectFilterParameter mEffectFilterParameter = (OFEditStickerEffectFilterParameter) param;
            if(fadeoutDuration > 0) {
                mEffectFilterParameter.mUseFadeout = true;
            }

            long delayTime = 200;
            mEffectFilterParameter.mStartPtsMs = startTime;
            fadeoutDuration = Math.max(fadeoutDuration, delayTime);  //增加200ms延迟，保证手指离开的那一帧画上特效
            mEffectFilterParameter.mEndPtsMs = endTime + fadeoutDuration;
            mEffectFilterParameter.mFadeoutStartPtsMs = endTime + delayTime;

            mEffectFilterParameter.mOPType = FilterOPType.OP_CHANGE_TIME;
            mEffectFilterParameter.mParameterID = paramId;

            mEffectFilterParameter.mOPTypeSave |= mEffectFilterParameter.mOPType;
            mFilterSession.modifyFilterParameterWithoutCopy(editTickerEffectFilterID, paramId, mEffectFilterParameter);
        } else {
            YYLog.error(TAG, "changeEditStickerEffectParam error id = " + editTickerEffectFilterID + " paramId=" + paramId);
        }
    }


    /**
     * 改变粒子颜色
     * @param editTickerEffectFilterID
     * @param paramID
     * @param red
     * @param green
     * @param blue
     */
    public  synchronized void changeEditStickerEffectParamColor(int editTickerEffectFilterID, int paramID, float red, float green, float blue) {
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramID >= 0) {
            BaseFilterParameter param = mFilterSession.getFilterParameter(editTickerEffectFilterID, paramID);
            if (param == null) {
                return;
            }
            OFEditStickerEffectFilterParameter effectFilterParameter = (OFEditStickerEffectFilterParameter) param;
            effectFilterParameter.mColorR = red;
            effectFilterParameter.mColorG = green;
            effectFilterParameter.mColorB = blue;
            effectFilterParameter.mParameterID = paramID;
            effectFilterParameter.mOPType = FilterOPType.OP_CHANGE_COLOR;
            effectFilterParameter.mOPTypeSave |= effectFilterParameter.mOPType;
            mFilterSession.modifyFilterParameter(editTickerEffectFilterID, paramID, effectFilterParameter);
        } else {
            YYLog.error(TAG, "changeEditStickerEffectParamColor error id = " + editTickerEffectFilterID + " paramId=" + paramID);
        }
    }


    /**
     * 改变贴纸的大小
     * @param editTickerEffectFilterID
     * @param paramID
     * @param scale
     */
    public  synchronized  void changeEditStickerEffectParamScale(int editTickerEffectFilterID, int paramID, float scale) {
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramID >= 0) {
            BaseFilterParameter param = mFilterSession.getFilterParameter(editTickerEffectFilterID, paramID);
            if (param == null) {
                return;
            }
            OFEditStickerEffectFilterParameter effectFilterParameter = (OFEditStickerEffectFilterParameter) param;
            effectFilterParameter.mScale = scale;
            effectFilterParameter.mParameterID = paramID;
            effectFilterParameter.mOPType = FilterOPType.OP_CHANGE_SCALE;
            effectFilterParameter.mOPTypeSave |= effectFilterParameter.mOPType;
            mFilterSession.modifyFilterParameter(editTickerEffectFilterID, paramID, effectFilterParameter);
        } else {
            YYLog.error(TAG, "changeEditStickerEffectParamScale error id = " + editTickerEffectFilterID + " paramId=" + paramID);
        }
    }


    /**
     * 改变贴纸的旋转角度
     * @param editTickerEffectFilterID
     * @param paramID
     * @param rotation
     */
    public  synchronized  void changeEditStickerEffectParamRotation(int editTickerEffectFilterID, int paramID, float rotation) {
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramID >= 0) {
            BaseFilterParameter param = mFilterSession.getFilterParameter(editTickerEffectFilterID, paramID);
            if (param == null) {
                return;
            }
            OFEditStickerEffectFilterParameter effectFilterParameter = (OFEditStickerEffectFilterParameter) param;
            effectFilterParameter.mRotation = rotation;
            effectFilterParameter.mParameterID = paramID;
            effectFilterParameter.mOPType = FilterOPType.OP_CHANGE_ROTATION;
            effectFilterParameter.mOPTypeSave |= effectFilterParameter.mOPType;
            mFilterSession.modifyFilterParameter(editTickerEffectFilterID, paramID, effectFilterParameter);
        } else {
            YYLog.error(TAG, "changeEditStickerEffectParamRotation error id = " + editTickerEffectFilterID + " paramId=" + paramID);
        }
    }


    /**
     * 移除编辑贴纸效果
     */
    public synchronized void removeEditSticker(int paramId, int editTickerEffectFilterID) {
        if (FilterIDManager.NO_ID != editTickerEffectFilterID && paramId >= 0) {
            mFilterSession.removeFilterByFilterID(editTickerEffectFilterID);
            YYLog.info(TAG, "removeEditSticker success:id=" + editTickerEffectFilterID + ",paramId=" + paramId);
        } else {
            YYLog.error(TAG, "removeEditSticker error:id = " + editTickerEffectFilterID + ",paramId=" + paramId);
        }
    }

    /**
     * 移除所有编辑贴纸效果
     */
    public synchronized void removeEditStickerByType() {
        YYLog.info(TAG, "removeEditStickerByType ");
        mFilterSession.removeFilterByFilterType(FilterType.GPUFILTER_EDIT_STICKER_EFFECT);
        mFilterSession.removeFilterByFilterType(FilterType.GPUFILTER_WORD_STICKER_EFFECT); // 文字贴纸也跟着移除
    }


    /**
     * 增加拼图特效
     */
    public synchronized int addPuzzleFromDirectory(String directory) {
        YYLog.info(TAG, "addPuzzleFromDirectory:" + directory);
        int puzzleID = mFilterSession.addFilter(FilterType.GPUFILTER_PUZZLE, FilterGroupType.DEFAULT_FILTER_GROUP);

        PuzzleFilterParameter puzzleFilterParameter = new PuzzleFilterParameter();
        puzzleFilterParameter.mPuzzleDirectory = directory;

        int paramID = mFilterSession.addFilterParameter(puzzleID, puzzleFilterParameter);
        return puzzleID;


    }

    /**
     * 移除拼图特效
     */
    public synchronized void removePuzzle(int puzzleID) {
        YYLog.info(TAG, "removePuzzle:" + puzzleID);
        if (puzzleID != FilterIDManager.NO_ID) {
            mFilterSession.removeFilterByFilterID(puzzleID);
        }
    }


    /**
     * 设置特效，贴纸等是否可见
     *
     * @param filterId
     * @param isVisible
     */
    public synchronized void setVisibility(int filterId, boolean isVisible) {
        if (filterId != FilterIDManager.NO_ID) {
            List<BaseFilterParameter> params = mFilterSession.getFilterParameters(filterId);
            if (params != null) {
                for (BaseFilterParameter param : params) {
                    if (param != null) {
                        param.mVisible = isVisible;

                        mFilterSession.modifyFilterParameter(filterId, param.mParameterID, param);
                    }
                }
            }
        }
    }


    /**
     * 设置filterInfo的回调。在gpuFilter线程orangeFilter创建完filter后触发
     * @param listener
     */
    public void setFilterInfoListener(final IFilterInfoListener listener) {
        YYLog.info(TAG, "setFilterInfoListener:" + (listener == null ? "null" : "new listener"));
        mFilterSession.setFilterInfoListener(listener);
    }


    /**
     * 获取所有filter序列化后的配置信息
     */
    public String getFilterConfig() {
        return mFilterSession.getFilterConfig();
    }

    /**
     * 获取当前filterId对应的filter序列化后的配置信息
     *
     * @param filterIds 需要生成配置文件的filterId list
     */
    public String getFilterConfig(List<Integer> filterIds) {
        return mFilterSession.getFilterConfigByFilterId(filterIds);
    }

    /**
     * 透传客户端的uiconfig到OrangeFilter
     *
     * @param filterId
     * @param conf
     */
    public void setFilterUIConf(int filterId, Map<String, Object> conf) {
//        YYLog.info(TAG, "setFilterUIConf:filterId=" + filterId);
        if (filterId != FilterIDManager.NO_ID) {
            List<BaseFilterParameter> params = mFilterSession.getFilterParameters(filterId);
            for (BaseFilterParameter param : params) {
                if (param != null) {
                    param.mOPType = FilterOPType.OP_SET_UICONFIG;
                    param.mOPTypeSave |= FilterOPType.OP_SET_UICONFIG;
                    param.mUIConf = conf;

                    mFilterSession.modifyFilterParameter(filterId, param.mParameterID, param);
                }
            }
        }

    }
	
	/**
     * 清理当前session对应的of context缓存
     */
    public void clearCachedResource() {
        YYLog.info(TAG, "clearCachedResource");
        mFilterSession.clearCachedResource();
    }

    /*--------------new interface of of effect-----------------*/

    /**
     * 添加filter
     *
     * @param filterType      filter类型: 贴纸，色表，美颜等
     * @param filterGroupType filterGroup类型:特效互斥组
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
     * 通过filterConfig还原filter，并返回filterId list
     *
     * @param filterConfig filter序列化后的配置
     * @return filterConfig还原的filterId list
     */
    public List<Integer> addFilters(String filterConfig) {
        YYLog.info(TAG, "addFilters filterConfig:" + filterConfig);
        return mFilterSession.addFilter(filterConfig, false);
    }

    /**
     * 更新filter的参数
     *
     * @param filterId   filter的标识
     * @param filterConf key：定义某种update操作的类型，参见FilterOPType。例如：
     *                   FilterOPType.OP_CHANGE_MATRIX: 更新平移位置
     *                   FilterOPType.OP_CHANGE_SCALE:  更新缩放大小
     *                   <p>
     *                   value：update操作的参数具体值，可以为任意类型或者集合
     *                   <p>
     *                   filterConf map中可以设置多个entry,一次性update多个filter参数
     */
    public void updateFilterConf(int filterId, Map<Integer, Object> filterConf) {
        if (filterId == FilterIDManager.NO_ID) {
            YYLog.error(TAG, "updateFilterConf error, filterId is invalid");
            return;
        }
        if (mFilterSession.getFilterParameters(filterId) == null) {
            YYLog.error(TAG, "updateFilterConf error, getFilterParameters is null");
            return;
        }
        //目前只支持一个filter对应一个param的情况
        BaseFilterParameter param = mFilterSession.getFilterParameters(filterId).get(0);
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

    /**
     * 删除所有filter
     */
    public void removeAllFilters() {
        YYLog.info(TAG, Constant.MEDIACODE_PLAYER_FILTER + "removeAllFilters.");
        mFilterSession.removeAllFilter();
    }
    /*--------------new interface of of effect-----------------*/
}
