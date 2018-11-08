/*
 * Copyright (C) 2013 MorihiroSoft
 * Copyright 2013 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ycloud.mediarecord;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.Surface;

import com.ycloud.gles.core.EglContextWrapper;
import com.ycloud.gles.core.EglHelperFactory;
import com.ycloud.gles.core.GLBuilder;
import com.ycloud.gles.core.IEglHelper;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class InputSurface {
	//---------------------------------------------------------------------
	// MEMBERS
	//---------------------------------------------------------------------
	private Surface    mSurface    = null;
	private GLBuilder.EGLConfigChooser mConfigChooser = null;
	private GLBuilder.EGLContextFactory mEglContextFactory = null;
	private GLBuilder.EGLWindowSurfaceFactory mEglWindowSurfaceFactory = null;
	private IEglHelper mEglHelper = null;
	private EglContextWrapper mEglContext = EglContextWrapper.EGL_NO_CONTEXT_WRAPPER;

	//---------------------------------------------------------------------
	// PUBLIC METHODS
	//---------------------------------------------------------------------
	public InputSurface(Surface surface) {
		if (surface == null) {
			throw new NullPointerException();
		}
		mSurface = surface;
		eglSetup();
	}

	public void release() {
		mEglHelper.destroySurface();
		mEglHelper.finish();

		mSurface.release();

		mSurface    = null;
	}

	public Surface getSurface() {
		return mSurface;
	}

	public void makeCurrent() {
		if (mEglHelper != null) {
			mEglHelper.makeCurrent();
		}
	}

	public boolean swapBuffers() {
		if (mEglHelper != null) {
			mEglHelper.swap();
		}
		return true;
	}

	public void setPresentationTime(long nsecs) {
		mEglHelper.setPresentationTime(nsecs);
	}

	//---------------------------------------------------------------------
	// PRIVATE...
	//---------------------------------------------------------------------
	private void eglSetup() {
		if (mConfigChooser == null) {
			mConfigChooser = new GLBuilder.SimpleEGLConfigChooser(true, 2);
		}
		if (mEglContextFactory == null) {
			mEglContextFactory = new GLBuilder.DefaultContextFactory(2);
		}
		if (mEglWindowSurfaceFactory == null) {
			mEglWindowSurfaceFactory = new GLBuilder.DefaultWindowSurfaceFactory();
		}

		mEglHelper = EglHelperFactory.create(mConfigChooser, mEglContextFactory, mEglWindowSurfaceFactory);
		mEglContext = mEglHelper.start(mEglContext);
		mEglHelper.createSurface(mSurface);
	}
}
