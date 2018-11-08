//
// Created by Administrator on 2016/9/20.
//

#include <jni.h>
#include "x264Encoder.h"
#include "Macros.h"
#include "JNIHelper.h"
#include "Mediabase.h"
#include <string>
#include "Common.h"

#include "libyuv/include/libyuv.h"

static const char* const IllegalStateException = "java/lang/IllegalStateException";
const char* const kImageUtilClassPathName = "com/ycloud/utils/ImageFormatUtil";

NAMESPACE_YYMFW_BEGIN

    static void ImageUtil_RBGAtoYUV(JNIEnv* env, jobject thiz, jbyteArray abgr32In, jint width, jint height, jbyteArray yuv420spOut)
    {
        jbyte *yuvOut = (jbyte*) env->GetPrimitiveArrayCritical(yuv420spOut, 0);
        jbyte* abgrIn = (jbyte*) env->GetPrimitiveArrayCritical(abgr32In, 0);
        //   int input_frame_size = width * height * 4;
        int y_plane_size = width * height;
        uint8* dst_y = (uint8*)yuvOut;

        //exchage v/u by liush
        //int u_plane_size = ((width + 1) / 2) * ((height) / 2);
        uint8* dst_u = (uint8*)(dst_y + y_plane_size);
        //int v_plane_size = ((width + 1) / 2) * ((height) / 2);
        uint8* dst_v = (uint8*)(dst_u + y_plane_size / 4);
        libyuv::ABGRToI420((uint8*)abgrIn, width * 4,
                           dst_y, width,
                           dst_u, (width + 1) / 2,
                           dst_v, (width + 1) / 2,
                           width, height);
        env->ReleasePrimitiveArrayCritical(abgr32In, abgrIn, 0);
        env->ReleasePrimitiveArrayCritical(yuv420spOut, yuvOut, 0);


    }


static void ImageUtil_RBGABufferToYUV(JNIEnv* env, jobject thiz, jobject abgr32In, jint offset, jint width, jint height, jbyteArray yuv420spOut)
{

	//LOGD("[ffmux] ImageUtil_RBGABufferToYUV  begin");
	bool directMode = false;
	void *data = env->GetDirectBufferAddress(abgr32In);

	//LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos begin4444444");	
	   jbyteArray byteArray = NULL;
	   jclass byteBufClass = env->FindClass("java/nio/ByteBuffer");
	   if(byteBufClass == NULL) {
			LOGE("rgbBufferToYUV error, class ByteBuffer is not found!!");
			return;
	   }

	   	//LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos 1");
	
	   if (data == NULL) {
	   	//not directbuffer
		   jmethodID arrayID =env->GetMethodID(byteBufClass, "array", "()[B");
			if(arrayID != NULL) {
				LOGE("rgbBufferToYUV error, array method is not found for nondirect bytebuffer!!");
				if(byteBufClass != NULL) {
					env->DeleteLocalRef(byteBufClass);
				}
				return;
			}

			//LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos 2");
	
		   byteArray =(jbyteArray)env->CallObjectMethod(abgr32In, arrayID);
		   if (byteArray == NULL) {
		   		if(byteBufClass != NULL) {
					env->DeleteLocalRef(byteBufClass);
				}
			   return;
		   }

		   //LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos 3");

		   data = env->GetPrimitiveArrayCritical(byteArray, 0);
		   //dataSize = (size_t) env->GetArrayLength(byteArray);
	   } else {
		   //dataSize = (size_t) env->GetDirectBufferCapacity(byteBuf);
		    //LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos 4");
		   directMode = true;
	   }


	 //LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos 5");

		jbyte *yuvOut = (jbyte*) env->GetPrimitiveArrayCritical(yuv420spOut, 0);

     	//LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos begin233323");

     //   int input_frame_size = width * height * 4;
        int y_plane_size = width * height;
        uint8* dst_y = (uint8*)yuvOut;

        //exchage v/u by liush
        //int u_plane_size = ((width + 1) / 2) * ((height) / 2);
        uint8* dst_u = (uint8*)(dst_y + y_plane_size);
        //int v_plane_size = ((width + 1) / 2) * ((height) / 2);
        uint8* dst_v = (uint8*)(dst_u + y_plane_size / 4);
        libyuv::ABGRToI420((uint8*)data+offset, width * 4,
                           dst_y, width,
                           dst_u, (width + 1) / 2,
                           dst_v, (width + 1) / 2,
                           width, height);

		//LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos 6");
		   
		if(!directMode) {
			 env->ReleasePrimitiveArrayCritical(byteArray, data, 0);
		}

		//LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos 8");
		
	    env->ReleasePrimitiveArrayCritical(yuv420spOut, yuvOut, 0);

		//LOGD("[ffmux] ImageUtil_RBGABufferToYUV  pos 7");
	
		if(byteBufClass != NULL) {
			env->DeleteLocalRef(byteBufClass);
		}
		
		//LOGD("[ffmux] ImageUtil_RBGABufferToYUV  end");
}



    static JNINativeMethod gImageUtilMethods[] = {
            {"RBGAtoYUV",         "([BII[B)V",   (void*)ImageUtil_RBGAtoYUV },
            {"RAGABufferToYUV",   "(Ljava/nio/ByteBuffer;III[B)V", (void*)ImageUtil_RBGABufferToYUV},
    };

    extern "C"  void register_ImageUitl_Jni(JNIEnv *env ) {
        JNIHelper::registerNativeMethod(env, kImageUtilClassPathName, gImageUtilMethods, YYArrayLen(gImageUtilMethods));
    }





NAMESPACE_YYMFW_END