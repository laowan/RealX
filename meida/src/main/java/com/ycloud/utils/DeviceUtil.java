package com.ycloud.utils;

import android.opengl.GLES20;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by kele on 2017/5/5.
 */

public class DeviceUtil {
    //android 获取当前手机型号
    public static String getPhoneModel() {
        Build bd = new Build();
        return  bd.MODEL;
    }

    public boolean isRoot() {
        String binPath = "/system/bin/su";
        String xBinPath = "/system/xbin/su";

        boolean isRooted = false;
        try {
            isRooted = new File(binPath).exists() && isExecutable(binPath)
                    || new File(xBinPath).exists() && isExecutable(xBinPath);
        }
        catch (Throwable e) {
            isRooted = false;
        }

        YYLog.info(this,"DeviceUtil::isRoot: " + isRooted);

        return isRooted;
    }

    private boolean isExecutable(String filePath) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("ls -l " + filePath);
            // 获取返回内容
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    p.getInputStream()));
            String str = in.readLine();
            YYLog.info(this, "DeviceUtil:isExecutable " + str);
            if (str != null && str.length() >= 4) {
                char flag = str.charAt(3);
                if (flag == 's' || flag == 'x') {
                    return true;
                }
            }
        } catch (IOException e) {
            YYLog.info(this, "DeviceUtil: isExecutable failed:" + e.getMessage());
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
        return false;
    }
}
