/***************** BEGIN FILE HRADER BLOCK *********************************
 *                                                                         *
 *   Copyright (C) 2016 by YY.inc.                                         *
 *                                                                         *
 *   Proprietary and Trade Secret.                                         *
 *                                                                         *
 *   Authors:                                                              *
 *   1): Cheng Yu -- <chengyu@yy.com>                                      *
 *                                                                         *
 ***************** END FILE HRADER BLOCK ***********************************/

package com.ycloud.gles;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

public class GLTexture {
	private int mTextureID = -1;
	private int mTarget = GLES20.GL_TEXTURE_2D;
	private int mWidth = 0;
	private int mHeight = 0;
	private int mFormat = GLES20.GL_RGBA;

	public GLTexture(int target) {
		mTarget = target;
	}

	public void create(int width, int height, int format) {
		destory();

		int[] textureHandles = new int[1];
		GLES20.glGenTextures(1, textureHandles, 0);
		mTextureID = textureHandles[0];

		GLES20.glBindTexture(mTarget, mTextureID);
		GLES20.glTexParameteri(mTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(mTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(mTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(mTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexImage2D(mTarget, 0, format, width, height, 0, format, GLES20.GL_UNSIGNED_BYTE, null);
		mWidth = width;
		mHeight = height;
		mFormat = format;
	}

	public void destory() {
		if (mTextureID != -1){
			int[] textureHandles = new int[1];
			textureHandles[0] = mTextureID;
			GLES20.glDeleteTextures(1, textureHandles, 0);
			mTextureID = -1;
			mWidth = mHeight = 0;
		}
	}

	public void loadTextures(Context context, int resID) {
		// Load input bitmap
		Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resID);
		mWidth = bitmap.getWidth();
		mHeight = bitmap.getHeight();

		// Upload to texture
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);
		GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

		// Set texture parameters
		GLES20.glBindTexture(mTarget, mTextureID);
		GLES20.glTexParameteri(mTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(mTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(mTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(mTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
	}

	public void bindFBO(int framebuffer) {
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
		GLES20.glBindTexture(mTarget, mTextureID);
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, mTarget, mTextureID, 0);
	}

	public int getTarget() {
		return mTarget;
	}

	public int getTextureId() {
		return mTextureID;
	}

	public int getWidth() {
		return mWidth;
	}

	public int getHeight() {
		return mHeight;
	}

}
