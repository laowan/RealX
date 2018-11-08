package com.ycloud.audio;

/**
 * Created by Administrator on 2018/1/22.
 */

public abstract class  AudioPlayer {
    float mVolume = 1.0f;
    int mID;

    public AudioPlayer(int ID) {
        mID = ID;
    }

    public  abstract  void release();

    public int ID() {
        return mID;
    }

    /**
     *
     * @param startPlayPositionInMS, the position offset that  relative to AudioPlayEditor play position
     */
    public abstract  void start(long startPlayPositionInMS);


    /**
     *
     * @param stopPlayPositionInMS the position offset that  relative to AudioPlayEditor play position
     */
    public abstract  void stop(long stopPlayPositionInMS);

    public abstract  void pause();

    public abstract  void resume();

    public abstract  void seek(long positionInMS);

    public long getPlayPositionInMS() { return 0; }

    public void setVolume(float volume) {
        mVolume = volume;
    }

    public float getVolume() {
        return mVolume;
    }

    public abstract  boolean isFinish(long playPositionMS);

    public abstract int read(byte[]buffer, int requestLen, long playPositionMS);

    public void setErasure(boolean isErasure) {}

    public boolean isErasure(long playPositionMS) {
        return false;
    }
}
