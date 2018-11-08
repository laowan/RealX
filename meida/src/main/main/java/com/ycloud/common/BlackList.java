package com.ycloud.common;

import com.ycloud.api.common.SDKCommonCfg;

/**
 * Created by Administrator on 2018/2/5.
 */

public class BlackList {

    private static String[] BlackList = {
            "HUAWEI C8813Q",
            "NX531J",
            "GT-I8552",
            "Lenovo A820t",
            "GT-N8000",
            "2013022",
            "2014811",
            "R823T",
            "N958St",
            "HUAWEI SC-CL00",
            "Lenovo A830",
            "Moto X Pro",
            "GiONEE_E6mini",
            "DOOV_DOOV S1",
            "oppo-x909",
            "X805",
            "M045",
            "M040",
            "M032",
            "M030",
            "GT-I9200",
            "vivo X6Plus D",
            "ASUS_Z00DUO",
            "vivo Y37A",
            "Coolpad A8-930",
            "Meitu M4",
            "Lenovo S898t",
            "vivo Y37",
            "vivo Y28",
            "OPPO A53",
            "R7Plusm",
            "SM-J7108",
            "vivo Y51A",
            "LEX651",
            "SM-N9100",
            "vivo Y67A",
            "CHE-TL00H",
            "HUAWEI_NXT-AL10",
            "OPPO R7",
            "Mi Note 2",
            "vivo X6Plus A",
            "m1 note"

    };

    private static String[] BlackListYoyi = {
            "Redmi 3",
    };

    private static String[] SoftwareDecoderList = {
//            "SM-J330G",
//            "SM-G610F",
    };


    public static boolean inBlack(String mode) {
        for (String str : BlackList) {
            if (str.equals(mode)) {
                return true;
            }
        }
        if (SDKCommonCfg.getRecordModePicture()) {
            for (String str : BlackListYoyi) {
                if (str.equals(mode)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean inSoftwareDecoderList(String mode) {
        for (String str : SoftwareDecoderList) {
            if (str.equals(mode)) {
                return true;
            }
        }
        return false;
    }
}
