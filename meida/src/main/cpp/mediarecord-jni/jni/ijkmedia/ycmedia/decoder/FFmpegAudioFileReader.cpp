#include "FFmpegAudioFileReader.h"
#include "android/FFmpegAacEncoder.h"
#include <android/log.h>
#include <jni.h>
#define LOGV(...)   __android_log_print((int)ANDROID_LOG_INFO, "FFmpegAudioFileReader", __VA_ARGS__)

FFmpegAudioFileReader::FFmpegAudioFileReader(uint32_t outSampleRate, uint32_t outChannelCount)
: mOutChannelCount(outChannelCount)
, mOutSampleRate(outSampleRate)
, mFmtCtx(NULL)
, mAudioDecCtx(NULL)
, mAudioStream(NULL)
, mAudioStreamIdx(-1)
, mFrame(NULL)
, mPkt(NULL)
, mSwrCtx(NULL){

}

FFmpegAudioFileReader::~FFmpegAudioFileReader() {
	LOGV(" dtor");
	_close();
}

bool FFmpegAudioFileReader::open(const char* pPath) {
    if(pPath == NULL) {
        return false;
    }
	av_register_all();

	if (avformat_open_input(&mFmtCtx, pPath, NULL, NULL) < 0) {
		LOGV("Could not open source file %s. ", pPath);
	    return false;
	}

	if (avformat_find_stream_info(mFmtCtx, NULL) < 0) {
		LOGV("Could not find stream information.");
		return false;
	}
	if(_openDecoder(&mAudioStreamIdx, mFmtCtx, AVMEDIA_TYPE_AUDIO)) {
		mAudioStream = mFmtCtx->streams[mAudioStreamIdx];
		mAudioDecCtx = mAudioStream->codec;
		if(mOutChannelCount == 0) {
		    mOutChannelCount = channelCount();
		}
		if(mOutSampleRate == 0) {
		    mOutSampleRate = sampleRate();
		}

		LOGV(" %s %d sample rate %d channelCount %d " , pPath, lenInMS(), mAudioDecCtx->sample_rate, mAudioDecCtx->channels);
		return true;
	}
	return false;
}

uint32_t FFmpegAudioFileReader::lenInMS() {
    if(mAudioStream != NULL) {
        long duration;
        if(mAudioStream->duration != AV_NOPTS_VALUE) {
            duration = mAudioStream->duration * av_q2d(mAudioStream->time_base) * 1000;
        }else {
            duration = mFmtCtx->duration * av_q2d(mAudioStream->time_base) * 1000;
        }
        return duration;
    }
    return 0;
}

uint32_t FFmpegAudioFileReader::sampleRate() {
    if(mAudioDecCtx != NULL) {
        return mAudioDecCtx->sample_rate;
    }
    return 0;
}

uint32_t FFmpegAudioFileReader::channelCount() {
    if(mAudioDecCtx != NULL) {
        return mAudioDecCtx->channels;
    }
    return 0;
}

uint32_t FFmpegAudioFileReader::currentPositionInMS() {
    if(mFrame != NULL) {
        long cur = mFrame->pts * av_q2d(mAudioStream->time_base) * 1000;
        return cur;
    }
    return 0;
}

void FFmpegAudioFileReader::seek(uint32_t positionInMS) {
    if(mFmtCtx != NULL) {
        double tmp = (double)positionInMS;
        int64_t seekTime = tmp / 1000 / av_q2d(mAudioStream->time_base);
        int ret = av_seek_frame(mFmtCtx, mAudioStreamIdx, seekTime, AVSEEK_FLAG_BACKWARD);
        if(ret < 0) {
            LOGV("seekTo Seeking to %ld failed ret:0x%x ", positionInMS, ret );
        } else {
            LOGV("seekTo Seeking to %ld OK ret:0x%x seekTime :%"PRId64, positionInMS, ret, seekTime );
        }
    }
}

uint32_t FFmpegAudioFileReader::readFrame(uint8_t* buffer, uint32_t bufferLen){
    if(mAudioDecCtx == NULL) {
        return 0;
    }
    if(mPkt == NULL) {
        mPkt = av_packet_alloc();
	    av_init_packet(mPkt);
    }
    //LOGV(" ccccccccc %d", currentPositionInMS());
    if(mFrame == NULL) {
	    mFrame = av_frame_alloc();
    }
    int tryCount = 10;
    int retLen = 0;
    do {
        int ret = av_read_frame(mFmtCtx, mPkt);
        int got_frame = 0;
        if (ret >= 0 ) {
            if(mPkt->stream_index == mAudioStreamIdx) {
                ret = avcodec_decode_audio4(mAudioDecCtx, mFrame, &got_frame, mPkt);
                if(ret > 0) {
                    if(got_frame > 0) {
                        if(mSwrCtx == NULL) {
                            int sampleRate = mFrame->sample_rate;
                            int channelCount = av_get_default_channel_layout(mFrame->channels);
                            LOGV(" alloc swr %d %d ", mOutChannelCount, mOutSampleRate);
                            mSwrCtx = swr_alloc_set_opts(NULL, mOutChannelCount == 2 ? AV_CH_LAYOUT_STEREO : AV_CH_LAYOUT_MONO, AV_SAMPLE_FMT_S16, mOutSampleRate, channelCount, mFrame->format, sampleRate, 0, NULL);
                            if(mSwrCtx == NULL) {
                                LOGV("swr_ctx == NULL");
                            }
                            swr_init(mSwrCtx);
                        }
                        uint8_t* out[] = {buffer};
                        retLen = swr_convert(mSwrCtx, (uint8_t**)out, bufferLen, (const uint8_t **)mFrame->extended_data, mFrame->nb_samples);
                        retLen *= (2 * mOutChannelCount);
                        tryCount = 0;
                    }else {
                        //
                        LOGV(" got invalid frame ");
                    }
                }else {
                    LOGV("avcodec_decode_audio4 eof ");
                }
            }else {
                //
                LOGV(" not audio stream index %d %d", mPkt->stream_index, mAudioStreamIdx);
                tryCount--;
            }
        }else {
            LOGV(" av_read_frame eof ");
            tryCount = 0;
        }
        av_packet_unref(mPkt);
    }while(tryCount > 0);
    return retLen;
}

bool FFmpegAudioFileReader::_openDecoder(int *stream_idx, AVFormatContext *fmt_ctx, enum AVMediaType type)
{
    int ret = -1;
    AVStream *st = NULL;
    AVCodecContext *dec_ctx = NULL;
    AVCodec *dec = NULL;
    AVDictionary *opts = NULL;
    const char *typeStr = av_get_media_type_string(type);

    ret = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
    if (ret < 0) {
    	LOGV("Could not find %s stream in input file ",typeStr);
        return false;
    }

	*stream_idx = ret;
	st = fmt_ctx->streams[*stream_idx];

	/* find decoder for the stream */
	dec_ctx = st->codec;
	dec = avcodec_find_decoder(dec_ctx->codec_id);
	if (!dec) {
		LOGV("Failed to find %s codec",typeStr);
		return false;
	}

    LOGV(" open codec %d ", dec_ctx->codec_id);
	if ((ret = avcodec_open2(dec_ctx, dec, &opts)) < 0) {
		LOGV("Failed to open %s codec",typeStr);
		return false;
	}
    return true;
}

void FFmpegAudioFileReader::_close() {
    if(mAudioDecCtx != NULL) {
		avcodec_close(mAudioDecCtx);
		mAudioDecCtx = NULL;
    }
    if(mSwrCtx !=  NULL) {
        swr_close(mSwrCtx);
        swr_free(&mSwrCtx);
        mSwrCtx = NULL;
    }
    if(mFrame != NULL) {
        av_frame_free(&mFrame);
        mFrame = NULL;
    }
    if(mPkt != NULL) {
        av_packet_free(&mPkt);
        mPkt = NULL;
    }
    if(mFmtCtx != NULL) {
		avformat_close_input(&mFmtCtx);
		mFmtCtx = NULL;
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_com_ycloud_audio_FFmpegAudioFileReader_create
    (JNIEnv *env, jobject thiz, jint out_sample_rate, jint out_channels) {
    FFmpegAudioFileReader* ins = new FFmpegAudioFileReader(out_sample_rate, out_channels);
    return (jlong)ins;
}

extern "C" JNIEXPORT void JNICALL Java_com_ycloud_audio_FFmpegAudioFileReader_destroy
    (JNIEnv *env, jobject thiz, jlong pointer) {
    FFmpegAudioFileReader *ptr = (FFmpegAudioFileReader *)pointer;
    if(ptr != NULL) {
        delete ptr;
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_ycloud_audio_FFmpegAudioFileReader_open
    (JNIEnv *env, jobject thiz, jlong pointer, jstring path) {
    FFmpegAudioFileReader *ptr = (FFmpegAudioFileReader *)pointer;
    if(ptr != NULL) {
        const char* pPath = env->GetStringUTFChars(path, 0);
        ptr->open(pPath);
        env->ReleaseStringUTFChars(path, pPath);
    }
}

extern "C" JNIEXPORT int JNICALL Java_com_ycloud_audio_FFmpegAudioFileReader_getSampleRate
    (JNIEnv *env, jobject thiz, jlong pointer) {
    FFmpegAudioFileReader *ptr = (FFmpegAudioFileReader *)pointer;
    if(ptr != NULL) {
        return ptr->sampleRate();
    }
    return 0;
}

extern "C" JNIEXPORT int JNICALL Java_com_ycloud_audio_FFmpegAudioFileReader_getChannelCount
    (JNIEnv *env, jobject thiz, jlong pointer) {
    FFmpegAudioFileReader *ptr = (FFmpegAudioFileReader *)pointer;
    if(ptr != NULL) {
        return ptr->channelCount();
    }
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL Java_com_ycloud_audio_FFmpegAudioFileReader_lenInMS
    (JNIEnv *env, jobject thiz, jlong pointer) {
    FFmpegAudioFileReader *ptr = (FFmpegAudioFileReader *)pointer;
    if(ptr != NULL) {
        return ptr->lenInMS();
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL Java_com_ycloud_audio_FFmpegAudioFileReader_seek
    (JNIEnv *env, jobject thiz, jlong pointer, jlong ms) {
    FFmpegAudioFileReader *ptr = (FFmpegAudioFileReader *)pointer;
    if(ptr != NULL) {
        ptr->seek((uint32_t)ms);
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_com_ycloud_audio_FFmpegAudioFileReader_currentPosition
    (JNIEnv *env, jobject thiz, jlong pointer) {
    FFmpegAudioFileReader *ptr = (FFmpegAudioFileReader *)pointer;
    if(ptr != NULL) {
        return ptr->currentPositionInMS();
    }
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL Java_com_ycloud_audio_FFmpegAudioFileReader_readFrame
    (JNIEnv *env, jobject thiz, jlong pointer, jbyteArray in_buffer, jint in_offset, jint in_len) {
    FFmpegAudioFileReader *ptr = (FFmpegAudioFileReader *)pointer;
    if(ptr != NULL) {
        jbyte* in_buffer_ptr = env->GetByteArrayElements(in_buffer, NULL);
        int retLen = ptr->readFrame(in_buffer_ptr + in_offset, in_len);
        env->ReleaseByteArrayElements(in_buffer, in_buffer_ptr, 0);
        return retLen;
    }
    return 0;
}
