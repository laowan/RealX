//[added by wangfei team]
#ifndef _UTILS_JNI_H_
#define _UTILS_JNI_H_

#include <unistd.h>
#include <pthread.h>

#ifdef __ANDROID__
#include <jni.h>
#include <android/log.h>
#include <libavutil/time.h>

void avcodec_register_all_by_android();
void pass_jvm(JavaVM *vm); /*BY ZB*/
int jni_get_env(JNIEnv **env);
int jni_attach_thread(JNIEnv **env, const char *thread_name);
void jni_detach_thread();


#ifndef __MAX
#   define __MAX(a, b)   ( ((a) > (b)) ? (a) : (b) )
#endif
#ifndef __MIN
#   define __MIN(a, b)   ( ((a) < (b)) ? (a) : (b) )
#endif

////////////////////////////////////////////////////////////////

#include <pthread.h>
typedef pthread_t       vlc_thread_t;
typedef pthread_mutex_t vlc_mutex_t;
#define VLC_STATIC_MUTEX PTHREAD_MUTEX_INITIALIZER

vlc_mutex_t* get_android_opaque_mutex();
void vlc_mutex_lock (vlc_mutex_t *p_mutex);
int vlc_mutex_trylock (vlc_mutex_t *p_mutex);
void vlc_mutex_unlock (vlc_mutex_t *p_mutex);

#endif

void setHWDecoderPixFmt(const char *pixfmt);
//void setHWGLESDecoderSurfaceContext(jobject context);

//#define LOG_DEBUG
#if (defined LOG_DEBUG) && (defined __ANDROID__)

//  #define DEBUG_H264_FILE_OUTPUT
#define DEBUG_YUV_FILE_OUTPUT
//#define DEBUG_COST

#define LOG_DEFINE(t) static const char *sLoggerTag = t

#define INFO(...)       __android_log_print(ANDROID_LOG_INFO, sLoggerTag, __VA_ARGS__)
#define ERROR(...)      __android_log_print(ANDROID_LOG_ERROR, sLoggerTag, __VA_ARGS__)
#define WARN(...)       __android_log_print(ANDROID_LOG_WARN, sLoggerTag, __VA_ARGS__)
#define DEBUG(...)     __android_log_print(ANDROID_LOG_DEBUG, sLoggerTag, __VA_ARGS__)
#define VERBOSE(...)    __android_log_print(ANDROID_LOG_VERBOSE, sLoggerTag, __VA_ARGS__)
//#undef VERBOSE
//#define VERBOSE(...) (void)0

#ifdef DEBUG_COST

#define COSTTIME_BEGIN(func)  static int64_t func##_call_total = 0; int64_t func##_call_begin = av_gettime()
#define COSTTIME_END(func)    int64_t func##_call_cost = av_gettime() - func##_call_begin;                      \
                             func##_call_total += func##_call_cost; __android_log_print(ANDROID_LOG_DEBUG, "COST", #func " cost %lld μs. total=%lld μs",   \
                              func##_call_cost, func##_call_total)

#define COSTTIME_STEP_BEGIN()  static int64_t func_call_total = 0; int64_t func_step_call_begin = av_gettime()

#define COSTTIME_STEP_END(step)    static int64_t step##_fun_call_step_total = 0;      \
                                    int64_t step##_call_cost = av_gettime() - func_step_call_begin;                              \
                                    step##_fun_call_step_total += step##_call_cost;      \
                                    func_call_total += step##_call_cost;                                                   \
                                    func_step_call_begin += step##_call_cost;                                                   \
                                    __android_log_print(ANDROID_LOG_DEBUG, "COST", #step " cost %lld μs. step total=%lld μs, total=%lld μs",        \
                                    step##_call_cost, step##_fun_call_step_total, func_call_total)

#else //DEBUG_COST

#define COSTTIME_BEGIN(func) (void)0
#define COSTTIME_END(func) (void)0
#define COSTTIME_STEP_BEGIN() (void)0
#define COSTTIME_STEP_END(step) (void)0

/*DEBUG_COST*/
#endif

#else

#define COSTTIME_BEGIN(func) (void)0
#define COSTTIME_END(func) (void)0
#define COSTTIME_STEP_BEGIN() (void)0
#define COSTTIME_STEP_END(step) (void)0

#define LOG_DEFINE(t)   /*(void)0*/
#define VERBOSE(...)    (void)0
//#define INFO(...)       (void)0
//#define WARN(...)       (void)0
#define VERBOSE(...)    (void)0
//#define ERROR(...)      (void)0
#define DEBUG(...)      (void)0

#define INFO(...)       __android_log_print(ANDROID_LOG_INFO, "HW_DE", __VA_ARGS__)
#define ERROR(...)      __android_log_print(ANDROID_LOG_ERROR, "HW_DE", __VA_ARGS__)
#define WARN(...)       __android_log_print(ANDROID_LOG_WARN, "HW_DE", __VA_ARGS__)

#endif

#define VLC_TS_INVALID INT64_C(0)
#define VLC_TS_0 INT64_C(1)

#define CLOCK_FREQ INT64_C(1000000)

/*****************************************************************************
 * Interface configuration
 *****************************************************************************/

/* Base delay in micro second for interface sleeps */
#define INTF_IDLE_SLEEP                 (CLOCK_FREQ/20)

/*****************************************************************************
 * Input thread configuration
 *****************************************************************************/

/* Used in ErrorThread */
#define INPUT_IDLE_SLEEP                (CLOCK_FREQ/10)

/*
 * General limitations
 */

/* Duration between the time we receive the data packet, and the time we will
 * mark it to be presented */
#define DEFAULT_PTS_DELAY               (3*CLOCK_FREQ/10)

/*****************************************************************************
 * SPU configuration
 *****************************************************************************/

/* Buffer must avoid arriving more than SPU_MAX_PREPARE_TIME in advanced to
 * the SPU */
#define SPU_MAX_PREPARE_TIME            (CLOCK_FREQ/2)

/*****************************************************************************
 * Video configuration
 *****************************************************************************/

/*
 * Default settings for video output threads
 */

/* Multiplier value for aspect ratio calculation (2^7 * 3^3 * 5^3) */
#define VOUT_ASPECT_FACTOR              432000

/* Maximum width of a scaled source picture - this should be relatively high,
 * since higher stream values will result in no display at all. */
#define VOUT_MAX_WIDTH                  4096

/* Number of planes in a picture */
#define VOUT_MAX_PLANES                 5

/*
 * Time settings
 */

/* Time to sleep when waiting for a buffer (from vout or the video fifo).
 * It should be approximately the time needed to perform a complete picture
 * loop. Since it only happens when the video heap is full, it does not need
 * to be too low, even if it blocks the decoder. */
#define VOUT_OUTMEM_SLEEP               (CLOCK_FREQ/50)

/* The default video output window title */
#define VOUT_TITLE                      "VLC"

/*****************************************************************************
 * Messages and console interfaces configuration
 *****************************************************************************/

/* Maximal depth of the object tree output by vlc_dumpstructure */
#define MAX_DUMPSTRUCTURE_DEPTH         100

#endif 
//[added by wangfei team end]
