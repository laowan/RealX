package com.ycloud.api.process;

import android.graphics.Bitmap;

/**
 * Created by Administrator on 2018/5/21.
 */

public interface ImageProcessListener {
    void onProcessFinish(Bitmap bitmap, String path, int hash);
}
