package com.ycloud.utils;

import java.lang.reflect.Field;

import android.graphics.BitmapFactory.Options;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import com.ycloud.api.config.AspectRatioType;

public class ImageSizeUtil
{
	/**
	 * 根据需求的宽和高以及图片实际的宽和高计算SampleSize
	 * 
	 * @param options
	 * @return
	 */
	public static int caculateInSampleSize(Options options, int reqWidth,
			int reqHeight)
	{
		int width = options.outWidth;
		int height = options.outHeight;

		int inSampleSize = 1;

		if (width > reqWidth || height > reqHeight)
		{
			int widthRadio = Math.round(width * 1.0f / reqWidth);
			int heightRadio = Math.round(height * 1.0f / reqHeight);

			inSampleSize = Math.max(widthRadio, heightRadio);
		}

		return inSampleSize;
	}

	/**
	 * 根据ImageView获适当的压缩的宽和高
	 * 
	 * @param imageView
	 * @return
	 */
	public static ImageSize getImageViewSize(ImageView imageView)
	{

		ImageSize imageSize = new ImageSize();
		DisplayMetrics displayMetrics = imageView.getContext().getResources()
				.getDisplayMetrics();
		

		LayoutParams lp = imageView.getLayoutParams();

		int width = imageView.getWidth();// 获取imageview的实际宽度
		if (width <= 0)
		{
			width = lp.width;// 获取imageview在layout中声明的宽度
		}
		if (width <= 0)
		{
			 //width = imageView.getMaxWidth();// 检查最大值
			width = getImageViewFieldValue(imageView, "mMaxWidth");
		}
		if (width <= 0)
		{
			width = displayMetrics.widthPixels;
		}

		int height = imageView.getHeight();// 获取imageview的实际高度
		if (height <= 0)
		{
			height = lp.height;// 获取imageview在layout中声明的宽度
		}
		if (height <= 0)
		{
			height = getImageViewFieldValue(imageView, "mMaxHeight");// 检查最大值
		}
		if (height <= 0)
		{
			height = displayMetrics.heightPixels;
		}
		imageSize.width = width;
		imageSize.height = height;

		return imageSize;
	}

	public static class ImageSize
	{
		int width;
		int height;
	}
	
	/**
	 * 通过反射获取imageview的某个属性值
	 * 
	 * @param object
	 * @param fieldName
	 * @return
	 */
	private static int getImageViewFieldValue(Object object, String fieldName)
	{
		int value = 0;
		try
		{
			Field field = ImageView.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			int fieldValue = field.getInt(object);
			if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE)
			{
				value = fieldValue;
			}
		} catch (Exception e)
		{
		}
		return value;

	}

	public static Rect getCropRect(int w, int h, AspectRatioType aspect) {
		Rect rect = new Rect(0, 0, w, h);
		float inputAspect = (float) w / (float) (h);

		if (w > h) {
			float aspect4v3 = (float) 4 / (float) 3;
			float aspect16v9 = (float) 16 / (float) 9;

			// 目前打开相机业务端设置固定为 4:3,
			switch (aspect) {
				case ASPECT_RATIO_4_3:  // 16:9 --> 4:3 左右裁剪
					if (Float.compare(inputAspect, aspect4v3) != 0) {
						int newW = (int) (h * aspect4v3);
						rect.left += (w - newW) / 2;
						rect.right -= (w - newW) / 2;
					}
					break;
				case ASPECT_RATIO_16_9: // 4:3 -- > 16:9 上下裁剪
					if (Float.compare(inputAspect, aspect16v9) != 0) {
						int newH = (int) (w / aspect16v9);
						rect.top += (h - newH) / 2;
						rect.bottom -= (h - newH) / 2;
					}
					break;
				case ASPECT_RATIO_1_1:  // 4:3 -- > 1:1  左右裁剪
					if (Float.compare(inputAspect, 1.0f) != 0) {
						int newW = h;
						rect.left += (w - newW) / 2;
						rect.right -= (w - newW) / 2;
					}
					break;
				default:
					break;
			}
		} else {
			float aspect3v4 = (float) 3 / (float) 4;
			float aspect9v16 = (float) 9 / (float) 16;

			// 目前打开相机业务端设置固定为 4:3,
			switch (aspect) {
				case ASPECT_RATIO_4_3:  // 9:16 --> 3:4 上下裁剪
					if (Float.compare(inputAspect, aspect3v4) != 0) {
						int newH = (int)(w / aspect3v4);
						rect.top += (h-newH)/2;
						rect.bottom -= (h-newH)/2;
					}
					break;
				case ASPECT_RATIO_16_9: // 3:4 -- > 9:16 左右裁剪
					if (Float.compare(inputAspect, aspect9v16) != 0) {
						int newW = (int)(h * aspect9v16);
						rect.left += (w-newW)/2;
						rect.right -= (w-newW)/2;
					}
					break;
				case ASPECT_RATIO_1_1: // 3:4 -> 1:1 上下裁剪
					if (Float.compare(inputAspect, 1.0f) != 0) {
						int newH = w;
						rect.top += (h-newH)/2;
						rect.bottom -= (h-newH)/2;
					}
					break;
				default:
					break;
			}
		}

		return rect;
	}



}
