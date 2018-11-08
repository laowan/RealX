package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.api.common.SampleType;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.OFGameParameter;
import com.ycloud.gpuimagefilter.utils.FilterOPType;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;
import com.ycloud.ymrmodel.YYMediaSampleAlloc;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by jinyongqing on 2018/1/23.
 */

public class OFGameFilter extends BaseFilter {
    private final String TAG = OFGameFilter.class.getSimpleName();
    private static GameEventCallBack mCallBack;
    private boolean mUseGameFilter = false;
    private int mOPType = 0;
    private OrangeFilter.OF_FrameData mFrameData = null;

    private int mRequiredInputCnt = 1;
    private int mRequiredOutputCnt = 1;

    private OrangeFilter.OF_Texture[] mInputTextureArray = null;
    private OrangeFilter.OF_Texture[] mOutputTextureArray = null;

    public OFGameFilter() {
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

        if (mOutputTextureArray != null) {
            for (int i = 0; i < mRequiredOutputCnt; i++) {
                OpenGlUtils.deleteTexture(mOutputTextureArray[i].textureID);
            }

            mOutputTextureArray = null;
        }

        if (mFilterId != -1) {
            OrangeFilter.destroyGame(mOFContext, mFilterId);
            OrangeFilter.freeGameEventCallbackJsonListener(mFilterId);
            mCallBack = null;
            mFilterId = -1;
        }

        OpenGlUtils.checkGlError("destroy end");
        YYLog.info(TAG, "destroy");
    }

    @Override
    public String getFilterName() {
        return TAG;
    }

    @Override
    protected void updateParams() {
        Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();
        while (it.hasNext()) {
            OFGameParameter param = (OFGameParameter) it.next().getValue();
            mOPType = param.mOPType;

            if ((mOPType & FilterOPType.OP_SET_EFFECT_PATH) != 0) {
                updateParams(param.mGamePath);
            }

            if ((mOPType & FilterOPType.OP_START_GAME) != 0) {
                startGame();
            }

            if ((mOPType & FilterOPType.OP_PAUSE_GAME) != 0) {
                pauseGame();
            }

            if ((mOPType & FilterOPType.OP_RESUME_GAME) != 0) {
                resumeGame();
            }

            if ((mOPType & FilterOPType.OP_STOP_GAME) != 0) {
                stopGame();
            }

            if ((mOPType & FilterOPType.OP_SET_GAME_CALLBACK) != 0) {
                setGameEventCallback(param.mCallBack);
            }

            if ((mOPType & FilterOPType.OP_SEND_GAME_EVENT) != 0) {
                sendGameEventJson(param.mEventJson);
            }
        }
    }

    @Override
    public void changeSize(int newWidth, int newHeight) {
        super.changeSize(newWidth, newHeight);
        if (mOutputTextureArray != null) {
            for (int i = 0; i < mRequiredOutputCnt; i++) {
                OpenGlUtils.deleteTexture(mOutputTextureArray[i].textureID);
                int textureID = OpenGlUtils.createTexture(mOutputWidth, mOutputHeight);
                copyToOFTexture(textureID, GLES20.GL_TEXTURE_2D, GLES20.GL_RGBA, mOutputWidth, mOutputHeight, mOutputTextureArray[i]);
            }
        }
    }

    private void copyToOFTexture(int textureID, int target, int format, int width, int height, OrangeFilter.OF_Texture ofTexture) {
        ofTexture.textureID = textureID;
        ofTexture.target = target;
        ofTexture.format = format;
        ofTexture.width = width;
        ofTexture.height = height;
    }

    private void updateParams(String gameFilePath) {
        if (gameFilePath != null) {
            int indexOfSplit = gameFilePath.lastIndexOf(File.separator);
            if (indexOfSplit < 0) {
                YYLog.error(TAG, "gameFilePath is invalid:" + gameFilePath + ",just return!!!");
                return;
            }

            String gameFileDir = gameFilePath.substring(0, indexOfSplit);
            if (gameFilePath != null) {
                if (mFilterId <= 0) {
                    mFilterId = OrangeFilter.createGameFromFile(mOFContext, gameFilePath, gameFileDir);
                    YYLog.info(TAG, "setGameFilePath  GameId = " + mFilterId + " path =" + gameFilePath);

                    if (mFilterId <= 0) {
                        YYLog.error(TAG, "createGameFromFile failed.just return");
                        mUseGameFilter = false;
                        return;
                    }

                    mRequiredInputCnt = OrangeFilter.getRequiredInputCount(mOFContext, mFilterId);
                    if (mRequiredInputCnt > 0) {
                        mInputTextureArray = new OrangeFilter.OF_Texture[mRequiredInputCnt];
                        for (int i = 0; i < mRequiredInputCnt; i++) {
                            OrangeFilter.OF_Texture of_texture = new OrangeFilter.OF_Texture();
                            mInputTextureArray[i] = of_texture;
                        }
                        //dn't need the create a texture for input texture, it is assigned as input texture
                        //before apply frame.
                    }
                    mRequiredOutputCnt = OrangeFilter.getRequiredOutputCount(mOFContext, mFilterId);
                    if (mRequiredOutputCnt > 0) {
                        mOutputTextureArray = new OrangeFilter.OF_Texture[mRequiredOutputCnt];
                        for (int i = 0; i < mRequiredOutputCnt; i++) {
                            OrangeFilter.OF_Texture of_texture = new OrangeFilter.OF_Texture();
                            mOutputTextureArray[i] = of_texture;
                            int textureID = OpenGlUtils.createTexture(mOutputWidth, mOutputHeight);
                            copyToOFTexture(textureID, GLES20.GL_TEXTURE_2D, GLES20.GL_RGBA, mOutputWidth, mOutputHeight, mOutputTextureArray[i]);
                        }
                    }

                    YYLog.info(this, "---mRequiredInputCnt=" + mRequiredInputCnt + " mRequiredOutputCnt=" + mRequiredOutputCnt
                            + " mOutputWidth=" + mOutputWidth + " mOutputHeight=" + mOutputHeight);

                }
            } else {
                YYLog.info(TAG, "setGameFilePath  path  is null");
            }
            mUseGameFilter = true;
        } else {
            mUseGameFilter = false;
        }
    }

    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (mUseGameFilter) {
            storeOldFBO();
            mFrameData.faceFrameDataArr = sample.mFaceFrameDataArr;
            if (sample.mGestureFrameDataArr != null && sample.mGestureFrameDataArr.count > 0) {
                mFrameData.gestureFrameDataArr = new OrangeFilter.OF_GestureFrameData[sample.mGestureFrameDataArr.count];
                for (int i = 0; i < sample.mGestureFrameDataArr.count; i++) {
                    mFrameData.gestureFrameDataArr[i].type = sample.mGestureFrameDataArr.arr[i].type;
                    mFrameData.gestureFrameDataArr[i].x = sample.mGestureFrameDataArr.arr[i].x;
                    mFrameData.gestureFrameDataArr[i].y = sample.mGestureFrameDataArr.arr[i].y;
                    mFrameData.gestureFrameDataArr[i].width = sample.mGestureFrameDataArr.arr[i].width;
                    mFrameData.gestureFrameDataArr[i].height = sample.mGestureFrameDataArr.arr[i].height;
                }
            }
            mFrameData.bodyFrameDataArr = sample.mBodyFrameDataArr;
            //填充抠图数据
            mFrameData.segmentFrameData = sample.mSegmentFrameData;

            OrangeFilter.prepareFrameData(mOFContext, mOutputWidth, mOutputHeight, mFrameData);

            //jyq test
            /*if (sample.mExtraTextureId > 0) {
                ImageStorageUtil.save2DTextureToJPEG(sample.mExtraTextureId, mOutputWidth, mOutputHeight);
            }*/

            //根据of约定，当前输出的requiredTextureCnt的取值为1或者2，textureArray[0]始终为摄像头数据的纹理，texture[1]若存在，是mp4数据的纹理
            copyToOFTexture(sample.mTextureId, GLES20.GL_TEXTURE_2D, GLES20.GL_RGBA, mOutputWidth, mOutputHeight, mInputTextureArray[0]);
            if (mInputTextureArray.length > 1) {
                copyToOFTexture(sample.mExtraTextureId, GLES20.GL_TEXTURE_2D, GLES20.GL_RGBA, mOutputWidth, mOutputHeight, mInputTextureArray[1]);
            }

            OrangeFilter.applyFrame(mOFContext, mFilterId, mInputTextureArray, mOutputTextureArray);

            /*boolean hasFaceOrGesture = (mFrameData.faceFrameDataArr == null ? false : true);
            OrangeFilter.prepareFrameData(mOFContext, mOutputWidth, mOutputHeight, mFrameData);
            OrangeFilter.applyGameRGBA(mOFContext, mFilterId, mInputTextureArray[0].textureID, GLES20.GL_TEXTURE_2D, mOutputTextureArray[0].textureID,
                    GLES20.GL_TEXTURE_2D, 0, 0, mOutputWidth, mOutputHeight, hasFaceOrGesture == true ? mFrameData : null);
            super.drawToFrameBuffer(sample);  //编码路径需要用来截图.*/

            if (mRequiredOutputCnt == 1) {
                drawTextureToFrameBuffer(sample, mOutputTextureArray[0].textureID);  //sample.deliverToPreview = true as default.
                recoverOldFBO();
                deliverToDownStream(sample);
                return true;

            } else {
                for (int i = 0; i < mRequiredOutputCnt; i++) {
                    YYMediaSample outSample = YYMediaSampleAlloc.instance().alloc();
                    outSample.assigne(sample);
                    outSample.mTextureId = mOutputTextureArray[i].textureID;
                    outSample.mSampleType = SampleType.VIDEO;
                    outSample.mWidth = mOutputWidth;
                    outSample.mHeight = mOutputHeight;
                    outSample.mEncodeWidth = mOutputWidth;
                    outSample.mEncodeHeight = mOutputHeight;

                    /**sample.mDeliverToEncoder == true 表示录制开始了.*/
                    outSample.mDeliverToEncoder = (outSample.mDeliverToEncoder ? i == 0 : false);  //第1个编码，
                    outSample.mDeliverToPreview = (i == 1); //第2个预览.
                    outSample.mDeliverToSnapshot = outSample.mDeliverToEncoder;  //只能用来编码的纹理才能用来截图.
                    if (i == 0) {
                        drawTextureToFrameBuffer(outSample, outSample.mTextureId);  //用于截图.
                    }

                    recoverOldFBO();
                    deliverToDownStream(outSample);
                    outSample.decRef();
                }
            }
            return true;
        }

        deliverToDownStream(sample);
        return true;
    }


    public interface GameEventCallBack {
        void onEvent(String json);
    }

    public void setGameEventCallback(final GameEventCallBack callback) {
        mCallBack = callback;
        if (mCallBack != null) {
            OrangeFilter.setGameEventCallbackJsonListener(mFilterId, new OrangeFilter.GameEventCallbackJsonListener() {
                @Override
                public void onEvent(int gameId, String json) {
                    YYLog.info(TAG, "setGameEventCallback onEvent call");
                    mCallBack.onEvent(json);
                }
            });
        } else {
            YYLog.info(TAG, "setGameEventCallback callback null");
            OrangeFilter.freeGameEventCallbackJsonListener(mFilterId);
        }
    }

    public void startGame() {
        YYLog.info(TAG, "startGame GameId = " + mFilterId);
        if (mFilterId > 0) {
            OrangeFilter.startGame(mOFContext, mFilterId);
            mUseGameFilter = true;
        }
    }

    public void pauseGame() {
        YYLog.info(TAG, "pauseGame GameId = " + mFilterId);
        if (mFilterId > 0) {
            OrangeFilter.pauseGame(mOFContext, mFilterId);
        }
    }

    public void resumeGame() {
        YYLog.info(TAG, "resumeGame GameId = " + mFilterId);
        if (mFilterId > 0) {
            OrangeFilter.resumeGame(mOFContext, mFilterId);
        }

    }

    public void stopGame() {
        YYLog.info(TAG, "stopGame GameId = " + mFilterId);
        if (mFilterId > 0) {
            OrangeFilter.stopGame(mOFContext, mFilterId);
        }
    }

    public void sendGameEventJson(String json) {
        YYLog.info(TAG, "sendGameEventJson = " + json);
        if (mFilterId > 0) {
            OrangeFilter.sendGameEventJson(mOFContext, mFilterId, json);
        }
    }
}
