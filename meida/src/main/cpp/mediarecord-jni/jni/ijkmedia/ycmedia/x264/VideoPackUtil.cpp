//
// Created by kele on 2016/9/30.
//

#include <jni.h>
#include "VideoPackUtil.h"
#include "Macros.h"
#include "Common.h"
#include "IntTypes.h"
#include "JNIHelper.h"
#include "Mediabase.h"
#include <string>

NAMESPACE_YYMFW_BEGIN

const char* const kVideoPackUtilClassPathName = "com/ycloud/utils/JVideoPackUtil";

struct GeneralBuffer {
    uint8_t *data;
    int pos, size;
};

static inline uint16_t U16_AT(const uint8_t *ptr)
{
    return ptr[0] << 8 | ptr[1];
}
const uint8_t startCode[4] = { 0x00, 0x00, 0x00, 0x01 };
const uint8_t dzero[2] = { 0x00, 0x00 };

void StartSizeToStartCode(uint8_t *frame, int size) {
    if (!(frame[0] == 0 && frame[1] == 0 && frame[2] == 0 && frame[3] == 1)) {
        uint8_t *p = frame;
        while (p - frame < size) {
            int nalsize = 0;
            for (int i = 0; i < 4; i++)
                nalsize = (nalsize << 8) | p[i];
            memcpy(p, startCode, sizeof(startCode));
            p += 4 + nalsize;
        }
    }
}

static int ParseSpsPpsData(const unsigned char* pSpsPpsData, int nLen, GeneralBuffer& SpsPpsData)
{
    //sps header
    const unsigned char *p = pSpsPpsData;
    p += 5;
    //sps body
    int nSpsCount = *p & 0x1f;
    p += 1;

    if (nSpsCount > 0)
    {
        for (int i = 0; i < nSpsCount; ++i)
        {
            int nalsize = ((p[0] << 8) | p[1]);

            memcpy(SpsPpsData.data + SpsPpsData.pos, dzero, sizeof(dzero));
            SpsPpsData.pos += sizeof(dzero);

            memcpy(SpsPpsData.data + SpsPpsData.pos, p, nalsize + 2);
            SpsPpsData.pos += nalsize + 2;

            p += nalsize + 2;
        }
    }

    if (nLen - (p - pSpsPpsData) > 0)
    {
        int nPpsCount = *p & 0x1f;
        p += 1;

        if (nPpsCount > 0)
        {
            for (int i = 0; i < nPpsCount; ++i)
            {
                int nalsize = ((p[0] << 8) | p[1]);

                memcpy(SpsPpsData.data + SpsPpsData.pos, dzero, sizeof(dzero));
                SpsPpsData.pos += sizeof(dzero);

                memcpy(SpsPpsData.data + SpsPpsData.pos, p, nalsize + 2);
                SpsPpsData.pos += nalsize + 2;

                p += nalsize + 2;
            }
        }
    }
    return 0;
}

static jbyteArray VideoPackUtil_UnpackHeader(JNIEnv* env, jclass clazz, jbyteArray header, jint size) {
    int width = 480, height = 360;//, frameRate = 0;//, result;
    GeneralBuffer SpsPpsData;
    //padding widht/height
    SpsPpsData.data = new uint8_t[size];
    SpsPpsData.pos = 0;
    SpsPpsData.size = size;

   unsigned  char* pHeader = JNIHelper::newBufferFromByteArray(env, header);
    ParseSpsPpsData(pHeader, size, SpsPpsData);
    JNIHelper::freeBuffer(pHeader);

    //TODO. 需要把width, height抛到java层.
    StartSizeToStartCode(SpsPpsData.data, SpsPpsData.pos);
    int ret = ParseVideoHeader(AV_CODEC_ID_H264, SpsPpsData.data, SpsPpsData.pos, &width, &height);
    LOGD("fastVideo width = %d, height = %d, PareseVideoHeader ret=%d", width, height, ret);
    /*
    memcpy(SpsPpsData.data+SpsPpsData.pos, &width, 4);
    memcpy(SpsPpsData.data+SpsPpsData.pos+4, &height, 4);
    SpsPpsData.pos += 8;
     */
    jbyteArray jbarray = env->NewByteArray(SpsPpsData.pos);
    if (jbarray != NULL) {
        jbyte *jy = (jbyte*)SpsPpsData.data;
        env->SetByteArrayRegion(jbarray, 0, SpsPpsData.pos, jy);
    }
    delete[] SpsPpsData.data;
    return jbarray;
}

static jbyteArray VideoPackUtil_UnpackFrame(JNIEnv* env, jclass clazz, jbyteArray frame, jint size) {

    unsigned  char* pFrame = JNIHelper::newBufferFromByteArray(env, frame);
    StartSizeToStartCode(pFrame, size);
    jbyteArray jbarray = env->NewByteArray(size);
    if (jbarray != NULL) {
        jbyte *jy = (jbyte*)pFrame;
        env->SetByteArrayRegion(jbarray, 0, size, jy);
        //jni->CallStaticVoidMethod(jVideoDecoderCenterClass, jDeliverVideoDataMethod, mUserGroupId, mStreamId, jbarray, pts, mMicPos);
        //jni->DeleteLocalRef(jbarray);
    }

    JNIHelper::freeBuffer(pFrame);
    return jbarray;
}

static jbyteArray VideoPackUtil_PackHeader(JNIEnv* env, jclass clazz, jbyteArray sps, jint nSpsLen, jbyteArray pps, jint nPpsLen) {
    if(sps == NULL || nSpsLen <= 0)
       return NULL;

    unsigned char * spsData = JNIHelper::newBufferFromByteArray(env, sps);
    uint8_t nProfile	   = (int)(*(spsData + 1));
    uint8_t nCompatibility = (int)(*(spsData + 2));
    uint8_t nLevel		 = (int)(*(spsData + 3));
    int nDatalen = nSpsLen + nPpsLen + 8 + 3;
    uint8_t* pSps_PpsBuf = ( uint8_t*)malloc(nDatalen);

    //sps
    uint8_t* pSps = pSps_PpsBuf;
    pSps[0] = 1;		   // version 1
    pSps[1] = nProfile;
    pSps[2] = nCompatibility;
    pSps[3] = nLevel;
    pSps[4] = (uint8_t)(0xfC | (4 - 1));
    pSps[5] = 0xe1;  // 1 SPS

    //sps len
    pSps[6] = (nSpsLen >> 8) & 0xff;
    pSps[7] =  nSpsLen & 0xff;
    memcpy(pSps + 8, spsData, nSpsLen);

    if(pps && nPpsLen > 0)
    {
        unsigned char * ppsData = JNIHelper::newBufferFromByteArray(env, pps);
        //pps
        uint8_t* pPps = pSps_PpsBuf + 8 + nSpsLen;
        pPps[0] = 1;   // 1 PPS

        //pps len
        pPps[1] = (nPpsLen >> 8) & 0xff;
        pPps[2] =  nPpsLen & 0xff;
        memcpy(pPps + 3, ppsData, nPpsLen);

        JNIHelper::freeBuffer(ppsData);
    }

    jbyteArray jbarray = env->NewByteArray(nDatalen);
    if (jbarray != NULL) {
        jbyte *jy = (jbyte*)pSps_PpsBuf;
        env->SetByteArrayRegion(jbarray, 0, nDatalen, jy);
        //jni->CallStaticVoidMethod(jVideoDecoderCenterClass, jDeliverVideoDataMethod, mUserGroupId, mStreamId, jbarray, pts, mMicPos);
        //jni->DeleteLocalRef(jbarray);
    }

   // *ppSps_Pps = pSps_PpsBuf;
    JNIHelper::freeBuffer(spsData);
    return jbarray;
}

static jbyteArray VideoPackUtil_nativeClassInit(JNIEnv* env, jclass clazz) {
    //i
    LOGDXXX("VideoPackUtil.nativeClassInit, register all codec");
    registerACCodecAll();
}


        static JNINativeMethod VideoPackUtilMethods[] = {
        {"unpackHeader",         "([BI)[B",   (void*)VideoPackUtil_UnpackHeader },
        {"unpackFrame",         "([BI)[B",   (void*)VideoPackUtil_UnpackFrame },
        {"packHeader",       "([BI[BI)[B", (void*)VideoPackUtil_PackHeader },
        {"nativeClassInit",  "()V", (void*)VideoPackUtil_nativeClassInit},
};

extern "C"  void register_VideoPackUtil_Jni(JNIEnv *env ) {
    JNIHelper::registerNativeMethod(env, kVideoPackUtilClassPathName, VideoPackUtilMethods, YYArrayLen(VideoPackUtilMethods));
}

NAMESPACE_YYMFW_END