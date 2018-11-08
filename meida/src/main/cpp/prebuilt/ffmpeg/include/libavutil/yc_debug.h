#pragma once
/*
 * yc_log.h
 *
 *  Created on: 2016-11-01
 *      Author: zhangbin1@yy.com
 */

/** ANDROID */
#if defined(__ANDROID__)
#include <android/log.h>

#ifdef USE_YCMEDIA_DEBUG /*enable it in ffmpeg configure BY ZB*/
	#define YC_DEBUG_LOG 1
#else
	#define YC_DEBUG_LOG 0
#endif


#endif
