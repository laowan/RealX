#include "GLESNativeTools.h"
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

JNIEXPORT void JNICALL Java_com_yy_mediaframeworks_gpuimage_adapter_GLESNativeTools_glReadPixelWithJni
  (JNIEnv * env, jobject obj, jint x, jint y, jint width, jint height, jint format, jint type, jint offset){
	glReadPixels(x, y, width, height, format, type, 0);
}
