package com.ycloud.mediafilters;

import com.ycloud.mediarecord.RecordConfig;

/**
 * Created by DZHJ on 2017/9/14.
 */

public class AudioFilterContext {

    private RecordConfig   mRecordConfig = null;
    private AudioManager mAudioManager;

    public AudioFilterContext(){
        mAudioManager = new AudioManager();
    }

    public void setRecordConfig(RecordConfig mRecordConfig) {
        this.mRecordConfig = mRecordConfig;
    }

    public RecordConfig getRecordConfig() {
        return mRecordConfig;
    }

    public AudioManager getAudioManager(){
        return  mAudioManager;
    }
}
