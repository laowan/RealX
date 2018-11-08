package com.ycloud.audio;

import java.util.Arrays;

/**
 * Created by Administrator on 2018/3/22.
 */

public class EffectAudioPlayer extends FingerMagicAudioPlayer {
    private boolean mIsErasure = false;

    public EffectAudioPlayer(int ID) {
        super(ID);
    }

    @Override
    public int read(byte[] buffer, int reqLen, long curPlayPosition) {
        int len = super.read(buffer, reqLen, curPlayPosition);
        if (mIsErasure && len > 0) {
            Arrays.fill(buffer, (byte) 0);
        }
        return len;
    }


    @Override
    public boolean isErasure(long playPositionMS) {
        return mState == PLAY_STATE.PLAY_STATE_PLAYING && Float.compare(mVolume, 0.0f) != 0;
    }

    @Override
    public void setErasure(boolean isErasure) {
        mIsErasure = isErasure;
    }
}
