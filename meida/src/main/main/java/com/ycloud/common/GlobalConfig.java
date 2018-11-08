package com.ycloud.common;

import com.ycloud.api.config.RecordContants;
import com.ycloud.api.config.RecordContants1080P;
import com.ycloud.api.config.RecordContants540P;
import com.ycloud.api.config.RecordContants540X720;
import com.ycloud.api.config.RecordContants720P;
import com.ycloud.api.config.RecordContants720X960;
import com.ycloud.api.config.ResolutionType;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全局配置类，主要存储sdk预设的参数，通常与单次录制，导出等无关的参数
 * Created by jinyongqing on 2017/11/24.
 */

public class GlobalConfig {
    private static GlobalConfig mGlobalConfig;

    /*全局配置的参数集*/
    private RecordContants mRecordConstant;

    /*初始化完成标识*/
    private AtomicBoolean mIsInit;

    public static synchronized GlobalConfig getInstance() {
        if (mGlobalConfig == null) {
            mGlobalConfig = new GlobalConfig();
        }
        return mGlobalConfig;
    }

    private GlobalConfig() {
        mRecordConstant = new RecordContants540X720();
        mIsInit = new AtomicBoolean(false);
    }

    /**
     * 使用resolutionType初始化，选择不同的Constant参数集
     *
     * @param resolutionType
     */
    public void init(ResolutionType resolutionType) {
        mIsInit.set(true);
        switch (resolutionType) {
            case R540P:
                mRecordConstant = new RecordContants540P();
                break;
            case R540X720:
                mRecordConstant = new RecordContants540X720();
                break;
            case R720P:
                mRecordConstant = new RecordContants720P();
                break;
            case R720X960:
                mRecordConstant = new RecordContants720X960();
                break;
            case R1080P:
                mRecordConstant = new RecordContants1080P();
                break;
            default:
                return;
        }
    }

    public boolean isInit() {
        return mIsInit.get();
    }

    public RecordContants getRecordConstant() {
        return mRecordConstant;
    }
}
