/*
 * Copyright 2014 Google Inc. All rights reserved.
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

package com.ycloud.gles;

import android.opengl.Matrix;


import com.ycloud.utils.OpenGlUtils;

import java.nio.FloatBuffer;

/**
 * This class essentially represents a viewport-sized sprite that will be rendered with
 * a texture, usually from an external source like the camera or video decoder.
 */
public class FullFrameRect {

    private Drawable2d mRectDrawable;
    private Texture2dProgram mProgram;
    public final float[] IDENTITY_MATRIX = new float[16];
    public final FloatBuffer DEFAULT_TEX_COORD_BUFFER = Drawable2d.FULL_RECTANGLE_TEX_BUF;
//    private FloatBuffer mTexCoordArray;
//    private FloatBuffer mExtraTexCoordArray;
//    private FloatBuffer mExtraTexCoordArray2;
    private float vertices[] = new float[8];

    private FloatBuffer[] mCurTexCoordArray = new FloatBuffer[3];
    private FloatBuffer[] mOrigTexCoordArray = new FloatBuffer[3];

    public static int MAIN_TEXTURE = 0;
    public static int EXTRA_TEXTURE1 = 1;
    public static int EXTRA_TEXTURE2 = 2;

    private boolean[] mFlipX = new boolean[]{false, false, false};
    private boolean[] mFlipY = new boolean[]{false, false, false};
    //for test
    private boolean need2print = true;
    /**
     * Prepares the object.
     *
     * @param program The program to use.  FullFrameRect takes ownership, and will release
     *     the program when no longer needed.
     */
    public FullFrameRect(Texture2dProgram program) {
        this(program, Drawable2d.Prefab.FULL_RECTANGLE);
    }

    public FullFrameRect(Texture2dProgram program, Drawable2d.Prefab d2dType) {
        mProgram = program;
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
        mRectDrawable = new Drawable2d(d2dType);

        mCurTexCoordArray[MAIN_TEXTURE] = mOrigTexCoordArray[MAIN_TEXTURE] = mRectDrawable.getTexCoordArray();
        mCurTexCoordArray[EXTRA_TEXTURE1] = mOrigTexCoordArray[EXTRA_TEXTURE1] = DEFAULT_TEX_COORD_BUFFER;
        mCurTexCoordArray[EXTRA_TEXTURE2] = mOrigTexCoordArray[EXTRA_TEXTURE2] = DEFAULT_TEX_COORD_BUFFER;
    }

    public FullFrameRect(Texture2dProgram program, Drawable2d.Prefab d2dType, FloatBuffer extra1Cord, FloatBuffer extra2Cord) {
        mProgram = program;
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
        mRectDrawable = new Drawable2d(d2dType);

        mCurTexCoordArray[MAIN_TEXTURE] = mOrigTexCoordArray[MAIN_TEXTURE] = mRectDrawable.getTexCoordArray();
        mCurTexCoordArray[EXTRA_TEXTURE1] = mOrigTexCoordArray[EXTRA_TEXTURE1] = extra1Cord;
        mCurTexCoordArray[EXTRA_TEXTURE2] = mOrigTexCoordArray[EXTRA_TEXTURE2] = extra2Cord;
    }

    public void changeDrawable2d(Drawable2d.Prefab d2dType){
        //TODO:需要重置所有texCoordArray
        mRectDrawable = new Drawable2d(d2dType);
    }
    /**
     * Releases resources.
     * <p>
     * This must be called with the appropriate EGL context current (i.e. the one that was
     * current when the constructor was called).  If we're about to destroy the EGL context,
     * there's no value in having the caller make it current just to do this cleanup, so you
     * can pass a flag that will tell this function to skip any EGL-context-specific cleanup.
     */
    public void release(boolean doEglCleanup) {
        if (mProgram != null) {
            if (doEglCleanup) {
                mProgram.release();
            }
            mProgram = null;
        }
    }

    public void release() {
        release(true);
    }

    /**
     * Returns the program currently in use.
     */
    public Texture2dProgram getProgram() {
        return mProgram;
    }

    /**
     * Changes the program.  The previous program will be released.
     * <p>
     * The appropriate EGL context must be current.
     */
    public void changeProgram(Texture2dProgram program) {
        mProgram.release();
        mProgram = program;
    }

    /**
     * Creates a texture object suitable for use with drawFrame().
     */
    public int createTextureObject() {
        return mProgram.createTextureObject();
    }

    public void scaleMVPMatrix(float x, float y) {
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
        Matrix.scaleM(IDENTITY_MATRIX, 0, x, y, 1f);
    }


    public void drawFrame(int textureId, float[] texMatrix) {
        drawFrame(textureId, texMatrix, -1);
    }

    /**
     * Draws a viewport-filling rect, texturing it with the specified texture object.
     */
    public void drawFrame(int textureId, float[] texMatrix, int extraTextureId) {
        // Use the identity matrix for MVP so our 2x2 FULL_RECTANGLE covers the viewport.
        drawFrame(textureId, texMatrix, extraTextureId, -1);
    }

    /**
     * Draws a viewport-filling rect, texturing it with the specified texture object.
     */
    public void drawFrame(int textureId, float[] texMatrix, int extraTextureId, int extraTextureId2) {
        // Use the identity matrix for MVP so our 2x2 FULL_RECTANGLE covers the viewport.
        if (need2print){
            //YMFLog.info(this,"drawFrame extra1="+extraTextureId+" extra2="+extraTextureId2);
            need2print = false;
        }

        mProgram.draw(IDENTITY_MATRIX, mRectDrawable.getVertexArray(), 0,
                mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
                mRectDrawable.getVertexStride(),
                texMatrix, mCurTexCoordArray[MAIN_TEXTURE], textureId,
                mRectDrawable.getTexCoordStride(),
                mCurTexCoordArray[EXTRA_TEXTURE1], extraTextureId,
                mCurTexCoordArray[EXTRA_TEXTURE2], extraTextureId2);
    }

    /**
     * Draws a viewport-filling rect, texturing it with the specified texture object.
     */
    public void drawFrameForBlurBitmap(int textureId, float[] texMatrix, int extraTextureId, int extraTextureId2, FloatBuffer vertexBuffer) {
        // Use the identity matrix for MVP so our 2x2 FULL_RECTANGLE covers the viewport.
        if (need2print){
            //YMFLog.info(this,"drawFrame extra1="+extraTextureId+" extra2="+extraTextureId2);
            need2print = false;
        }

        mProgram.draw(IDENTITY_MATRIX, /*mRectDrawable.getVertexArray()*/vertexBuffer, 0,
                mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
                mRectDrawable.getVertexStride(),
                texMatrix, mCurTexCoordArray[MAIN_TEXTURE], textureId,
                mRectDrawable.getTexCoordStride(),
                mCurTexCoordArray[EXTRA_TEXTURE1], extraTextureId,
                mCurTexCoordArray[EXTRA_TEXTURE2], extraTextureId2);
    }

    //只改动MAIN_TEXTURE的texCord，水印和贴纸不动
    public void adjustTexture(float incomingWidth, float incomingHeight, float outputWidth, float outputHeight) {
        need2print =true;
        //adjustTexture需要重置整个texCoordArray
        mCurTexCoordArray[MAIN_TEXTURE] = mOrigTexCoordArray[MAIN_TEXTURE];
        mCurTexCoordArray[EXTRA_TEXTURE1] = mOrigTexCoordArray[EXTRA_TEXTURE1];
        mCurTexCoordArray[EXTRA_TEXTURE2] = mOrigTexCoordArray[EXTRA_TEXTURE2];

        for (int i = 0; i< mCurTexCoordArray.length; i++){
            if (mFlipX[i]){
                mCurTexCoordArray[i] = OpenGlUtils.setFlipX(mCurTexCoordArray[i]);
            }

            if (mFlipY[i]){
                mCurTexCoordArray[i] = OpenGlUtils.setFlipY(mCurTexCoordArray[i]);
            }
        }
        mCurTexCoordArray[MAIN_TEXTURE] = OpenGlUtils.adjustTexture(mCurTexCoordArray[MAIN_TEXTURE], incomingWidth, incomingHeight, outputWidth, outputHeight);
    }

    public void adjustAllTexutre(float incomingWidth, float incomingHeight, float outputWidth, float outputHeight) {
        //adjustTexture需要重置整个texCoordArray
        mCurTexCoordArray[MAIN_TEXTURE] = mOrigTexCoordArray[MAIN_TEXTURE];
        mCurTexCoordArray[EXTRA_TEXTURE1] = mOrigTexCoordArray[EXTRA_TEXTURE1];
        mCurTexCoordArray[EXTRA_TEXTURE2] = mOrigTexCoordArray[EXTRA_TEXTURE2];

        for (int i = 0; i< mCurTexCoordArray.length; i++){
            if (mFlipX[i]){
                mCurTexCoordArray[i] = OpenGlUtils.setFlipX(mCurTexCoordArray[i]);
            }

            if (mFlipY[i]){
                mCurTexCoordArray[i] = OpenGlUtils.setFlipY(mCurTexCoordArray[i]);
            }
            mCurTexCoordArray[i] = OpenGlUtils.adjustTexture(mCurTexCoordArray[i], incomingWidth, incomingHeight, outputWidth, outputHeight);
        }
    }

    public void setTextureFlipX(int type) {
        need2print =true;
        if (type == MAIN_TEXTURE){
            mFlipX[MAIN_TEXTURE] = !mFlipX[MAIN_TEXTURE];
            mCurTexCoordArray[MAIN_TEXTURE] = OpenGlUtils.setFlipX(mCurTexCoordArray[MAIN_TEXTURE]);
        }else if(type == EXTRA_TEXTURE1){
            mFlipX[EXTRA_TEXTURE1] = !mFlipX[EXTRA_TEXTURE1];
            mCurTexCoordArray[EXTRA_TEXTURE1] = OpenGlUtils.setFlipX(mCurTexCoordArray[EXTRA_TEXTURE1]);
        }else if(type == EXTRA_TEXTURE2){
            mFlipX[EXTRA_TEXTURE2] = !mFlipX[EXTRA_TEXTURE2];
            mCurTexCoordArray[EXTRA_TEXTURE2] = OpenGlUtils.setFlipX(mCurTexCoordArray[EXTRA_TEXTURE2]);
        }

    }

    public void setTextureFlipY(int type) {
        need2print =true;
        if (type == MAIN_TEXTURE){
            mFlipY[MAIN_TEXTURE] = !mFlipY[MAIN_TEXTURE];
            mCurTexCoordArray[MAIN_TEXTURE] = OpenGlUtils.setFlipY(mCurTexCoordArray[MAIN_TEXTURE]);
        }else if(type == EXTRA_TEXTURE1){
            mFlipY[EXTRA_TEXTURE1] = !mFlipY[EXTRA_TEXTURE1];
            mCurTexCoordArray[EXTRA_TEXTURE1] = OpenGlUtils.setFlipY(mCurTexCoordArray[EXTRA_TEXTURE1]);
        }else if(type == EXTRA_TEXTURE2){
            mFlipY[EXTRA_TEXTURE2] = !mFlipY[EXTRA_TEXTURE2];
            mCurTexCoordArray[EXTRA_TEXTURE2] = OpenGlUtils.setFlipY(mCurTexCoordArray[EXTRA_TEXTURE2]);
        }
    }


    public void drawFrame(int ytextureId, int utextureId, int vtextureId, float[] texMatrix) {
        drawFrame(-1, texMatrix, -1, ytextureId, utextureId, vtextureId);
    }

    public void drawFrame(int textureId, float[] texMatrix, int waterMarkTextureId,
                          int ytextureId, int utextureId, int vtextureId) {
    // Use the identity matrix for MVP so our 2x2 FULL_RECTANGLE covers the viewport.
    mProgram.draw(OpenGlUtils.IDENTITY_MATRIX, mRectDrawable.getVertexArray(), 0,
            mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
            mRectDrawable.getVertexStride(),
            texMatrix, mRectDrawable.getTexCoordArray(), textureId,
            mRectDrawable.getTexCoordStride(), waterMarkTextureId, ytextureId, utextureId, vtextureId);
    }

    public void setClipTextureCord(float [] ClipTexture) {
        mRectDrawable.setTexCoordArray(ClipTexture);
    }
}
