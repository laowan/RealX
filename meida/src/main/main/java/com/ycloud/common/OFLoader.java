package com.ycloud.common;

import com.orangefilter.OrangeFilter;
import com.ycloud.utils.YYLog;

/**
 * Created by Kele on 2017/5/23.
 */

public class OFLoader {
    private static final String TAG = "OFLoader";
    private static boolean mIsNeedResume = false;

    public static int createOrangeFilterContext() {
        synchronized (OFLoader.class) {
            int context = OrangeFilter.createContext();
            YYLog.info(TAG, "createOrangeFilterContext context = " + context + ", thread id = " + Thread.currentThread().getId());
            return context;
        }
    }

    public static void destroyOrangeFilterContext(final int context) {
        synchronized (OFLoader.class) {
            if (context != -1) {
                OrangeFilter.destroyContext(context);
            }
            YYLog.info(TAG, "destroyOrangeFilterContext context = " + context + ", thread id = " + Thread.currentThread().getId());
        }
    }

    public static boolean getNeedResumeEffect() {
        synchronized (OFLoader.class) {
            return mIsNeedResume;
        }
    }

    public static void setNeedResumeEffect(boolean needResume) {
        synchronized (OFLoader.class) {
            mIsNeedResume = needResume;
            YYLog.info(TAG, "setNeedResumeEffect needResume=" + needResume);
        }
    }

    private static final class OF_Log implements OrangeFilter.OF_LogListener {
        @Override
        public void logCallBackFunc(String s) {
            if (s != null) {
                YYLog.info(TAG, "[OFSDK]:" + s);
            }
        }
    }

}
