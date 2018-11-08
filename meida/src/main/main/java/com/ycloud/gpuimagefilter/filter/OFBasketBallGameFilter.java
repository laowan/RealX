package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.OFBasketBallGameParameter;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by Administrator on 2017/6/22.
 */

public class OFBasketBallGameFilter extends BaseFilter {
    private final String TAG = "OFBasketBallGameFilter";
    private OrangeFilter.OF_FrameData mFrameData = null;
    private static BasketBallGameCallBack mCallBack;
    private boolean mUseBasketBallFilter = false;

    public OFBasketBallGameFilter() {
        super();
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);

        mFrameData = new OrangeFilter.OF_FrameData();
    }

    @Override
    public void destroy() {
        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        if (mFilterId != -1) {
            OrangeFilter.destroyGame(mOFContext, mFilterId);
            mFilterId = -1;
        }

        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }

    @Override
    public String getFilterName() {
        return TAG;
    }

    private void updateParams(String effectFilePath) {
        if (effectFilePath != null) {
            if (effectFilePath != null) {
                if (mFilterId <= 0) {
                    OrangeFilter.setConfigBool(mOFContext, OrangeFilter.OF_ConfigKey_IsMirror, false);
                    mFilterId = OrangeFilter.createGameFromFile(mOFContext, effectFilePath + File.separator + "basketball.ofgame", effectFilePath);
                    YYLog.info("BasketballGameFilter", "setBasketballFilePath  basketBallGameId = " + mFilterId + " path =" + effectFilePath);
                }
            } else {
                YYLog.info("BasketballGameFilter", "setBasketballFilePath  path  is null");
            }
            mUseBasketBallFilter = true;
        } else {
            mUseBasketBallFilter = false;
        }
    }

    @Override
    protected void updateParams() {
        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            OFBasketBallGameParameter param = (OFBasketBallGameParameter) it.next().getValue();
            if (mOPType != param.mOPType) {
                mOPType = param.mOPType;

                if ((mOPType & FilterOPType.OP_SET_EFFECT_PATH) != 0) {
                    updateParams(param.mBasketBallPathParam);
                }

                if ((mOPType & FilterOPType.OP_START_GAME) != 0) {
                    startBasketballGame();
                }

                if ((mOPType & FilterOPType.OP_SEND_GAME_EVENT) != 0) {
                    setBasketballGameData(param.mInitScore);
                }

                if ((mOPType & FilterOPType.OP_SET_GAME_CALLBACK) != 0) {
                    setBasketballCallBack(param.mCallBack);
                }

                if ((mOPType & FilterOPType.OP_PAUSE_GAME) != 0) {
                    pauseBasketballGame();
                }

                if ((mOPType & FilterOPType.OP_RESUME_GAME) != 0) {
                    resumeBasketballGame();
                }

                if ((mOPType & FilterOPType.OP_STOP_GAME) != 0) {
                    stopBasketballGame();
                }

                if ((mOPType & FilterOPType.OP_DESTROY_GAME) != 0) {
                    destroyBasketballGame(mOFContext);
                }
            }
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (true == mUseBasketBallFilter && isBasketballNeedRender()) {
            storeOldFBO();
            mFrameData.faceFrameDataArr = sample.mFaceFrameDataArr;
            boolean hasFace = (mFrameData.faceFrameDataArr == null ? false : true);

            OrangeFilter.prepareFrameData(mOFContext,mOutputWidth,mOutputHeight,mFrameData);
            OrangeFilter.applyGameRGBA(mOFContext, mFilterId, sample.mTextureId, GLES20.GL_TEXTURE_2D, mTextures[0],
                    GLES20.GL_TEXTURE_2D, 0, 0, mOutputWidth, mOutputHeight, hasFace == true ? mFrameData : null);

            super.drawToFrameBuffer(sample);
            recoverOldFBO();

        }

        deliverToDownStream(sample);
        return true;
    }


    public boolean isBasketballNeedRender() {
        if (mFilterId > 0) {
            return true;
        }
        return false;
    }

    public interface BasketBallGameCallBack {
        public void basketBallGameCallbackFunc(int gameEvent, int ballNo, int maxCombo, int finalScore);

    }

    public void startBasketballGame() {
        YYLog.info("BasketballGameFilter", "startBasketballGame  basketBallGameId = " + mFilterId);
        if (mFilterId > 0) {
            OrangeFilter.startGame(mOFContext, mFilterId);
        }

    }

    public void pauseBasketballGame() {
        YYLog.info("BasketballGameFilter", "pauseBasketballGame  basketBallGameId = " + mFilterId);
        if (mFilterId > 0) {
            OrangeFilter.pauseGame(mOFContext, mFilterId);
        }

    }

    public void resumeBasketballGame() {
        YYLog.info("BasketballGameFilter", "resumeBasketballGame  basketBallGameId = " + mUseBasketBallFilter);
        if (mFilterId > 0) {
            OrangeFilter.resumeGame(mOFContext, mFilterId);
        }

    }

    public void destroyBasketballGame(int context) {
        YYLog.info("BasketballGameFilter", "destroyBasketballGame  basketBallGameId = " + mFilterId);
        mUseBasketBallFilter = false;
        if (mFilterId > 0) {
            OrangeFilter.setBasketBallGameListener(null);
            OrangeFilter.destroyGame(context, mFilterId);
            mFilterId = 0;
            mCallBack = null;
        }

    }

    public void setBasketballGameData(int score) {
        YYLog.info("BasketballGameFilter", "setBasketballGameData  basketBallGameId = " + mFilterId + " score=" + score);
        if (mFilterId > 0) {
            OrangeFilter.BasketballGameData gameData = new OrangeFilter.BasketballGameData();
            gameData.topScore = score;
            OrangeFilter.setGameData(mOFContext, mFilterId, gameData);
            OrangeFilter.setBasketBallGameListener(mBasketBallGameListener);
        }

    }

    public void stopBasketballGame() {
        YYLog.info("BasketballGameFilter", "stopBasketballGame  basketBallGameId =" + mFilterId);
        if (mFilterId > 0) {
            OrangeFilter.stopGame(mOFContext, mFilterId);
        }

    }


    private OrangeFilter.BasketBallGameListener mBasketBallGameListener = new OrangeFilter.BasketBallGameListener() {

        @Override
        public void basketBallGameCallbackFunc(int i, OrangeFilter.BasketballGameEventData data) {
            YYLog.info(this, "basketBallGameCallbackFunc ballNo=" + data.ballNo + " maxCombo=" + data.totalCombo + " finalScore=" + data.finalScore);
            if (mCallBack != null) {
                mCallBack.basketBallGameCallbackFunc(i, data.ballNo, data.totalCombo, data.finalScore);
            }
        }
    };


    public void setBasketballCallBack(BasketBallGameCallBack callBack) {
        mCallBack = callBack;
    }


}
