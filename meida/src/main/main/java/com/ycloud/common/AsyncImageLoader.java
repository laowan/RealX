package com.ycloud.common;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;

public class AsyncImageLoader {
	private HashMap<String, SoftReference<Bitmap>> imageCache;

	public AsyncImageLoader() {
		this.imageCache = new HashMap<String, SoftReference<Bitmap>>();
	}

	public Bitmap loadBitmapByPath(final String imagePath, final ImageCallback imageCallback) {
		if (imageCache.containsKey(imagePath)) {
			// 从缓存中获取
			SoftReference<Bitmap> softReference = imageCache.get(imagePath);
			Bitmap bitmap = softReference.get();
			if (bitmap != null) {
				return bitmap;
			}
		}
		final Handler handler = new Handler() {
			public void handleMessage(Message message) {
				imageCallback.imageLoaded((Bitmap) message.obj, imagePath);
			}
		};
		// 建立新一个获取SD卡的图片
		new Thread() {
			@Override
			public void run() {
				Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
				imageCache.put(imagePath, new SoftReference<Bitmap>(bitmap));
				Message message = handler.obtainMessage(0, bitmap);
				handler.sendMessage(message);
			}
		}.start();
		return null;
	}

	// 回调接口
	public interface ImageCallback {
		public void imageLoaded(Bitmap imageBitmap, String imagePath);
	}
}
