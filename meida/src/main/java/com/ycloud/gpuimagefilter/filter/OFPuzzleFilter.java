package com.ycloud.gpuimagefilter.filter;

import android.opengl.GLES20;

import com.orangefilter.OrangeFilter;
import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.param.PuzzleFilterParameter;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;
import com.ycloud.ymrmodel.YYMediaSample;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by jinyongqing on 2017/12/6.
 */

public class OFPuzzleFilter extends BaseFilter {
    private final String TAG = OFPuzzleFilter.class.getSimpleName();

    private OrangeFilter.OF_FrameData mFrameData = null;
    private boolean mIsUsePuzzle = false;
    private String mLastPuzzleDirectory = "";
    private long mStartPtsMs = -1;


    public OFPuzzleFilter() {
        super();
    }

    @Override
    public void init(int outputWidth, int outputHeight, boolean isExtTexture, int oFContext) {
        OpenGlUtils.checkGlError("init start");
        super.init(outputWidth, outputHeight, isExtTexture, oFContext);
        OpenGlUtils.checkGlError("init end");
        YYLog.info(TAG, "init outputWidth=" + outputWidth + " outputHeight=" + outputHeight);
    }

    @Override
    public void destroy() {
        OpenGlUtils.checkGlError("destroy start");
        super.destroy();

        if (mFilterId != -1) {
            OrangeFilter.destroyEffect(mOFContext, mFilterId);
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
    public void updateParams() {
        //拼图参数不为空
        if (mFilterInfo.mFilterConfigs != null && !mFilterInfo.mFilterConfigs.entrySet().isEmpty()) {
            mIsUsePuzzle = true;

            Iterator<Map.Entry<Integer, BaseFilterParameter>> it = mFilterInfo.mFilterConfigs.entrySet().iterator();

            while (it.hasNext()) {
                BaseFilterParameter param = it.next().getValue();
                String puzzleDirectory = ((PuzzleFilterParameter) (param)).mPuzzleDirectory;
                setOrangeFilterParams(puzzleDirectory);
                YYLog.info(TAG, "OFPuzzleFilter updateParams:" + puzzleDirectory);
            }
        } else {
            mIsUsePuzzle = false;
        }
    }


    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        if (true == mIsUsePuzzle) {
            if (mStartPtsMs == -1) {
                mStartPtsMs = sample.mTimestampMs;
                OrangeFilter.pauseEffectAnimation(mOFContext, mFilterId);
            }

            if (sample.mTimestampMs < mStartPtsMs) {
                mStartPtsMs = sample.mTimestampMs;
            }

            storeOldFBO();

            boolean hasFace = mFrameData == null ? false : true;
            int delta = (int) (sample.mTimestampMs - mStartPtsMs);
            int pts = delta > 0 ? delta : 0;
            OrangeFilter.seekEffectAnimation(mOFContext, mFilterId, pts);
            OrangeFilter.prepareFrameData(mOFContext, mOutputWidth, mOutputHeight, mFrameData);
            OrangeFilter.applyEffect(mOFContext, mFilterId, sample.mTextureId, GLES20.GL_TEXTURE_2D, mTextures[0],
                    GLES20.GL_TEXTURE_2D, 0, 0, mOutputWidth, mOutputHeight, hasFace == true ? mFrameData : null);

            super.drawToFrameBuffer(sample);
            recoverOldFBO();
        }

        deliverToDownStream(sample);
        return true;
    }

    private void setOrangeFilterParams(String puzzleDirectory) {
        if (puzzleDirectory != null) {
            if (mLastPuzzleDirectory.equals(puzzleDirectory)) {
                return;
            }

            YYLog.debug(this, "OFPuzzleFilter.setOrangeFilterParams");

            int indexOfSplit = puzzleDirectory.lastIndexOf("/");
            if (indexOfSplit < 0) {
                YYLog.error(TAG, "Puzzle filter param is invalid:" + puzzleDirectory + ",just return!!!");
                return;
            }

            String dir = puzzleDirectory.substring(0, puzzleDirectory.lastIndexOf("/"));
            if (-1 == mFilterId) {
                mFilterId = OrangeFilter.createEffectFromFile(mOFContext, puzzleDirectory, dir);
                if(mFilterId <= 0) {
                    YYLog.error(this, "0FPuzzleFilter.setOrangeFilterParameter, OrangeFilter.createEffectFromFile fail: " + mFilterId);
                }
            } else {
                OrangeFilter.updateEffectFromFile(mOFContext, mFilterId, puzzleDirectory, dir);
            }

            if(mFilterId <=0 ) {
                YYLog.error(this, "setOrangeFilerParams fail:  " + mFilterId);
                mIsUsePuzzle = false;
            } else {
                mLastPuzzleDirectory = puzzleDirectory;
                mIsUsePuzzle = true;
            }
        } else {
            mIsUsePuzzle = false;
        }
    }

}
