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
import android.opengl.GLES20;


import com.ycloud.utils.YYLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class GLShaderProgram {
	static final String Tag = "GLShaderProgram";
	private int mProgram = -1;
	private int mShaderVertex = -1;
	private int mShaderFragment = -1;
	private String vertexSource;
	private String fragmentSource;
	
	//hashmap for storing uniform/attribute handles
	private final HashMap<String, Integer> mShaderHandleMap = new HashMap<String, Integer>();
	
	
	public GLShaderProgram() {
	}
	
	public void setProgram(int vertexShader, int fragmentShader, Context context) {
		vertexSource = loadRawString(vertexShader, context);
		fragmentSource = loadRawString(fragmentShader, context);

		setProgram(vertexSource, fragmentSource);
	}

	public void setProgram(String vertexSource, String fragmentSource)
	{
		mShaderVertex = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
		mShaderFragment = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

		int program = GLES20.glCreateProgram();
		if(program != 0){
			GLES20.glAttachShader(program, mShaderVertex);
			GLES20.glAttachShader(program, mShaderFragment);
			GLES20.glLinkProgram(program);
			int[] linkStatus = new int[1];
			GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
			if(linkStatus[0] != GLES20.GL_TRUE){
				String error = GLES20.glGetProgramInfoLog(program);
				destory();
				YYLog.error(Tag, "Link shader error: " + error);
				return;
			}
		}

		mProgram = program;
		mShaderHandleMap.clear();
	}
	
	public void useProgram(){
		GLES20.glUseProgram(mProgram);
	}
	
	public void destory(){
        if (mProgram != -1){
            GLES20.glDeleteShader(mShaderVertex);
            GLES20.glDeleteShader(mShaderFragment);
            GLES20.glDeleteProgram(mProgram);
            mProgram = mShaderVertex = mShaderFragment = -1;
        }
	}
	
	public int programHandle(){
		return mProgram;
	}
	
	public int getHandle(String name){
		if(mShaderHandleMap.containsKey(name)){
			return mShaderHandleMap.get(name);
		}
		
		int handle = GLES20.glGetAttribLocation(mProgram, name);
		if(handle == -1){
			handle = GLES20.glGetUniformLocation(mProgram, name);
		}
		if(handle == -1){
			YYLog.error("GLSL shader", "Could not get attrib location for " + name);
		}else{
			mShaderHandleMap.put(name, handle);
		}
		
		return handle;
	}
	
	public int[] getHandles(String... names){
		int[] res = new int[names.length];
		for(int i = 0; i < names.length; ++i){
			res[i] = getHandle(names[i]);
		}
		
		return res;
	}

	private int loadShader(int shaderType, String source){
		int shader = GLES20.glCreateShader(shaderType);
		if(shader != 0){
			GLES20.glShaderSource(shader, source);
			GLES20.glCompileShader(shader);
			int[] compiled = new int[1];
			GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
			
			if(compiled[0] == 0){
				String error = GLES20.glGetShaderInfoLog(shader);
				GLES20.glDeleteShader(shader);
				YYLog.error(Tag, "Compile shader error: " + error);
				return -1;
			}
		}
		
		return shader;
	}
	
	private String loadRawString(int rawId, Context context){
        InputStream is = context.getResources().openRawResource(rawId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try{
            byte[] buf = new byte[1024];
            int len;
            while((len = is.read(buf))!= -1){
                baos.write(buf, 0, len);
            }
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
		return baos.toString();
	}

	public void setUniform1i(
			String name,
			int x) {

		int location = getHandle(name);
		GLES20.glUniform1i(location, x);
	}

	public void setUniform2i(
			String name,
			int x,
			int y) {

		int location = getHandle(name);
		GLES20.glUniform2i(location, x, y);
	}

	public void setUniform1f(
			String name,
			float x) {

		int location = getHandle(name);
		GLES20.glUniform1f(location, x);
	}

	public void setUniform2f(
			String name,
			float x,
			float y) {

		int location = getHandle(name);
		GLES20.glUniform2f(location, x, y);
	}

	public void setUniformMatrix4fv(
			String name,
			int count,
			boolean transpose,
			float[] value,
			int offset) {

		int location = getHandle(name);
		GLES20.glUniformMatrix4fv(location, count, transpose, value, offset);
	}

	public void setVertexAttribPointer(
			String name,
			int size,
			int type,
			boolean normalized,
			int stride,
			java.nio.Buffer ptr) {

		int index = getHandle(name);
		GLES20.glEnableVertexAttribArray(index);
		GLES20.glVertexAttribPointer(index, size, type, normalized, stride, ptr);
	}

	public void disableVertexAttribPointer(String name) {
		int index = getHandle(name);
		GLES20.glDisableVertexAttribArray(index);
	}

	public void setUniformTexture(
			String name,
			int x,
			int textureID,
            int target) {

		int location = getHandle(name);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + x);
		GLES20.glBindTexture(target, textureID);
		GLES20.glUniform1i(location, x);
	}
}
