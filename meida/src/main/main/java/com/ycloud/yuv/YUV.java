package com.ycloud.yuv;

import android.util.Log;

import com.ycloud.utils.FileUtils;
import com.ycloud.utils.YYLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class YUV {
	static String TAG="YUV";
	/* filtering mode */
	// Point sample; Fastest.
	public static final int kFilterNone = 0;
	// Filter horizontally only.
	public static final int kFilterLinear = 1;  
	// Faster than box, but lower quality scaling down.
	public static final int kFilterBilinear = 2; 
	// Highest quality.
	public static final int kFilterBox = 3;
	private static FileOutputStream outyuv=null;
	private static boolean isOutyuvClosed=true;
	private static  String outyuvFilename= FileUtils.getInnerSDCardPath()+"/Movies/"+"yctest.yuv";
	static {
		try {
			System.loadLibrary("ycmediayuv");
		} catch (UnsatisfiedLinkError e) {
			YYLog.error(TAG, "LoadLibrary failed, UnsatisfiedLinkError " + e.getMessage());
		}
    }
	private static int yuvcnt=0;
	public  static void writeYUV(byte[] data){
		if(outyuv==null) {
			yuvcnt=0;
			File file = new File(outyuvFilename);
			if (file.exists()) {
				Log.d(TAG, "outyuv file [" + outyuvFilename + " exists ,delete it");
				file.delete();
			}
			try {
				outyuv = new FileOutputStream(outyuvFilename);
				if(outyuv!=null){
					isOutyuvClosed=false;
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		if (outyuv != null && isOutyuvClosed==false)  {
			try {
				outyuv.write(data);
				yuvcnt++;
				isOutyuvClosed=false;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else
			Log.d(TAG,"outyuv file is null or already closed");

	}
	public static void stopWriteYUV(){
		if(isOutyuvClosed==false){
			if(outyuv!=null){
				try {
					outyuv.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				isOutyuvClosed=true;
				Log.d(TAG,"save all ["+yuvcnt+"] to file ok");
			}
		}else
			Log.d(TAG,"already close outyuv");
	}
    public static native void ARGBScale(byte[] src_argb, int src_width, int src_height,
    		byte[]dst_argb, int dst_width, int dst_height, int filtering_mode);
    public static native void YUVtoRBGA(byte[] yuv, int width, int height, int[] out);
    public static native void YUVtoARBG(byte[] yuv, int width, int height, int[] out);
    public static native void RGBAtoI420(byte[] rgba, int width, int height, byte[] out);
	public static native void YC_RGBAtoNV12(byte[] rgba, int width, int height, byte[] out,int padding,int swap);
	public static native void YUVtoRBGAWithRGB(byte[] yuv, int width, int height, int[] out, int[] position, int[] rgb);
    public static native void YUVtoRGBAWithByteBuffer(ByteBuffer yuv,int size, int width, int height,int crop_width,int crop_height,int argbStride,int colorFormat, int[] out);
	/*ARGBToNV12*/
	public native static int convertVideoFrame(ByteBuffer src, ByteBuffer dest, int destFormat, int width, int height, int padding, int swap);

}
