#include "FFmpegAacEncoder.h"

#include <android/log.h>
#include <jni.h>
#define LOGV(...)   __android_log_print((int)ANDROID_LOG_INFO, "FFmpegAacEncoder", __VA_ARGS__)

static const int aac_sampling_freq[16] = {96000, 88200, 64000, 48000, 44100, 32000,
    24000, 22050, 16000, 12000, 11025,  8000,
    0, 0, 0, 0}; /* filling */
static const int id = 0, profile = 1;

static  void adts_hdr(char *adts, int sampleRate, int nChannelsOut)
{
    int srate_idx = 15, i;

    /* sync word, 12 bits */
    adts[0] = (char)0xff;
    adts[1] = (char)0xf0;

    /* ID, 1 bit */
    adts[1] |= id << 3;
    /* layer: 2 bits = 00 */

    /* protection absent: 1 bit = 1 (ASSUMPTION!) */
    adts[1] |= 1;

    /* profile, 2 bits */
    adts[2] = profile << 6;

    for (i = 0; i < 16; i++)
        if (sampleRate >= (aac_sampling_freq[i] - 1000)) {
            srate_idx = i;
            break;
        }

    /* sampling frequency index, 4 bits */
    adts[2] |= srate_idx << 2;

    /* private, 1 bit = 0 (ASSUMPTION!) */

    /* channels, 3 bits */
    adts[2] |= (nChannelsOut & 4) >> 2;
    adts[3] = (nChannelsOut & 3) << 6;

    /* adts buffer fullness, 11 bits, 0x7ff = VBR (ASSUMPTION!) */
    adts[5] |= (char)0x1f;
    adts[6] = (char)0xfc;
}

static void adts_hdr_up(char *adts, int size)
{
    /* frame length, 13 bits */
    int len = size + 7;
    adts[3] |= len >> 11;
    adts[4] = (len >> 3) & 0xff;
    adts[5] = (len & 7) << 5;
}

FFmpegAacEncoder::FFmpegAacEncoder()
 : mAudioEncCtx(NULL)
 , mFrame(NULL) {
}

FFmpegAacEncoder::~FFmpegAacEncoder() {
	LOGV(" dtor");
    if(mAudioEncCtx != NULL) {
		avcodec_close(mAudioEncCtx);
		avcodec_free_context(&mAudioEncCtx);
		mAudioEncCtx = NULL;
    }
    if(mFrame != NULL) {
        av_frame_free(&mFrame);
        mFrame = NULL;
    }
}

bool FFmpegAacEncoder::init(int sampleRate, int channelCount, int bitRate) {
	av_register_all();
    AVCodecID codecId = AV_CODEC_ID_AAC;
    do {
        //AVCodec *codec = avcodec_find_encoder(codecId);
        AVCodec *codec = avcodec_find_encoder_by_name("libfdk_aac");
        if(codec == NULL) {
            LOGV("can't find aac codec");
            break;
        }
        mAudioEncCtx = avcodec_alloc_context3((const AVCodec*)codec);
        if(mAudioEncCtx == NULL) {
            LOGV("can't alloc aac codec");
            break;
        }
        mAudioEncCtx->codec_id = codecId;
        mAudioEncCtx->codec_type = AVMEDIA_TYPE_AUDIO;
        mAudioEncCtx->sample_fmt = AV_SAMPLE_FMT_S16;
        mAudioEncCtx->sample_rate = sampleRate;
        mAudioEncCtx->channel_layout = channelCount == 2 ? AV_CH_LAYOUT_STEREO : AV_CH_LAYOUT_MONO;
        mAudioEncCtx->channels = av_get_channel_layout_nb_channels(mAudioEncCtx->channel_layout);
        mAudioEncCtx->profile = FF_PROFILE_AAC_LOW;
        mAudioEncCtx->bit_rate = bitRate;
        AVDictionary *opts = NULL;
        int ret = avcodec_open2(mAudioEncCtx, codec, &opts);
        if(ret < 0) {
            LOGV("can't open aac codec %d %d %d", sampleRate, channelCount, ret);
            break;
        }
        mFrame = av_frame_alloc();
        mFrame->nb_samples = mAudioEncCtx->frame_size;
        mFrame->format = mAudioEncCtx->sample_fmt;
        mFrame->channels = channelCount;

        int size = av_samples_get_buffer_size(NULL, mAudioEncCtx->channels, mAudioEncCtx->frame_size, mAudioEncCtx->sample_fmt, 0);

        LOGV("open aac codec %d %d %d %d", sampleRate, channelCount, mAudioEncCtx->frame_size, size);
	    av_init_packet(&mPacket);
        return true;
    }while(false);

    return false;
}

void FFmpegAacEncoder::deint() {
}

int FFmpegAacEncoder::inputFrameSize() {
    if(mAudioEncCtx == NULL) {
        return 0;
    }
    int size = av_samples_get_buffer_size(NULL, mAudioEncCtx->channels, mAudioEncCtx->frame_size, mAudioEncCtx->sample_fmt, 0);
    return size;
}

void FFmpegAacEncoder::pushFrame(uint8_t* data, uint32_t len, int64_t pts ) {
    if(mAudioEncCtx == NULL) {
        return;
    }
    int size = av_samples_get_buffer_size(NULL, mAudioEncCtx->channels, mAudioEncCtx->frame_size, mAudioEncCtx->sample_fmt, 0);
    if(len != size) {
        LOGV(" must push integrated frame %d %d", len, size);
    }
    int ret = avcodec_fill_audio_frame(mFrame, mAudioEncCtx->channels, mAudioEncCtx->sample_fmt, (const uint8_t*)data, len , 0);
    mFrame->pts = pts;
    if(ret < 0) {
        LOGV("fill frame error");
        return;
    }
    ret = avcodec_send_frame(mAudioEncCtx, (const AVFrame*)mFrame);
    if(ret < 0) {
        LOGV("send frame error");
    }
}

uint32_t FFmpegAacEncoder::pullFrame(uint8_t* buffer, uint32_t bufferLen, int64_t& pts) {
    if(mAudioEncCtx == NULL) {
        return 0;
    }
    int ret = avcodec_receive_packet(mAudioEncCtx, &mPacket);
    int retLen = 0;
    if(ret == 0) {
        //fdk aac frame is a adts frame, we need raw frame, so filter adts header
        memcpy(buffer, mPacket.data + 7, mPacket.size - 7);
        retLen = mPacket.size - 7;
        pts = mPacket.pts;
    }
    av_packet_unref(&mPacket);
    return retLen;
}


extern "C" JNIEXPORT jlong JNICALL Java_com_ycloud_mediacodec_audiocodec_FFmpegAacEncoder_create
    (JNIEnv *env, jobject thiz, jint sample_rate, jint channels, jint bitRate) {
    FFmpegAacEncoder* ins = new FFmpegAacEncoder();
    ins->init(sample_rate, channels, bitRate);
    return (jlong)ins;
}

extern "C" JNIEXPORT void JNICALL Java_com_ycloud_mediacodec_audiocodec_FFmpegAacEncoder_destroy
    (JNIEnv *env, jobject thiz, jlong pointer) {
    FFmpegAacEncoder *ptr = (FFmpegAacEncoder *)pointer;
    if(ptr != NULL) {
        delete ptr;
    }
}

extern "C" JNIEXPORT int JNICALL Java_com_ycloud_mediacodec_audiocodec_FFmpegAacEncoder_inputFrameSize
    (JNIEnv *env, jobject thiz, jlong pointer) {
    FFmpegAacEncoder *ptr = (FFmpegAacEncoder *)pointer;
    if(ptr != NULL) {
        return ptr->inputFrameSize();
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL Java_com_ycloud_mediacodec_audiocodec_FFmpegAacEncoder_pushFrame
    (JNIEnv *env, jobject thiz, jlong pointer, jbyteArray in_buffer, jint len, jlong pts) {
    FFmpegAacEncoder *ptr = (FFmpegAacEncoder *)pointer;
    if(ptr != NULL) {
        jbyte* in_buffer_ptr = env->GetByteArrayElements(in_buffer, NULL);
        ptr->pushFrame((uint8_t*)in_buffer_ptr, len, pts);
        env->ReleaseByteArrayElements(in_buffer, in_buffer_ptr, 0);
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_ycloud_mediacodec_audiocodec_FFmpegAacEncoder_pullFrame
    (JNIEnv *env, jobject thiz, jlong pointer, jbyteArray in_buffer, jint len, jlongArray returnInfo) {
    FFmpegAacEncoder *ptr = (FFmpegAacEncoder *)pointer;
    int retLen = 0;
    jlong  pts = 0;
    if(ptr != NULL) {
        jbyte* in_buffer_ptr = env->GetByteArrayElements(in_buffer, NULL);
        jlong* in_return_ptr = env->GetLongArrayElements(returnInfo, NULL);
        retLen = ptr->pullFrame((uint8_t*)in_buffer_ptr, len, pts);
        in_return_ptr[0] = retLen;
        in_return_ptr[1] = pts;
        env->ReleaseByteArrayElements(in_buffer, in_buffer_ptr, 0);
        env->ReleaseLongArrayElements(returnInfo, in_return_ptr, 0);
    }
}
