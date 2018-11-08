//
// Created by Administrator on 2017/7/27.
//
#include <stdlib.h>
#include <string.h>
extern "C" {
#include "libavutil/error.h"
#include "libavformat/avformat.h"
#include "libswresample/swresample.h"
};
#undef YY_LOGTAG
#define YY_LOGTAG "NativeFfmpeg"
#include "YYJni.h"

#define FRAME_TYPE_PCM  1
#define FRAME_TYPE_I420 2
#define FRAME_TYPE_AAC  5
#define FRAME_TYPE_H264 6
#define FRAME_TYPE_HEVC 7

typedef struct _FfmpegPriv
{
    jweak objweak;
    AVCodec* codec;
    AVCodecContext* codecContext;
    AVFrame* outFrame;
    SwrContext* swrCtx;
    int sampleRate;
    int samples, channels;
    int width, height;
    int planeWidth, planeHeight;
    int planeSize, dataLen;
    bool mFlushTrigged;
} FfmpegPriv;

static const char* const kNativeFfmpegPath = "com/ycloud/svplayer/NativeFfmpeg";
static const char* const kMediaInfoPath = "com/ycloud/svplayer/MediaInfo";

static jmethodID methodID_ByteBuffer_position_I = NULL;
static jmethodID methodID_ByteBuffer_position_V = NULL;
static jmethodID methodID_ByteBuffer_limit_I = NULL;
static jmethodID methodID_ByteBuffer_limit_V = NULL;

JNI_DEFINE_METHODID(MediaFormat, getInteger);
JNI_DEFINE_METHODID(MediaFormat, getByteBuffer);

JNI_DEFINE_FIELDID(MediaInfo, type);
JNI_DEFINE_FIELDID(MediaInfo, width);
JNI_DEFINE_FIELDID(MediaInfo, height);
JNI_DEFINE_FIELDID(MediaInfo, planeWidth);
JNI_DEFINE_FIELDID(MediaInfo, planeHeight);
JNI_DEFINE_FIELDID(MediaInfo, planeSize);
JNI_DEFINE_FIELDID(MediaInfo, sampleRate);
JNI_DEFINE_FIELDID(MediaInfo, samples);
JNI_DEFINE_FIELDID(MediaInfo, channels);
JNI_DEFINE_FIELDID(MediaInfo, dataLen);

JNI_DEFINE_FIELDID(NativeFfmpeg, mNativeHandle);
JNI_DEFINE_METHODID(NativeFfmpeg, onFormatChanged);

static int read_int32(const uint8_t* data)
{
    unsigned int value = (*data++ << 24) & 0xff000000;
    value |= (*data++ << 16) & 0xff0000;
    value |= (*data++ << 8) & 0xff00;
    value |= *data & 0xff;
    return (int)value;
}

static void copy_yuv_frame(const AVFrame* frame, char* data, int planeSize)
{
    memcpy(data, frame->data[0], planeSize);
    data += planeSize;

    planeSize >>= 2;
    memcpy(data, frame->data[1], planeSize);
    data += planeSize;
    memcpy(data, frame->data[2], planeSize);
}

static char* get_byteBuffer_data(JNIEnv* env, jobject obj, int* position)
{
    *position = env->CallIntMethod(obj, methodID_ByteBuffer_position_V);
    return (char*)env->GetDirectBufferAddress(obj);
}

static jobject create_video_info(JNIEnv* env, FfmpegPriv* priv)
{
    jclass clazz = env->FindClass(kMediaInfoPath);
    jobject object = env->AllocObject(clazz);

    env->SetIntField(object, JNI_FIELDID(MediaInfo, type), (int)FRAME_TYPE_I420);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, width), priv->width);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, height), priv->height);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, planeWidth), priv->planeWidth);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, planeHeight), priv->planeHeight);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, planeSize), priv->planeSize);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, dataLen), priv->dataLen);
    DELETE_LOCAL_REF(env, clazz);
    return object;
}

static jobject create_audio_info(JNIEnv* env, FfmpegPriv* priv)
{
    jclass clazz = env->FindClass(kMediaInfoPath);
    jobject object = env->AllocObject(clazz);

    env->SetIntField(object, JNI_FIELDID(MediaInfo, type), (int)FRAME_TYPE_PCM);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, sampleRate), priv->sampleRate);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, samples), priv->samples);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, channels), priv->channels);
    env->SetIntField(object, JNI_FIELDID(MediaInfo, dataLen), priv->dataLen);
    DELETE_LOCAL_REF(env, clazz);
    return object;
}

static void init_codecContext_withFormat(JNIEnv* env, AVCodecContext* codecContext, jobject format)
{
    const char* txt = NULL;
    jstring object = NULL;
    jobject extra_data = NULL;

    if(codecContext->codec_id == AV_CODEC_ID_AAC)
    {
        object = env->NewStringUTF(txt = "sample-rate");
        codecContext->sample_rate = env->CallIntMethod(format, JNI_METHODID(MediaFormat, getInteger), object);
        DELETE_LOCAL_REF(env, object);
        object = env->NewStringUTF(txt = "channel-count");
        codecContext->channels = env->CallIntMethod(format, JNI_METHODID(MediaFormat, getInteger), object);
        DELETE_LOCAL_REF(env, object);
        object = env->NewStringUTF(txt = "aac-profile");
        codecContext->profile = env->CallIntMethod(format, JNI_METHODID(MediaFormat, getInteger), object);
        DELETE_LOCAL_REF(env, object);
        object = env->NewStringUTF(txt = "channel-layout");
        codecContext->channel_layout = env->CallIntMethod(format, JNI_METHODID(MediaFormat, getInteger), object);
        DELETE_LOCAL_REF(env, object);
        object = env->NewStringUTF(txt = "sample-fmt");
        codecContext->sample_fmt = (AVSampleFormat)env->CallIntMethod(format, JNI_METHODID(MediaFormat, getInteger), object);
        DELETE_LOCAL_REF(env, object);
        object = env->NewStringUTF(txt = "csd-0");
        if((extra_data = env->CallObjectMethod(format, JNI_METHODID(MediaFormat, getByteBuffer), object)) != NULL)
        {
            codecContext->extradata_size = env->GetDirectBufferCapacity(extra_data);
            codecContext->extradata = (uint8_t*)av_malloc(codecContext->extradata_size);
            memcpy(codecContext->extradata, env->GetDirectBufferAddress(extra_data), codecContext->extradata_size);
            codecContext->flags |= CODEC_FLAG_GLOBAL_HEADER;
        }
        else
        {
            YYLOGD("### WARNING ### extradata is not set.", 0);
        }
        DELETE_LOCAL_REF(env, object);
        DELETE_LOCAL_REF(env, extra_data);
        YYLOGD("audio codec: profile=%d, channels=%d, channle_layout=%lld, sample_fmt=%d, sample_rate=%d, extradata:%p, %d", codecContext->profile, codecContext->channels, codecContext->channel_layout, codecContext->sample_fmt, codecContext->sample_rate, codecContext->extradata, codecContext->extradata_size);
    }
    else if(codecContext->codec_id == AV_CODEC_ID_H264 || codecContext->codec_id == AV_CODEC_ID_HEVC)
    {
        codecContext->thread_count = 2;
        object = env->NewStringUTF(txt = "width");
        codecContext->width = env->CallIntMethod(format, JNI_METHODID(MediaFormat, getInteger), object);
        DELETE_LOCAL_REF(env, object);
        object = env->NewStringUTF(txt = "height");
        codecContext->height = env->CallIntMethod(format, JNI_METHODID(MediaFormat, getInteger), object);
        DELETE_LOCAL_REF(env, object);
        object = env->NewStringUTF(txt = "extra-data");
        if((extra_data = env->CallObjectMethod(format, JNI_METHODID(MediaFormat, getByteBuffer), object)) != NULL)
        {
            codecContext->extradata_size = env->GetDirectBufferCapacity(extra_data);
            codecContext->extradata = (uint8_t*)calloc(1, codecContext->extradata_size + FF_INPUT_BUFFER_PADDING_SIZE);
            memcpy(codecContext->extradata, env->GetDirectBufferAddress(extra_data), codecContext->extradata_size);
            codecContext->flags |= CODEC_FLAG_GLOBAL_HEADER;
        }
        else
        {
            YYLOGD("### WARNING ### extradata is not set.", 0);
        }
        DELETE_LOCAL_REF(env, object);
        DELETE_LOCAL_REF(env, extra_data);
        YYLOGD("video codec:%d, size:%dx%d, extradata:%p, %d", codecContext->codec_id, codecContext->width, codecContext->height, codecContext->extradata, codecContext->extradata_size);
    }
}

static int video_check_extradata(const AVCodecContext* codecContext, const uint8_t* data)
{
    int result = 0;

    if((result = read_int32(data)) > 2048 || result <= 0)
    {
        YYLOGD("### WARNING ### not available extra-data. read_int32() = %d", result);
    }
    if(codecContext->extradata == NULL || result != codecContext->extradata_size)
    {
        return 1;
    }
    return memcmp(data + 4, codecContext->extradata, codecContext->extradata_size) ? 1 : 0;
}

static int video_update_extradata(FfmpegPriv* priv, const uint8_t* data)
{
    int result = 0;
    AVCodecContext* codecContext = priv->codecContext;

    if(video_check_extradata(codecContext, data))
    {
        result = read_int32(data);
        YYLOGD("extradata changed. extradata_size: %d -> %d", codecContext->extradata_size, result);

        if((codecContext = avcodec_alloc_context3(priv->codec)) == NULL)
        {
            YYLOGD("avcodec_alloc_context3() failed.", 0);
            return -1;
        }
        SAFE_FREE(priv->codecContext->extradata);
        avcodec_free_context(&priv->codecContext);
        priv->codecContext = codecContext;

        codecContext->extradata_size = result;
        codecContext->extradata = (uint8_t*)calloc(1, codecContext->extradata_size + FF_INPUT_BUFFER_PADDING_SIZE);
        memcpy(codecContext->extradata, data + 4, codecContext->extradata_size);
        codecContext->flags |= CODEC_FLAG_GLOBAL_HEADER;

        if(avcodec_open2(codecContext, priv->codec, NULL) < 0)
        {
            avcodec_free_context(&priv->codecContext);
            YYLOGD("avcodec_open() failed.", 0);
            return -1;
        }
        priv->width = priv->height = 0;
        priv->planeWidth = priv->planeHeight = 0;
        priv->planeSize = priv->dataLen = 0;
    }
    return result;
}

static jint decode_video_frame(JNIEnv* env, FfmpegPriv* priv, jobject input, jobject output, jboolean keyFrame)
{
    int result = -1;
    AVPacket pkt;
    char* data = NULL;
    char log[AV_ERROR_MAX_STRING_SIZE] = {0};

    av_init_packet(&pkt);
    pkt.flags = keyFrame ? AV_PKT_FLAG_KEY : 0;
    pkt.data = (uint8_t*)get_byteBuffer_data(env, input, &pkt.size);
    //YYLOGD("ByteBuffer = %p, %d", pkt.data, pkt.size);

    if(keyFrame)
    {
        //video_update_extradata(priv, pkt.data);
    }
    if((result = avcodec_send_packet(priv->codecContext, &pkt)) < 0)
    {
        YYLOGD("avcodec_send_packet() video failed. result:0x%08x, %s", result, av_make_error_string(log, AV_ERROR_MAX_STRING_SIZE, result));
        return -1;
    }
    if((result = avcodec_receive_frame(priv->codecContext, priv->outFrame)) == AVERROR(EAGAIN))
    {
        /*
         *  AVERROR(EAGAIN):   output is not available in this state - user must try to send new input
         */
        return 0;
    }
    else if(result < 0)
    {
        YYLOGD("avcodec_receive_frame() failed. result:0x%08x, %s", result, av_make_error_string(log, AV_ERROR_MAX_STRING_SIZE, result));
        return -1;
    }
    if(priv->width != priv->codecContext->width || priv->height != priv->codecContext->height)
    {
        priv->width = priv->codecContext->width;
        priv->height = priv->codecContext->height;
        priv->planeWidth = priv->outFrame->linesize[0];
        priv->planeHeight = priv->height;
        priv->planeSize = priv->planeWidth * priv->planeHeight;
        priv->dataLen = (priv->planeSize * 3) >> 1;
        YYLOGD("profile:%d, %s, level:%d", priv->codecContext->profile, av_get_profile_name(priv->codec, priv->codecContext->profile), priv->codecContext->level);
        YYLOGD("decode result. frameSize:%dx%d, planeSize:%dx%d, dataLen:%d", priv->width, priv->height, priv->planeWidth, priv->planeHeight, priv->dataLen);
        jobject object = create_video_info(env, priv);
        env->CallVoidMethod(priv->objweak, JNI_METHODID(NativeFfmpeg, onFormatChanged), object);
        DELETE_LOCAL_REF(env, object);
    }
    if((data = (char*)env->GetDirectBufferAddress(output)) == NULL)
    {
        YYLOGD("direct buffer address is not accessable.", 0);
        return -1;
    }
    if((result = env->GetDirectBufferCapacity(output)) < priv->dataLen)
    {
        YYLOGE("capacity of output buffer is not enough. requested:%d, capacity:%d, retry with new buffer", priv->dataLen, result);
        //return -2 as s specific ret num indicate retry is needed
        return -2;
    }
    copy_yuv_frame(priv->outFrame, data, priv->planeSize);
    env->CallObjectMethod(output, methodID_ByteBuffer_position_I, 0);
    env->CallObjectMethod(output, methodID_ByteBuffer_limit_I, priv->dataLen);
    return 1;
}

static jint decode_audio_frame(JNIEnv* env, FfmpegPriv* priv, jobject input, jobject output)
{
    int result = -1;
    AVPacket pkt;
    char* data[3] = {NULL, NULL, NULL};
    char log[AV_ERROR_MAX_STRING_SIZE] = {0};

    av_init_packet(&pkt);
    //pkt.flags = AV_PKT_FLAG_KEY;
    pkt.data = (uint8_t*)get_byteBuffer_data(env, input, &pkt.size);
    //YYLOGD("ByteBuffer = %p, %d", pkt.data, pkt.size);

    if((result = avcodec_send_packet(priv->codecContext, &pkt)) < 0)
    {
        YYLOGD("avcodec_send_packet() audio failed. result:0x%08x, %s", result, av_make_error_string(log, AV_ERROR_MAX_STRING_SIZE, result));
        return -1;
    }
    if((result = avcodec_receive_frame(priv->codecContext, priv->outFrame)) == AVERROR(EAGAIN))
    {
        /*
         *  AVERROR(EAGAIN):   output is not available in this state - user must try to send new input
         */
        return 0;
    }
    else if(result < 0)
    {
        YYLOGD("avcodec_receive_frame() failed. result:0x%08x, %s", result, av_make_error_string(log, AV_ERROR_MAX_STRING_SIZE, result));
        return -1;
    }

    //YYLOGD("codecContext. sampleRate:%d, channels:%d, sample_fmt:%d", priv->codecContext->sample_rate, priv->codecContext->channels, priv->codecContext->sample_fmt);
    //YYLOGD("outFrame. sampleRate:%d, channels:%lld, %d, nb_samples:%d, %d", priv->outFrame->sample_rate, priv->outFrame->channel_layout, priv->outFrame->channels, priv->outFrame->nb_samples, priv->outFrame->format);
    //YYLOGD("outFrame. %p, %p, %d, %d", priv->outFrame->data[0], priv->outFrame->data[1], priv->outFrame->linesize[0], priv->outFrame->linesize[1]);

    if(priv->sampleRate != priv->codecContext->sample_rate || priv->channels != priv->codecContext->channels)
    {
        priv->sampleRate = priv->codecContext->sample_rate;
        priv->samples = priv->outFrame->nb_samples;
        priv->channels = priv->codecContext->channels;
        priv->dataLen = (priv->samples << 1) * priv->channels;
        if(priv->swrCtx != NULL)
        {
            swr_close(priv->swrCtx);
            swr_free(&priv->swrCtx);
        }
        priv->swrCtx = swr_alloc_set_opts(NULL, priv->codecContext->channel_layout, AV_SAMPLE_FMT_S16, priv->codecContext->sample_rate, priv->outFrame->channel_layout, (enum AVSampleFormat)priv->outFrame->format, priv->outFrame->sample_rate, 0, NULL);
        if(priv->swrCtx == NULL || (result = swr_init(priv->swrCtx)) < 0)
        {
            YYLOGD("swr_init() failed. result:%d", result);
            return -1;
        }
        YYLOGD("decode result. sampleRate:%d, samples:%d, channels:%d", priv->sampleRate, priv->samples, priv->channels);
        jobject object = create_audio_info(env, priv);
        env->CallVoidMethod(priv->objweak, JNI_METHODID(NativeFfmpeg, onFormatChanged), object);
        DELETE_LOCAL_REF(env, object);
    }
    if((data[0] = (char*)env->GetDirectBufferAddress(output)) == NULL)
    {
        YYLOGD("direct buffer address is not accessable.", 0);
        return -1;
    }
    if((result = env->GetDirectBufferCapacity(output)) < priv->dataLen)
    {
        YYLOGD("capacity of output buffer is not enough. requested:%d, capacity:%d", priv->dataLen, result);
        return -1;
    }
    if(priv->swrCtx != NULL && priv->codecContext->sample_fmt != AV_SAMPLE_FMT_S16)
    {
#if 0
        // LC-AAC: 1024 samples
        // HE-AAC: 2048 samples
        if(priv->outFrame->nb_samples != 1024)
        {
            YYLOGD("warning, output nb_samples:%d", priv->outFrame->nb_samples);
        }
#endif
        swr_convert(priv->swrCtx, (uint8_t**)data, priv->outFrame->nb_samples, (const uint8_t**)priv->outFrame->data, priv->outFrame->nb_samples);
    }
    else
    {
        // copy directly
        memcpy(data[0], priv->outFrame->data[0], priv->dataLen);
    }
    env->CallObjectMethod(output, methodID_ByteBuffer_position_I, 0);
    env->CallObjectMethod(output, methodID_ByteBuffer_limit_I, priv->dataLen);
    return 1;
}

static void nativeClassInit(JNIEnv* env, jobject thiz)
{
    avcodec_register_all();

    const char* path = "java/nio/Buffer";
    jclass clazz = env->FindClass(path);
    YYLOGD("class:%s, %p", path, clazz);
    methodID_ByteBuffer_position_I = env->GetMethodID(clazz, "position", "(I)Ljava/nio/Buffer;");
    YYLOGD("methodID_ByteBuffer_position_I:%p", methodID_ByteBuffer_position_I);
    methodID_ByteBuffer_position_V = env->GetMethodID(clazz, "position", "()I");
    YYLOGD("methodID_ByteBuffer_position_V:%p", methodID_ByteBuffer_position_V);
    methodID_ByteBuffer_limit_I = env->GetMethodID(clazz, "limit", "(I)Ljava/nio/Buffer;");
    YYLOGD("methodID_ByteBuffer_limit_I:%p", methodID_ByteBuffer_limit_I);
    methodID_ByteBuffer_limit_V = env->GetMethodID(clazz, "limit", "()I");
    YYLOGD("methodID_ByteBuffer_limit_V :%p", methodID_ByteBuffer_limit_V );
    DELETE_LOCAL_REF(env, clazz);

    path = "android/media/MediaFormat";
    clazz = env->FindClass(path);
    YYLOGD("class:%s, %p", path, clazz);
    JNI_LOAD_METHODID(env, clazz, MediaFormat, getInteger, "(Ljava/lang/String;)I");
    JNI_LOAD_METHODID(env, clazz, MediaFormat, getByteBuffer, "(Ljava/lang/String;)Ljava/nio/ByteBuffer;");
    DELETE_LOCAL_REF(env, clazz);

    path = kMediaInfoPath;
    clazz = env->FindClass(path);
    YYLOGD("class:%s, %p", path, clazz);
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, type, "I");
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, width, "I");
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, height, "I");
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, planeWidth, "I");
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, planeHeight, "I");
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, planeSize, "I");
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, sampleRate, "I");
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, samples, "I");
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, channels, "I");
    JNI_LOAD_FIELDID(env, clazz, MediaInfo, dataLen, "I");
    DELETE_LOCAL_REF(env, clazz);

    path = kNativeFfmpegPath;
    clazz = env->FindClass(path);
    YYLOGD("class:%s, %p", path, clazz);
    JNI_LOAD_FIELDID(env, clazz, NativeFfmpeg, mNativeHandle, "J");
    JNI_LOAD_METHODID(env, clazz, NativeFfmpeg, onFormatChanged, "(Lcom/ycloud/svplayer/MediaInfo;)V");
    DELETE_LOCAL_REF(env, clazz);
}

static void native_setup(JNIEnv* env, jobject thiz)
{
}

static void native_release(JNIEnv* env, jobject thiz)
{
}

static jint native_create(JNIEnv* env, jobject thiz, jint codecId, jobject format)
{
    FfmpegPriv* priv = NULL;
    AVCodec* codec = NULL;
    AVCodecContext* codecContext = NULL;

    switch(codecId)
    {
    case FRAME_TYPE_AAC:
        codecId = (jint)AV_CODEC_ID_AAC;
        break;
    case FRAME_TYPE_H264:
        codecId = (jint)AV_CODEC_ID_H264;
        break;
    case FRAME_TYPE_HEVC:
        codecId = (jint)AV_CODEC_ID_HEVC;
        break;
    default:
        return -1;
    }
    if((codec = avcodec_find_decoder((enum AVCodecID)codecId)) == NULL)
    {
        YYLOGD("avcodec_find_decoder(%d) failed.", codecId);
        return -1;
    }
    if((codecContext = avcodec_alloc_context3(codec)) == NULL)
    {
        YYLOGD("avcodec_alloc_context3() failed.", 0);
        return -1;
    }
    init_codecContext_withFormat(env, codecContext, format);

    if(avcodec_open2(codecContext, codec, NULL) < 0)
    {
        avcodec_free_context(&codecContext);
        YYLOGD("avcodec_open() failed.", 0);
        return -1;
    }
    if((priv = (FfmpegPriv*)calloc(1, sizeof(FfmpegPriv))) != NULL)
    {
        priv->codec = codec;
        priv->codecContext = codecContext;
        priv->outFrame = av_frame_alloc();
        priv->objweak = env->NewWeakGlobalRef(thiz);
        env->SetLongField(thiz, JNI_FIELDID(NativeFfmpeg, mNativeHandle), (jlong)priv);
        priv->mFlushTrigged = false;
        YYLOGD("env:%p, priv:%p, codecId:%d, objweak:%p, codec:%p, codecContext:%p", env, priv, codecId, priv->objweak, priv->codec, priv->codecContext);
    }
    return priv ? 0 : -1;
}

static void native_destroy(JNIEnv* env, jobject thiz)
{
    jlong handle = env->GetLongField(thiz, JNI_FIELDID(NativeFfmpeg, mNativeHandle));
    FfmpegPriv* priv = (FfmpegPriv*)handle;

    YYLOGD("thiz:%p, handle:%lld, priv:%p", thiz, handle, priv);
    if(priv == NULL) return;

    if(priv->codecContext!=NULL)
    {
        SAFE_FREE(priv->codecContext->extradata);
        avcodec_free_context(&priv->codecContext);
        priv->codec = NULL;
        priv->codecContext = NULL;
    }
    if(priv->swrCtx != NULL)
    {
        swr_close(priv->swrCtx);
        swr_free(&priv->swrCtx);
        priv->swrCtx = NULL;
    }
    if(priv->outFrame != NULL)
    {
        av_frame_free(&priv->outFrame);
        priv->outFrame = NULL;
    }
    DELETE_WEAK_GLOBAL_REF(env, priv->objweak);
    SAFE_FREE(priv);
}

static jint native_decode(JNIEnv* env, jobject thiz, jobject input, jobject output, jboolean keyFrame)
{
    jlong handle = env->GetLongField(thiz, JNI_FIELDID(NativeFfmpeg, mNativeHandle));
    FfmpegPriv* priv = (FfmpegPriv*)handle;

    if(priv == NULL || input == NULL || output == NULL) return -1;

    if(priv->codecContext->codec_id == AV_CODEC_ID_H264 || priv->codecContext->codec_id == AV_CODEC_ID_HEVC)
    {
        return decode_video_frame(env, priv, input, output, keyFrame);
    }
    else if(priv->codecContext->codec_id == AV_CODEC_ID_AAC)
    {
        return decode_audio_frame(env, priv, input, output);
    }
    return -1;
}

static jint native_flush(JNIEnv* env, jobject thiz, jobject output)
{
    jlong handle = env->GetLongField(thiz, JNI_FIELDID(NativeFfmpeg, mNativeHandle));
    FfmpegPriv* priv = (FfmpegPriv*)handle;

    if(priv == NULL ||  output == NULL) return -1;

    int result = -1;
    char* data = NULL;
    char log[AV_ERROR_MAX_STRING_SIZE] = {0};

    if(!priv->mFlushTrigged) {
        //send a decode flush...
        priv->mFlushTrigged = true;
        if((result = avcodec_send_packet(priv->codecContext, NULL)) < 0)
        {
            YYLOGD("native_flush, avcodec_send_packet() failed. result:0x%08x, %s", result, av_make_error_string(log, AV_ERROR_MAX_STRING_SIZE, result));
            return -1;
        }
    }

    if((result = avcodec_receive_frame(priv->codecContext, priv->outFrame)) == AVERROR(EAGAIN))
    {
       return 0;
    }
     else if(result == AVERROR_EOF)
     {
        YYLOGD("native flush avcodec_receive_frame() end of file. result:0x%08x, %s", result, av_make_error_string(log, AV_ERROR_MAX_STRING_SIZE, result));
        return -2;
     } else if(result < 0) {
        YYLOGD("native flush avcodec_receive_frame() fail result:0x%08x, %s", result, av_make_error_string(log, AV_ERROR_MAX_STRING_SIZE, result));
        return -1;
     }

        if(priv->width != priv->codecContext->width || priv->height != priv->codecContext->height)
        {
            priv->width = priv->codecContext->width;
            priv->height = priv->codecContext->height;
            priv->planeWidth = priv->outFrame->linesize[0];
            priv->planeHeight = priv->height;
            priv->planeSize = priv->planeWidth * priv->planeHeight;
            priv->dataLen = (priv->planeSize * 3) >> 1;
            YYLOGD("profile:%d, %s, level:%d", priv->codecContext->profile, av_get_profile_name(priv->codec, priv->codecContext->profile), priv->codecContext->level);
            YYLOGD("decode result. frameSize:%dx%d, planeSize:%dx%d, dataLen:%d", priv->width, priv->height, priv->planeWidth, priv->planeHeight, priv->dataLen);
            jobject object = create_video_info(env, priv);
            env->CallVoidMethod(priv->objweak, JNI_METHODID(NativeFfmpeg, onFormatChanged), object);
            DELETE_LOCAL_REF(env, object);
        }
        if((data = (char*)env->GetDirectBufferAddress(output)) == NULL)
        {
            YYLOGD("direct buffer address is not accessable.", 0);
            return -1;
        }
        if((result = env->GetDirectBufferCapacity(output)) < priv->dataLen)
        {
            YYLOGD("capacity of output buffer is not enough. requested:%d, capacity:%d", priv->dataLen, result);
            return -1;
        }
        copy_yuv_frame(priv->outFrame, data, priv->planeSize);
        env->CallObjectMethod(output, methodID_ByteBuffer_position_I, 0);
        env->CallObjectMethod(output, methodID_ByteBuffer_limit_I, priv->dataLen);
        return 1;
}

static JNINativeMethod gNativeFfmpegMethod[] =
{
    {"nativeClassInit", "()V",  (void*)nativeClassInit },
    {"native_setup",    "()V",  (void*)native_setup },
    {"native_release",  "()V",  (void*)native_release },
    {"native_create",   "(ILandroid/media/MediaFormat;)I", (void*)native_create },
    {"native_destroy",  "()V", (void*)native_destroy },
    {"native_decode",   "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Z)I", (void*)native_decode },
    {"native_flush",  "(Ljava/nio/ByteBuffer;)I", (void*)native_flush },
};

extern "C" void registerNativeFfmpeg(JNIEnv* env)
{
    int result = 0;
    jclass clazz = NULL;

    if((clazz = env->FindClass(kNativeFfmpegPath)) == NULL)
    {
        YYLOGE("class not found. %s", kNativeFfmpegPath);
        return;
    }
    if((result = env->RegisterNatives(clazz, gNativeFfmpegMethod, sizeof(gNativeFfmpegMethod) / sizeof(*gNativeFfmpegMethod))) == JNI_OK)
    {
        YYLOGD("succeed to register native methods for class %s", kNativeFfmpegPath);
    }
    else
    {
        YYLOGE("env->RegisterNatives() failed. class:%s, result:%d", kNativeFfmpegPath, result);
    }
    DELETE_LOCAL_REF(env, clazz);
}

extern "C"  void unregisterNativeFfmpeg(JNIEnv *env)
{

}
