package com.ycloud.audio;

/**
 * Created by Administrator on 2018/3/22.
 */

public class ErasureAudioPlayer extends AudioPlayer {

    private long mStartPositionMS = 0;
    private long mStopPositionMS = AudioPlayEditor.kMAX_EXPORT_SIZE_MS;
    private boolean mIsStop;

    public ErasureAudioPlayer(int ID) {
        super(ID);
    }

    @Override
    public void release() {

    }

    @Override
    public void start(long startPlayPositionInMS) {
        mStartPositionMS = startPlayPositionInMS;
        mIsStop = false;
    }

    @Override
    public void stop(long stopPlayPositionInMS) {
        if (!mIsStop) {
            mStopPositionMS = stopPlayPositionInMS;
            mIsStop = true;
        }
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void seek(long positionInMS) {

    }

    @Override
    public boolean isFinish(long playPositionMS) {
        return true;
    }

    @Override
    public int read(byte[] buffer, int requestLen, long playPositionMS) {
        return 0;
    }

    @Override
    public boolean isErasure(long playPositionMS) {
        if (Float.compare(mVolume, 0.0f) != 0 && playPositionMS >= mStartPositionMS && playPositionMS < mStopPositionMS) {
            return true;
        }
        return false;
    }
}
