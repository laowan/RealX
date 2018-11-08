package com.ycloud.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;

import com.ycloud.utils.FileUtils;

public class ImageUtils {

	private final static String TAG = "ImageUtils";

	public static class Font {
		int index;
		String content;
		int size;
		int color = -1;
		int x;
		int y;
		Typeface typeface;

		public Font() {
		};

		public Font(String content, int size, int color, int x, int y) {
			this.content = content;
			this.size = size;
			this.color = color;
			this.x = x;
			this.y = y;
		}

		public Font(int index, String content, int size, int color, int x, int y) {
			this.index = index;
			this.content = content;
			this.size = size;
			this.color = color;
			this.x = x;
			this.y = y;
		}

		public Font(String content, int size, int color, int x, int y, Typeface typeface) {
			this.content = content;
			this.size = size;
			this.color = color;
			this.x = x;
			this.y = y;
			this.typeface = typeface;
		}

		public Font(int index, String content, int size, int color, int x, int y, Typeface typeface) {
			this.index = index;
			this.content = content;
			this.size = size;
			this.color = color;
			this.x = x;
			this.y = y;
			this.typeface = typeface;
		}

		/**
		 * @return the content
		 */
		public String getContent() {
			return content;
		}

		/**
		 * @param content
		 *            the content to set
		 */
		public void setContent(String content) {
			this.content = content;
		}

		/**
		 * @return the index
		 */
		public int getIndex() {
			return index;
		}

		/**
		 * @param index
		 *            the index to set
		 */
		public void setIndex(int index) {
			this.index = index;
		}

		/**
		 * @return the typeface
		 */
		public Typeface getTypeface() {
			return typeface;
		}

		/**
		 * @param typeface the typeface to set
		 */
		public void setTypeface(Typeface typeface) {
			this.typeface = typeface;
		}

		
	}

	public static class TextImg {
		public String path;
		public int width;
		public int height;
		public int backgroudColor = -1;

		public TextImg() {
		};

		public TextImg(String path, int width, int height) {
			this.path = path;
			this.width = width;
			this.height = height;
		}

		public TextImg(String path, int width, int height, int backgroudColor) {
			this.path = path;
			this.width = width;
			this.height = height;
			this.backgroudColor = backgroudColor;
		}

	}

	public void textToImg(String srcPath, String desPath, ArrayList<Font> fontList) {
		if (TextUtils.isEmpty(srcPath) || TextUtils.isEmpty(desPath) || fontList == null) {
			Log.e(TAG, "params error!");
			return;
		}
		FileOutputStream os = null;
		Bitmap bitmap=null;
		try {
			bitmap = BitmapFactory.decodeFile(srcPath).copy(Config.ARGB_8888, true);
			Canvas canvas = new Canvas(bitmap);
			TextPaint paint = new TextPaint();
			float baseline = 0;
			for (Font font : fontList) {
				if (font.color >= 0)
					paint.setColor(getColor(font.color));
				paint.setTextSize(font.size);
				if (null != font.typeface)
					paint.setTypeface(font.typeface);
				baseline = paint.baselineShift - paint.ascent();
				// Log.i("paint", "descent:"+paint.descent()+" ascent:"+paint.ascent());
				canvas.drawText(font.content, font.x, (font.y + baseline), paint);
			}
			canvas.save(Canvas.ALL_SAVE_FLAG);
			canvas.restore();
			os = new FileOutputStream(new File(desPath));
			if (FileUtils.getPathExtension(desPath).equalsIgnoreCase("jpeg") || FileUtils.getPathExtension(desPath).equalsIgnoreCase("jpg"))
				bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
			else
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);

		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (null != bitmap && !bitmap.isRecycled()) {
					bitmap.recycle();
					bitmap = null;
				}
				if (null != os) {
					os.flush();
					os.close();
					os = null;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public void textToImg(TextImg textImg, ArrayList<Font> fontList) {
		if (null == textImg || TextUtils.isEmpty(textImg.path) || fontList == null) {
			Log.e(TAG, "params error!");
			return;
		}
		FileOutputStream os = null;
		try {
			Bitmap bitmap = Bitmap.createBitmap(textImg.width, textImg.height, Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);
			if (textImg.backgroudColor >= 0)
				canvas.drawColor(textImg.backgroudColor);
			TextPaint paint = new TextPaint();
			float baseline = 0;
			for (Font font : fontList) {
				if (font.color >= 0)
					paint.setColor(getColor(font.color));
				paint.setTextSize(font.size);
				paint.setFakeBoldText(true);
				if (null != font.typeface)
					paint.setTypeface(font.typeface);
				baseline = paint.baselineShift - paint.ascent();
				// Log.i("paint", "descent:"+paint.descent()+" ascent:"+paint.ascent());
				canvas.drawText(font.content, font.x, (font.y + baseline), paint);
			}
			canvas.save(Canvas.ALL_SAVE_FLAG);
			canvas.restore();
			os = new FileOutputStream(new File(textImg.path));
			if (FileUtils.getPathExtension(textImg.path).equalsIgnoreCase("jpeg") || FileUtils.getPathExtension(textImg.path).equalsIgnoreCase("jpg"))
				bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
			else
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);

		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (null != os) {
					os.flush();
					os.close();
					os = null;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public int getColor(int color) {
		int red = (color & 0xff0000) >> 16;
		int green = (color & 0x00ff00) >> 8;
		int blue = (color & 0x0000ff);
		return Color.rgb(red, green, blue);
	}
}
