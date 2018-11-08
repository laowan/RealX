//
//  libffmpeg_event.h
//
//  Created by baohonglai on 15/7/1.
//  Copyright (c) 2015  baohonglai All rights reserved.
//

#ifndef _LIBFFMPEG_EVENT_H_
#define _LIBFFMPEG_EVENT_H_

#include <jni.h>
#include <stdint.h>
typedef enum {
    libffmpeg_cmd_snapshot_multiple =2,
    libffmpeg_cmd_video_concat =3 ,
    libffmpeg_cmd_transcode =6 ,
	libffmpeg_cmd_video_effect = 8,
	libffmpeg_cmd_video_cut = 9,
} libffmpeg_command_type;

typedef struct libffmpeg_event_t {
    libffmpeg_command_type type;
    int64_t frame_pts;
    int frame_num;
} libffmpeg_event_t;

typedef void (*FFmpegEventCB)(libffmpeg_event_t *event) ;

typedef struct FFmpegCtx {
    libffmpeg_command_type      cmd_type;
    FFmpegEventCB               ffmpeg_event_cb;
    int isUseGpuFilter;
    jobject videGpuFilter;
} FFmpegCtx;

void ffmpeg_event_callback(const libffmpeg_event_t *ev);
typedef struct {
    FFmpegCtx *excontext;
}libffmpeg_instance_t;
#endif // _LIBFFMPEG_EVENT_H_