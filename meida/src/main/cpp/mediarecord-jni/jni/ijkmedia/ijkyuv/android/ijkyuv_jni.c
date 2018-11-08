/*
 * ijkplayer_jni.c
 *
 * Copyright (c) 2013 Zhang Rui <bbcallen@gmail.com>
 *
 * This file is part of ijkPlayer.
 *
 * ijkPlayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#include <assert.h>
#include <string.h>
#include <pthread.h>
#include <jni.h>
#include <android/log.h>
#include <libyuv.h>
#include<stdio.h>

#define LOG_TAG "yuvnative"
#define ALOGD(fmt, args...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, fmt, ##args))
#define ALOGW(fmt, args...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, fmt, ##args))
#define ALOGE(fmt, args...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##args))

#define JNI_MODULE_PACKAGE      "com/ycloud/yuv"
#define JNI_CLASS_YUV     "com/ycloud/yuv/YUV"
#define JNI_YUV_EXCEPTION "com/ycloud/yuv/YUVException"

#define IJK_CHECK_MPRET_GOTO(retval, env, label) \
    JNI_CHECK_GOTO((retval != EIJK_INVALID_STATE), env, "java/lang/IllegalStateException", NULL, label); \
    JNI_CHECK_GOTO((retval != EIJK_OUT_OF_MEMORY), env, "java/lang/OutOfMemoryError", NULL, label); \
    JNI_CHECK_GOTO((retval == 0), env, JNI_YUV_EXCEPTION, NULL, label);

#if 0
JNIEXPORT void JNICALL YUV_YUVtoRBGA(JNIEnv * env, jobject obj, jbyteArray yuv420sp,
    jint width, jint height, jintArray rgbOut)
{
    int             sz;
    int             i;
    int             j;
    int             Y;
    int             Cr = 0;
    int             Cb = 0;
    int             pixPtr = 0;
    int             jDiv2 = 0;
    int             R = 0;
    int             G = 0;
    int             B = 0;
    int             cOff;
    int w = width;
    int h = height;
    sz = w * h;

    jint *rgbData = (jint*) ((*env)->GetPrimitiveArrayCritical(env, rgbOut, 0));
    jbyte* yuv = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, yuv420sp, 0);

    for(j = 0; j < h; j++) {
             pixPtr = j * w;
             jDiv2 = j >> 1;
             for(i = 0; i < w; i++) {
                     Y = yuv[pixPtr];
                     if(Y < 0) Y += 255;
                     if((i & 0x1) != 1) {
                             cOff = sz + jDiv2 * w + (i >> 1) * 2;
                             Cb = yuv[cOff];
                             if(Cb < 0) Cb += 127; else Cb -= 128;
                             Cr = yuv[cOff + 1];
                             if(Cr < 0) Cr += 127; else Cr -= 128;
                     }

                     //ITU-R BT.601 conversion
                     //
                     //R = 1.164*(Y-16) + 2.018*(Cr-128);
                     //G = 1.164*(Y-16) - 0.813*(Cb-128) - 0.391*(Cr-128);
                     //B = 1.164*(Y-16) + 1.596*(Cb-128);
                     //
                     Y = Y + (Y >> 3) + (Y >> 5) + (Y >> 7);
                     R = Y + (Cr << 1) + (Cr >> 6);
                     if(R < 0) R = 0; else if(R > 255) R = 255;
                     G = Y - Cb + (Cb >> 3) + (Cb >> 4) - (Cr >> 1) + (Cr >> 3);
                     if(G < 0) G = 0; else if(G > 255) G = 255;
                     B = Y + Cb + (Cb >> 1) + (Cb >> 4) + (Cb >> 5);
                     if(B < 0) B = 0; else if(B > 255) B = 255;
                     rgbData[pixPtr++] = 0xff000000 + (R << 16) + (G << 8) + B;
             }
    }

    (*env)->ReleasePrimitiveArrayCritical(env, rgbOut, rgbData, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, yuv420sp, yuv, 0);
}
#else
JNIEXPORT void JNICALL YUV_YUVtoRBGA(JNIEnv * env, jobject obj, jbyteArray yuv420sp,
    jint width, jint height, jintArray rgbOut)
{
    jint *rgbData = (jint*) ((*env)->GetPrimitiveArrayCritical(env, rgbOut, 0));
    jbyte* yuv = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, yuv420sp, 0);

    int sample_size = width*height*3/2;
    int argb_stride = width*4;
    int crop_x = 0;
    int crop_y = 0;
    int src_width = width;
    int src_height = height;
    int crop_width = width;
    int crop_height = height;

    ConvertToARGB(yuv, sample_size,
                  rgbData, argb_stride,
                  crop_x, crop_y,
                  src_width, src_height,
                  crop_width, crop_height,
                  kRotate0,
                  FOURCC_NV12);

    (*env)->ReleasePrimitiveArrayCritical(env, rgbOut, rgbData, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, yuv420sp, yuv, 0);
}
#endif

static int indexx = 0;
void saveFile(uint8* yuv,int size){
 char path[100]={0};
 sprintf(path,"/sdcard/Movies/yuv%d",indexx++);
 FILE *file = fopen(path,"wb");
 int MAXLEN =128;
 unsigned char buf[MAXLEN];
 int i=0;
 for(i=0;i<size;i+=MAXLEN){
  if(size-i>MAXLEN){
    fwrite(yuv+i, sizeof(unsigned char), MAXLEN, file);
  }else {
    fwrite(yuv+i, sizeof(unsigned char), size-i, file);
  }
 }
}

JNIEXPORT void JNICALL YUVtoRGBA_With_ByteBuffer(JNIEnv * env, jobject obj, jobject yuv420sp,jint sample_size,
    jint width, jint height,jint crop_width,jint crop_height,jint argb_stride,jint color_format,jintArray rgbOut)
{
    uint8* yuv  = (*env)->GetDirectBufferAddress(env, yuv420sp);
    if ((*env)->ExceptionCheck(env)) {
       ALOGE("YUV_YUVtoRBGA_With_ByteBuffer ExceptionCheck yuv:%p",yuv);
    }
    /*if(indexx<=0){
     saveFile(yuv,sample_size);
    }*/

    jint *rgbData = (jint*) ((*env)->GetPrimitiveArrayCritical(env, rgbOut, 0));

    int crop_x = 0;
    int crop_y = 0;
    int src_width = width;
    int src_height = height;
    uint32 cc=FOURCC_NV12;
    switch(color_format){
    case 21:/*COLOR_FormatYUV420SemiPlanar*/
    case 0x7fa30c00:/*COLOR_QCOM_FormatYUV420SemiPlanar*/
    case 0x7FA30C04:/*COLOR_QCOM_FormatYUV420SemiPlanar32m*/
	cc= FOURCC_NV12;
        break;
    case 19:/*COLOR_FormatYUV420Planar*/
	cc= FOURCC_I420;
	break;
    default:
	ALOGE("YUV_YUVtoRBGA_With_ByteBuffer unhandler color format:%d",color_format);
	break;
    }

    ConvertToARGB(yuv, sample_size,
                  rgbData, argb_stride,
                  crop_x, crop_y,
                  src_width, src_height,
                  crop_width, crop_height,
                  kRotate0,
                  cc);

    int length = crop_width*crop_height;
    int i=0;
    for(i=0;i<length; i++){
           unsigned int color = rgbData[i];
           int A = color & 0xff000000;
	   int R = (color & 0xff0000) >> 16;
           int G = color & 0xff00;
           int B = (color & 0xff);
	   rgbData[i]=A|(B<<16)|G|R;

   }


    (*env)->ReleasePrimitiveArrayCritical(env, rgbOut, rgbData, 0);
}

JNIEXPORT void JNICALL YUV_YUVtoRBGAWithRGB(JNIEnv * env, jobject obj, jbyteArray yuv420sp,
    jint width, jint height, jintArray rgbOut, jintArray position, jintArray rgb)
{
    jint *rgbData = (jint*) ((*env)->GetPrimitiveArrayCritical(env, rgbOut, 0));
    jbyte* yuv = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, yuv420sp, 0);
	jint *pos = (jint*)((*env)->GetPrimitiveArrayCritical(env,position,0));
	jint *rgbs = (jint*) ((*env)->GetPrimitiveArrayCritical(env,rgb,0));

    int sample_size = width*height*3/2;
    int argb_stride = width*4;
    int crop_x = 0;
    int crop_y = 0;
    int src_width = width;
    int src_height = height;
    int crop_width = width;
    int crop_height = height;

    ConvertToARGB(yuv, sample_size,
                  rgbData, argb_stride,
                  crop_x, crop_y,
                  src_width, src_height,
                  crop_width, crop_height,
                  kRotate0,
                  FOURCC_NV12);

	unsigned int px = (unsigned int)pos[0];
	unsigned int py = (unsigned int)pos[1];
	int tx=0,ty=0,count=0;
	int R=0,G=0,B=0;
	int i;
	for(i=0; i<20; i++){
		tx = px-10+i;
		ty = py-10+i;
		if(tx>0 && tx<crop_width && ty>0 && ty<crop_height){
		   unsigned int color = rgbData[ty*crop_width+tx];
	       R += (color & 0xff0000) >> 16;
           G += (color & 0xff00) >> 8;
           B += (color & 0xff);
		   count++;
		}
	}
	rgbs[0] = R/count;
	rgbs[1] = G/count;
	rgbs[2] = B/count;

    (*env)->ReleasePrimitiveArrayCritical(env, rgbOut, rgbData, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, yuv420sp, yuv, 0);
	(*env)->ReleasePrimitiveArrayCritical(env, position, pos, 0);
	(*env)->ReleasePrimitiveArrayCritical(env, rgb, rgbs, 0);
}

JNIEXPORT void JNICALL YUV_YUVtoARBG(JNIEnv * env, jobject obj, jbyteArray yuv420sp,
    jint width, jint height, jintArray rgbOut)
{
    int             sz;
    int             i;
    int             j;
    int             Y;
    int             Cr = 0;
    int             Cb = 0;
    int             pixPtr = 0;
    int             jDiv2 = 0;
    int             R = 0;
    int             G = 0;
    int             B = 0;
    int             cOff;
    int w = width;
    int h = height;
    sz = w * h;

    jint *rgbData = (jint*) ((*env)->GetPrimitiveArrayCritical(env, rgbOut, 0));
    jbyte* yuv = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, yuv420sp, 0);

    for(j = 0; j < h; j++) {
             pixPtr = j * w;
             jDiv2 = j >> 1;
             for(i = 0; i < w; i++) {
                     Y = yuv[pixPtr];
                     if(Y < 0) Y += 255;
                     if((i & 0x1) != 1) {
                             cOff = sz + jDiv2 * w + (i >> 1) * 2;
                             Cb = yuv[cOff];
                             if(Cb < 0) Cb += 127; else Cb -= 128;
                             Cr = yuv[cOff + 1];
                             if(Cr < 0) Cr += 127; else Cr -= 128;
                     }

                     //ITU-R BT.601 conversion
                     //
                     //R = 1.164*(Y-16) + 2.018*(Cr-128);
                     //G = 1.164*(Y-16) - 0.813*(Cb-128) - 0.391*(Cr-128);
                     //B = 1.164*(Y-16) + 1.596*(Cb-128);
                     //
                     Y = Y + (Y >> 3) + (Y >> 5) + (Y >> 7);
                     R = Y + (Cr << 1) + (Cr >> 6);
                     if(R < 0) R = 0; else if(R > 255) R = 255;
                     G = Y - Cb + (Cb >> 3) + (Cb >> 4) - (Cr >> 1) + (Cr >> 3);
                     if(G < 0) G = 0; else if(G > 255) G = 255;
                     B = Y + Cb + (Cb >> 1) + (Cb >> 4) + (Cb >> 5);
                     if(B < 0) B = 0; else if(B > 255) B = 255;
                     rgbData[pixPtr++] = 0xff000000 + (B << 16) + (G << 8) + R;
             }
    }

    (*env)->ReleasePrimitiveArrayCritical(env, rgbOut, rgbData, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, yuv420sp, yuv, 0);
}

JNIEXPORT void JNICALL YUV_ARGBScale(JNIEnv *env, jobject obj,
    jbyteArray src_argb, jint src_width, jint src_height,
    jbyteArray dst_argb, jint dst_width, jint dst_height, jint filtering_mode) {
    jint* src = (jint*) ((*env)->GetPrimitiveArrayCritical(env, src_argb, 0));
    jint* dst = (jint*) (*env)->GetPrimitiveArrayCritical(env, dst_argb, 0);
    ARGBScale((uint8*)src, src_width*4, src_width, src_height,
        (uint8*)dst, dst_width*4, dst_width, dst_height, filtering_mode);
     (*env)->ReleasePrimitiveArrayCritical(env, src_argb, src, 0);
     (*env)->ReleasePrimitiveArrayCritical(env, dst_argb, dst, 0);
}

JNIEXPORT void JNICALL YUV_RGBAtoI420(JNIEnv * env, jobject obj, jbyteArray rgba,
    jint width, jint height, jbyteArray yuv)
{
    uint8 *dst_y, *dst_u, *dst_v;
    jint* rgbaData = (jint*) ((*env)->GetPrimitiveArrayCritical(env, rgba, 0));
    jbyte* yuvData = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, yuv, 0);
    int w;
    int h;
    w = width > 0 ? width : -width;
    h = height > 0 ? height : -height;
    dst_y = yuvData;
    dst_u = yuvData + w*h;
    dst_v = yuvData + w*h + w*h/4;
    ABGRToI420((uint8*)rgbaData, w*4, dst_y, w, dst_u, w/2, dst_v, w/2, width, height);
    (*env)->ReleasePrimitiveArrayCritical(env, rgba, rgbaData, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, yuv, yuvData, 0);
}

JNIEXPORT void JNICALL YUV_NV21toRGBA(JNIEnv * env, jobject obj, jintArray rgba,
    jint width, jint height, jbyteArray yuv)
{
    uint8 *dst_y, *dst_u, *dst_v;
    jint* rgbaData = (jint*) ((*env)->GetPrimitiveArrayCritical(env, rgba, 0));
    jbyte* yuvData = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, yuv, 0);
    int w;
    int h;
    w = width > 0 ? width : -width;
    h = height > 0 ? height : -height;
    dst_y = yuvData;
    dst_u = yuvData + w*h;
    dst_v = yuvData + w*h + w*h/4;
    ABGRToI420((uint8*)rgbaData, w*4, dst_y, w, dst_u, w/2, dst_v, w/2, width, height);
    (*env)->ReleasePrimitiveArrayCritical(env, rgba, rgbaData, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, yuv, yuvData, 0);
}

JNIEXPORT void JNICALL YC_RGBAtoNV12(JNIEnv * env, jobject obj, jbyteArray rgba,
    jint width, jint height, jbyteArray yuv,int padding, int swap)
{
    jint* rgbaData = (jint*) ((*env)->GetPrimitiveArrayCritical(env, rgba, 0));
    jbyte* destBuff = (jbyte*) (*env)->GetPrimitiveArrayCritical(env, yuv, 0);
	int half_width = (width + 1) / 2;
    int half_height = (height + 1) / 2;
	int tempHeight=-height;
	/*现有代码下，这个得到的是NV12*/
	ARGBToNV21((uint8*)rgbaData, width * 4,
                       destBuff, width,
                       destBuff + width * height + padding, half_width * 2,
                       width, tempHeight);
    (*env)->ReleasePrimitiveArrayCritical(env, rgba, rgbaData, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, yuv, destBuff, 0);
}

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif

static JNINativeMethod g_methods[] = {
   { "ARGBScale", "([BII[BIII)V", (void *)YUV_ARGBScale },
   { "YUVtoRBGA", "([BII[I)V", (void *)YUV_YUVtoRBGA },
   { "YUVtoARBG", "([BII[I)V", (void *)YUV_YUVtoARBG },
   { "RGBAtoI420", "([BII[B)V", (void *)YUV_RGBAtoI420 },
   { "YC_RGBAtoNV12", "([BII[BII)V", (void *)YC_RGBAtoNV12 },
   {"YUVtoRBGAWithRGB","([BII[I[I[I)V",(void *)YUV_YUVtoRBGAWithRGB},
   { "YUVtoRGBAWithByteBuffer", "(Ljava/nio/ByteBuffer;IIIIIII[I)V", (void *)YUVtoRGBA_With_ByteBuffer},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv* env = NULL;

    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        return -1;
    }
    assert(env != NULL);

    // FindClass returns LocalReference
    jclass yuv_clazz = (*env)->FindClass(env, JNI_CLASS_YUV);

    (*env)->RegisterNatives(env, yuv_clazz, g_methods, NELEM(g_methods) );

    ALOGD("yuv so JNI_OnLoad ok");

    (*env)->DeleteLocalRef(env, yuv_clazz);

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM *jvm, void *reserved)
{

}

/*
 * Class:     com_ycloud_yuv_YUV
 * Method:    convertVideoFrame
 * Signature: (Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;IIIII)I
 */
JNIEXPORT jint JNICALL Java_com_ycloud_yuv_YUV_convertVideoFrame
 (JNIEnv *env, jclass class, jobject src, jobject dest, int destFormat, int width, int height, int padding, int swap)
 {
 	if (!src || !dest || !destFormat) {
        return 0;
    }

    jbyte *srcBuff = (*env)->GetDirectBufferAddress(env, src);
    jbyte *destBuff = (*env)->GetDirectBufferAddress(env, dest);

    int half_width = (width + 1) / 2;
    int half_height = (height + 1) / 2;

	ARGBToNV12(srcBuff, width * 4,
                       destBuff, width,
                       destBuff + width * height + padding, half_width * 2,
                       width, height);

}
