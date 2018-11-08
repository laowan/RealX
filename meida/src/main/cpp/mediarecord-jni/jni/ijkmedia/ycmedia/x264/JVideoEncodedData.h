//
// Created by kele on 2016/9/28.
//

#ifndef TRUNK_JVIDEOENCODEDATA_H
#define TRUNK_JVIDEOENCODEDATA_H

#include <jni.h>
#include "Mediabase.h"

class JVideoEncodedData {
public:
    static jclass getVideoEncodedDataClass();
    static jobject newVideoEncodeDataObject(JNIEnv* env, MediaLibrary::VideoEncodedData& cVideoData);
};


#endif //TRUNK_VIDEOENCODEDBUFFERHELPER_H
