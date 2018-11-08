//
// Created by Administrator on 2016/9/13.
//

#ifndef TRUNK_COMMON_H
#define TRUNK_COMMON_H

#include <string>
#include <stddef.h>

#include <android/log.h>
#define  LOG_TAG    "YYMediaFW"

//TODO. implment the LOG function classes.
#define  LOGDXXX(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)      __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)      __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)      __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGT(...)      __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)

#endif //TRUNK_COMMON_H
