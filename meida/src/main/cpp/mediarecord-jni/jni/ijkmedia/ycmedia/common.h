/*
 * common.h
 *
 *  Created on: Nov 19, 2014
 *      Author: huangwanzhang
 */

#ifndef COMMON_H_
#define COMMON_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>  // C99
#include <limits.h>
#include <time.h>
#include <pthread.h>


#ifdef CC_ANDROID
#include <android/log.h>
#include <jni.h>
#endif

#include <libffmpeg_event.h>

//#define CC_ANDROID  /*enable android log*/
//#define CC_DEBUG  /*enable android log*/

#ifndef MAX_PATH
#define MAX_PATH 260
#endif

#define LOG_MAX_BUFFER_SIZE                     (1024*4)
#define LOG_TAG                                 "libycmedia"

typedef void* HANDLE;

int64_t getcurrenttime_us();

	#define ALOGD(fmt, args...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, fmt, ##args))
	#define ALOGI(fmt, args...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, fmt, ##args))
	#define ALOGW(fmt, args...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, fmt, ##args))
	#define ALOGE(fmt, args...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##args))

    /* warning: not support nested quotations*/
    char ** argv_create(const char* cmd, int* count);
    void argv_free(char **argv, int argc);


    int create_lock(pthread_mutex_t *pmutex);

    int lock(pthread_mutex_t *pmutex) ;

    int unlock(pthread_mutex_t *pmutex);

    int destroy_lock(pthread_mutex_t *pmutex);


#ifdef __cplusplus
};
#endif

#endif /* COMMON_H_ */
