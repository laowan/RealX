package com.ycloud.mediafilters;
import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import com.ycloud.mediarecord.R;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.ymrmodel.YYMediaSample;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.GL_SRC_ALPHA;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE1;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glBlendFunc;
import static android.opengl.GLES20.glBufferData;
import static android.opengl.GLES20.glDeleteBuffers;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glGenBuffers;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glUniform1f;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;
import static android.opengl.GLES20.glViewport;

//1080 1920  width height
public class BackgroundSegmentFilter {
	private final static String TAG 				= 	BackgroundSegmentFilter.class.getSimpleName();
	private static volatile BackgroundSegmentFilter singleton;
	private Context mActivityContext;
	private int mSubtractProgram = -1;
	private int mSubtractUMvpMatrixHandler;
	private int mSubtractAVertexPosHandler;
	private int mSubtractAVertexUVHandler;
	private int mSubtractUTextureHandler;
	private int mSubtractUTextureBGHandler;
	private int mSubtractUThresholdHandler;

	private int mSegmentProgram = -1;
	private int mSegmentUMvpMatrixHandler;
	private int mSegmentAVertexPosHandler;
	private int mSegmentAVertexUVHandler;
	private int mSegmentUTextureHandler;
	private int mSegmentUTextureBGHandler;

	private int mBGProgram = -1;
	private int mBGUMvpMatrixHandler;
	private int mBGAVertexPosHandler;
	private int mBGAVertexUVHandler;
	private int mBGUTextureHandler;

	private int mPyrDownProgram = -1;
	private int mPyrDownUMvpMatrixHandler;
	private int mPyrDownAVertexPosHandler;
	private int mPyrDownAVertexUVHandler;
	private int mPyrDownUTextureHandler;

	private int mErodeProgram = -1;
	private int mErodeUMvpMatrixHandler;
	private int mErodeAVertexPosHandler;
	private int mErodeAVertexUVHandler;
	private int mErodeUTextureHandler;

	private int mDilateProgram = -1;
	private int mDilateUMvpMatrixHandler;
	private int mDilateAVertexPosHandler;
	private int mDilateAVertexUVHandler;
	private int mDilateUTextureHandler;

	private ByteBuffer mBufferBG = null;
	private float[] mMVPMatrix					= new float[16];
	private float[] mModelMatrix				= new float[16];
	private float[] mViewMatrix					= new float[16];
	private float[] mCurMatrix					= new float[16];
	private float[] mProjectMatrix				= new float[16];
	private IntBuffer mVBOBuffer;
	private FloatBuffer mFloatBuffer				= null;
	private float mThreshold = 0.1f;//100
	private float width = 1080.f/1920.f;
	private float[] _textureVUV = {
			-width, 	-1.0f, 0.0f, 0.0f, 1.0f,
			width, 	-1.0f, 0.0f, 1.0f, 1.0f,
			-width,   1.0f, 0.0f, 0.0f, 0.0f,
			width,  	 1.0f, 0.0f, 1.0f, 0.0f
	};
	private float[] _textureBGVUV = {
			-width, 	-1.0f, 0.0f, 0.0f, 0.0f,
			width, 	-1.0f, 0.0f, 1.0f, 0.0f,
			-width,   1.0f, 0.0f, 0.0f, 1.0f,
			width,  	 1.0f, 0.0f, 1.0f, 1.0f
	};
	private int mWidth = 1080;
	private int mHeight = 1920;
	IntBuffer mTextures;
	IntBuffer mFrameBuffers;
	IntBuffer mOldFrameBuffers;
	public BackgroundSegmentFilter(Context context) {
		mActivityContext = context;
	}
	public void init(){
		mBGProgram = OpenGlUtils.createProgram(readTextFileFromRawResource(mActivityContext, R.raw.bg_vert), readTextFileFromRawResource(mActivityContext, R.raw.bg_frag));
		mBGAVertexPosHandler 				= glGetAttribLocation(mBGProgram,    		"a_VertexPos");
		mBGUMvpMatrixHandler 				= glGetUniformLocation(mBGProgram,   		"u_MvpMatrix");
		mBGAVertexUVHandler 					= glGetAttribLocation(mBGProgram,    		"a_VertexUV");
		mBGUTextureHandler					= glGetUniformLocation(mBGProgram,   		"u_Texture");

		mPyrDownProgram = OpenGlUtils.createProgram(readTextFileFromRawResource(mActivityContext, R.raw.pyrdown_vert), readTextFileFromRawResource(mActivityContext, R.raw.pyrdown_frag));
		mPyrDownAVertexPosHandler 			= glGetAttribLocation(mPyrDownProgram,    "a_VertexPos");
		mPyrDownUMvpMatrixHandler 			= glGetUniformLocation(mPyrDownProgram,   "u_MvpMatrix");
		mPyrDownAVertexUVHandler 			= glGetAttribLocation(mPyrDownProgram,    "a_VertexUV");
		mPyrDownUTextureHandler				= glGetUniformLocation(mPyrDownProgram,   "u_Texture");

		mSubtractProgram = OpenGlUtils.createProgram(readTextFileFromRawResource(mActivityContext, R.raw.background_subtract_vert), readTextFileFromRawResource(mActivityContext, R.raw.background_subtract_frag));
		mSubtractAVertexPosHandler 			= glGetAttribLocation(mSubtractProgram,    "a_VertexPos");
		mSubtractUMvpMatrixHandler 			= glGetUniformLocation(mSubtractProgram,   "u_MvpMatrix");
		mSubtractAVertexUVHandler 			= glGetAttribLocation(mSubtractProgram,    "a_VertexUV");
		mSubtractUTextureBGHandler			= glGetUniformLocation(mSubtractProgram,   "u_TextureBG");
		mSubtractUTextureHandler			= glGetUniformLocation(mSubtractProgram,   "u_Texture");
		mSubtractUThresholdHandler			= glGetUniformLocation(mSubtractProgram,   "u_Threshold");

		mSegmentProgram = OpenGlUtils.createProgram(readTextFileFromRawResource(mActivityContext, R.raw.background_segment_vert), readTextFileFromRawResource(mActivityContext, R.raw.background_segment_frag));
		mSegmentAVertexPosHandler 			= glGetAttribLocation(mSegmentProgram,     "a_VertexPos");
		mSegmentUMvpMatrixHandler 			= glGetUniformLocation(mSegmentProgram,    "u_MvpMatrix");
		mSegmentAVertexUVHandler 			= glGetAttribLocation(mSegmentProgram,     "a_VertexUV");
		mSegmentUTextureBGHandler			= glGetUniformLocation(mSegmentProgram,    "u_TextureBG");
		mSegmentUTextureHandler				= glGetUniformLocation(mSegmentProgram,    "u_Texture");

		mErodeProgram = OpenGlUtils.createProgram(readTextFileFromRawResource(mActivityContext, R.raw.erode_vert), readTextFileFromRawResource(mActivityContext, R.raw.erode_frag));
		mErodeAVertexPosHandler 				= glGetAttribLocation(mErodeProgram,    	 "a_VertexPos");
		mErodeUMvpMatrixHandler 				= glGetUniformLocation(mErodeProgram,      "u_MvpMatrix");
		mErodeAVertexUVHandler 				= glGetAttribLocation(mErodeProgram,    	 "a_VertexUV");
		mErodeUTextureHandler				= glGetUniformLocation(mErodeProgram,   	 "u_Texture");

		mDilateProgram = OpenGlUtils.createProgram(readTextFileFromRawResource(mActivityContext, R.raw.dilate_vert), readTextFileFromRawResource(mActivityContext, R.raw.dilate_frag));
		mDilateAVertexPosHandler 			= glGetAttribLocation(mDilateProgram,      "a_VertexPos");
		mDilateUMvpMatrixHandler 			= glGetUniformLocation(mDilateProgram,     "u_MvpMatrix");
		mDilateAVertexUVHandler 				= glGetAttribLocation(mDilateProgram,      "a_VertexUV");
		mDilateUTextureHandler				= glGetUniformLocation(mDilateProgram,     "u_Texture");

		mOldFrameBuffers = IntBuffer.allocate(1);
		mFrameBuffers = IntBuffer.allocate(4);
		mTextures = IntBuffer.allocate(4);
		mVBOBuffer = IntBuffer.allocate(2);
		glGenTextures(4, mTextures);
		GLES20.glGenFramebuffers(4, mFrameBuffers);
		Matrix.setIdentityM(mMVPMatrix, 0);
		Matrix.setIdentityM(mModelMatrix, 0);
		Matrix.setIdentityM(mViewMatrix, 0);
		Matrix.setIdentityM(mProjectMatrix, 0);
		glGenBuffers(2,mVBOBuffer);

		mFloatBuffer						= ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder()).asFloatBuffer();
		mFloatBuffer.clear();
		mFloatBuffer.put(_textureVUV);
		mFloatBuffer.position(0);
		glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(0));
		glBufferData(GL_ARRAY_BUFFER, mFloatBuffer.capacity()*4, mFloatBuffer, GL_STATIC_DRAW );
		glBindBuffer(GL_ARRAY_BUFFER, 0);

		FloatBuffer mFloatBufferBG						= ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder()).asFloatBuffer();
		mFloatBufferBG.clear();
		mFloatBufferBG.put(_textureBGVUV);
		mFloatBufferBG.position(0);
		glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(1));
		glBufferData(GL_ARRAY_BUFFER, mFloatBufferBG.capacity()*4, mFloatBufferBG, GL_STATIC_DRAW );
		glBindBuffer(GL_ARRAY_BUFFER, 0);

		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(0));
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mWidth, mHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(0));
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTextures.get(0), 0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(1));
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mWidth/2, mHeight/2, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(1));
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTextures.get(1), 0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(2));
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mWidth/2, mHeight/2, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(2));
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTextures.get(2), 0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(3));
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mWidth/2, mHeight/2, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(3));
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTextures.get(3), 0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
	}
	public void deInit(){
		//has opened alpha blend
		glDeleteBuffers(2, mVBOBuffer);
		glDeleteTextures(4, mTextures);
		glDeleteProgram(mSubtractProgram);
		glDeleteProgram(mBGProgram);
		glDeleteProgram(mPyrDownProgram);
		glDeleteProgram(mSegmentProgram);
		glDeleteProgram(mDilateProgram);
		glDeleteProgram(mErodeProgram);
		glDeleteBuffers(4, mFrameBuffers);
		//recover OPENGL STATE
	}
	private int count = 0;
	private int step = 5;
	private ByteBuffer mSaveByteBuffer;
	public void draw(YYMediaSample sample, int width, int height){
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
		if (mBufferBG == null){
			GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, mOldFrameBuffers);
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(0));
			Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
			Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
			//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
			Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
			glViewport(0, 0, width, height);
			glUseProgram(mBGProgram);
			glUniform1i(mBGUTextureHandler, 0);
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(GL_TEXTURE_2D, sample.mTextureId);
			//Matrix.setIdentityM(mCurMatrix, 0);
			glUniformMatrix4fv(mBGUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
			glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(1));
			glEnableVertexAttribArray(mBGAVertexPosHandler);
			glVertexAttribPointer(mBGAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
			glEnableVertexAttribArray(mBGAVertexUVHandler);
			glVertexAttribPointer(mBGAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
			glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
			GLES20.glDisableVertexAttribArray(mBGAVertexPosHandler);
			GLES20.glDisableVertexAttribArray(mBGAVertexUVHandler);
			GLES20.glBindTexture(GL_TEXTURE_2D, 0);
			GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
			glUseProgram(0);
			mBufferBG = ByteBuffer.allocate(width * height*4);
			GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mBufferBG);
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOldFrameBuffers.get(0));

			//OpenGlUtils.saveFrame("/sdcard/testppp55.png", mBufferBG, width, height);
		}else {
			if (mSaveByteBuffer ==  null){
				mSaveByteBuffer = ByteBuffer.allocate(width*height*4);
			}
			OpenGlUtils.checkGlError("====1");
			GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, mOldFrameBuffers);
			//pyrdown width/(2,4,8,16)  height/(2,4,8,16) gsfilter
			{
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(1));
				Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
				Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
				//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				glViewport(0, 0, width/2, height/2);
				glUseProgram(mPyrDownProgram);
				glUniform1i(mPyrDownUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(GL_TEXTURE_2D, mTextures.get(0));
				//Matrix.setIdentityM(mCurMatrix, 0);
				glUniformMatrix4fv(mPyrDownUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(1));
				glEnableVertexAttribArray(mPyrDownAVertexPosHandler);
				glVertexAttribPointer(mPyrDownAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				glEnableVertexAttribArray(mPyrDownAVertexUVHandler);
				glVertexAttribPointer(mPyrDownAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				GLES20.glDisableVertexAttribArray(mPyrDownAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mPyrDownAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);
				//GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mSaveByteBuffer);
				//OpenGlUtils.saveFrame("/sdcard/testppp2.png", mSaveByteBuffer, width, height);

				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(2));
				Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
				Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
				//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				glViewport(0, 0, width/2, height/2);
				glUseProgram(mPyrDownProgram);
				glUniform1i(mPyrDownUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(GL_TEXTURE_2D, sample.mTextureId);
				//Matrix.setIdentityM(mCurMatrix, 0);
				glUniformMatrix4fv(mPyrDownUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(0));
				glEnableVertexAttribArray(mPyrDownAVertexPosHandler);
				glVertexAttribPointer(mPyrDownAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				glEnableVertexAttribArray(mPyrDownAVertexUVHandler);
				glVertexAttribPointer(mPyrDownAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				GLES20.glDisableVertexAttribArray(mPyrDownAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mPyrDownAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);
				//GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mSaveByteBuffer);
				//OpenGlUtils.saveFrame("/sdcard/testppp1.png", mSaveByteBuffer, width, height);
			}
			OpenGlUtils.checkGlError("====2");
			//subtract
			{
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(3));
				Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
				Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
				//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				glViewport(0, 0, width/2, height/2);
				OpenGlUtils.checkGlError("////1");
				glUseProgram(mSubtractProgram);
				OpenGlUtils.checkGlError("////3");
				glUniform1i(mSubtractUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(sample.mTextureTarget, mTextures.get(2));
				glUniform1i(mSubtractUTextureBGHandler, 1);
				glActiveTexture(GL_TEXTURE1);
				glBindTexture(GL_TEXTURE_2D, mTextures.get(1));
				OpenGlUtils.checkGlError("////4");
				glUniform1f(mSubtractUThresholdHandler, mThreshold);
				OpenGlUtils.checkGlError("////5");
				//Matrix.setIdentityM(mCurMatrix, 0);
				glUniformMatrix4fv(mSubtractUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				OpenGlUtils.checkGlError("////6");
				glBindBuffer(GL_ARRAY_BUFFER,mVBOBuffer.get(0));
				glEnableVertexAttribArray(mSubtractAVertexPosHandler);
				glVertexAttribPointer(mSubtractAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				OpenGlUtils.checkGlError("////7");
				glEnableVertexAttribArray(mSubtractAVertexUVHandler);
				glVertexAttribPointer(mSubtractAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				OpenGlUtils.checkGlError("////8");
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				OpenGlUtils.checkGlError("////9");
				GLES20.glDisableVertexAttribArray(mSubtractAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mSubtractAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);

				//GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mSaveByteBuffer);
				//OpenGlUtils.saveFrame("/sdcard/testppp.png", mSaveByteBuffer, width, height);
			}
			//erode
			{
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(2));
				Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
				Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
				//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				OpenGlUtils.checkGlError("jjjj1");
				glViewport(0, 0, width/2, height/2);
				glUseProgram(mErodeProgram);
				glUniform1i(mErodeUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(GL_TEXTURE_2D, mTextures.get(3));
				//Matrix.setIdentityM(mCurMatrix, 0);
				OpenGlUtils.checkGlError("jjjj2");
				glUniformMatrix4fv(mErodeUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(1));
				glEnableVertexAttribArray(mErodeAVertexPosHandler);
				glVertexAttribPointer(mErodeAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				glEnableVertexAttribArray(mErodeAVertexUVHandler);
				glVertexAttribPointer(mErodeAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				OpenGlUtils.checkGlError("jjjj3");
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				OpenGlUtils.checkGlError("jjjj4");
				GLES20.glDisableVertexAttribArray(mErodeAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mErodeAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);
			}
			//dilate
			{
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(1));
				Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
				Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
				//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				OpenGlUtils.checkGlError("jjjj1");
				glViewport(0, 0, width/2, height/2);
				glUseProgram(mDilateProgram);
				glUniform1i(mDilateUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(GL_TEXTURE_2D, mTextures.get(2));
				//Matrix.setIdentityM(mCurMatrix, 0);
				OpenGlUtils.checkGlError("jjjj2");
				glUniformMatrix4fv(mDilateUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(1));
				glEnableVertexAttribArray(mDilateAVertexPosHandler);
				glVertexAttribPointer(mDilateAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				glEnableVertexAttribArray(mDilateAVertexUVHandler);
				glVertexAttribPointer(mDilateAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				OpenGlUtils.checkGlError("jjjj3");
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				OpenGlUtils.checkGlError("jjjj4");
				GLES20.glDisableVertexAttribArray(mDilateAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mDilateAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);
			}
			//erode
			{
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(2));
				Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
				Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
				//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				OpenGlUtils.checkGlError("jjjj1");
				glViewport(0, 0, width/2, height/2);
				glUseProgram(mErodeProgram);
				glUniform1i(mErodeUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(GL_TEXTURE_2D, mTextures.get(1));
				//Matrix.setIdentityM(mCurMatrix, 0);
				OpenGlUtils.checkGlError("jjjj2");
				glUniformMatrix4fv(mErodeUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(1));
				glEnableVertexAttribArray(mErodeAVertexPosHandler);
				glVertexAttribPointer(mErodeAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				glEnableVertexAttribArray(mErodeAVertexUVHandler);
				glVertexAttribPointer(mErodeAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				OpenGlUtils.checkGlError("jjjj3");
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				OpenGlUtils.checkGlError("jjjj4");
				GLES20.glDisableVertexAttribArray(mErodeAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mErodeAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);
			}
			//dilate
			{
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(1));
				Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
				Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
				//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				OpenGlUtils.checkGlError("jjjj1");
				glViewport(0, 0, width/2, height/2);
				glUseProgram(mDilateProgram);
				glUniform1i(mDilateUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(GL_TEXTURE_2D, mTextures.get(2));
				//Matrix.setIdentityM(mCurMatrix, 0);
				OpenGlUtils.checkGlError("jjjj2");
				glUniformMatrix4fv(mDilateUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(1));
				glEnableVertexAttribArray(mDilateAVertexPosHandler);
				glVertexAttribPointer(mDilateAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				glEnableVertexAttribArray(mDilateAVertexUVHandler);
				glVertexAttribPointer(mDilateAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				OpenGlUtils.checkGlError("jjjj3");
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				OpenGlUtils.checkGlError("jjjj4");
				GLES20.glDisableVertexAttribArray(mDilateAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mDilateAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);
			}

			//dilate
			{
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(2));
				Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
				Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
				//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				OpenGlUtils.checkGlError("jjjj1");
				glViewport(0, 0, width/2, height/2);
				glUseProgram(mDilateProgram);
				glUniform1i(mDilateUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(GL_TEXTURE_2D, mTextures.get(1));
				//Matrix.setIdentityM(mCurMatrix, 0);
				OpenGlUtils.checkGlError("jjjj2");
				glUniformMatrix4fv(mDilateUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(1));
				glEnableVertexAttribArray(mDilateAVertexPosHandler);
				glVertexAttribPointer(mDilateAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				glEnableVertexAttribArray(mDilateAVertexUVHandler);
				glVertexAttribPointer(mDilateAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				OpenGlUtils.checkGlError("jjjj3");
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				OpenGlUtils.checkGlError("jjjj4");
				GLES20.glDisableVertexAttribArray(mDilateAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mDilateAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);
			}

			//dilate
			{
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers.get(1));
				Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
				Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
				//Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				OpenGlUtils.checkGlError("jjjj1");
				glViewport(0, 0, width/2, height/2);
				glUseProgram(mDilateProgram);
				glUniform1i(mDilateUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(GL_TEXTURE_2D, mTextures.get(2));
				//Matrix.setIdentityM(mCurMatrix, 0);
				OpenGlUtils.checkGlError("jjjj2");
				glUniformMatrix4fv(mDilateUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(1));
				glEnableVertexAttribArray(mDilateAVertexPosHandler);
				glVertexAttribPointer(mDilateAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				glEnableVertexAttribArray(mDilateAVertexUVHandler);
				glVertexAttribPointer(mDilateAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				OpenGlUtils.checkGlError("jjjj3");
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				OpenGlUtils.checkGlError("jjjj4");
				GLES20.glDisableVertexAttribArray(mDilateAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mDilateAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);
			}

			//dilate
			OpenGlUtils.checkGlError("====3");
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOldFrameBuffers.get(0));
			Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 1, 0, 0, -0.1f, 0, 1, 0);
			Matrix.perspectiveM(mProjectMatrix, 0, 90, (float) width / (float) height, 1.0f, 1000);
			//bg render
			{
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				glViewport(0, 0, width, height);
				glUseProgram(mBGProgram);
				glUniform1i(mBGUTextureHandler, 0);
				glActiveTexture(GL_TEXTURE0);
				glBindTexture(GL_TEXTURE_2D, mTextures.get(0));
				Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix, 0);
				glUniformMatrix4fv(mBGUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
				glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(0));
				glEnableVertexAttribArray(mBGAVertexPosHandler);
				glVertexAttribPointer(mBGAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
				glEnableVertexAttribArray(mBGAVertexUVHandler);
				glVertexAttribPointer(mBGAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
				GLES20.glDisableVertexAttribArray(mBGAVertexPosHandler);
				GLES20.glDisableVertexAttribArray(mBGAVertexUVHandler);
				GLES20.glBindTexture(GL_TEXTURE_2D, 0);
				GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
				glUseProgram(0);
			}
			if (count==1000){
				step = -5;
			}
			if (count == 0){
				step = 5;
			}
			count+=step;
			Matrix.setIdentityM(mModelMatrix, 0);
			Matrix.translateM(mModelMatrix, 0, 0,count*0.001f,0);
			Matrix.setIdentityM(mCurMatrix, 0);
			Matrix.multiplyMM(mCurMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
			Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mCurMatrix, 0);
			
			glViewport(0, 0, width, height);
			glUseProgram(mSegmentProgram);
			glUniform1i(mSegmentUTextureHandler, 0);
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(sample.mTextureTarget, sample.mTextureId);
			glUniform1i(mSegmentUTextureBGHandler, 1);
			glActiveTexture(GL_TEXTURE1);
			glBindTexture(GL_TEXTURE_2D, mTextures.get(2));
			glUniformMatrix4fv(mSegmentUMvpMatrixHandler, 1, false, mMVPMatrix, 0);
			glBindBuffer(GL_ARRAY_BUFFER, mVBOBuffer.get(0));
			glEnableVertexAttribArray(mSegmentAVertexPosHandler);
			glVertexAttribPointer(mSegmentAVertexPosHandler, 3, GL_FLOAT, false, 20, 0);
			glEnableVertexAttribArray(mSegmentAVertexUVHandler);
			glVertexAttribPointer(mSegmentAVertexUVHandler, 2, GL_FLOAT, false, 20, 12);
			glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
			GLES20.glDisableVertexAttribArray(mSegmentAVertexPosHandler);
			GLES20.glDisableVertexAttribArray(mSegmentAVertexUVHandler);
			GLES20.glBindTexture(GL_TEXTURE_2D, 0);
			GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0);
			glUseProgram(0);
			OpenGlUtils.checkGlError("====");
		}
	}
	public static String readTextFileFromRawResource(final Context context, final int resourceId) {
		final InputStream inputStream = context.getResources().openRawResource(resourceId);
		final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
		final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
		String nextLine;
		final StringBuilder body = new StringBuilder();
		try {
			while ((nextLine = bufferedReader.readLine()) != null)
			{
				body.append(nextLine);
				body.append('\n');
			}
		} catch (IOException e) {
			return null;
		}
		return body.toString();
	}
}
