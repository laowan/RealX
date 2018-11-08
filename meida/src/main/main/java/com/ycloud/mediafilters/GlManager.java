package com.ycloud.mediafilters;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.ycloud.gles.EglCore;
import com.ycloud.gles.EglFactory;
import com.ycloud.gles.IEglCore;
import com.ycloud.gles.IEglSurfaceBase;
import com.ycloud.utils.OpenGlUtils;
import com.ycloud.utils.YYLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

/** 
 * TODO: thread safty...
 * Looper Thread.
 * @author Administrator
 *
 */
@SuppressLint("NewApi")
public class GlManager implements Runnable
{
    private static final String TAG = "GlManager";
	static {
		try {
			System.loadLibrary("ycmediayuv");
		} catch (UnsatisfiedLinkError e) {
			YYLog.error(TAG, "LoadLibrary failed, UnsatisfiedLinkError " + e.getMessage());
		}
	}

	public Thread 			mLooperThread;
	public GlHandler		mGlHandler = null; //handler in glthread, so create in runnable function.
	
    private IEglCore mEglCore; //display, context initialized..
    private IEglSurfaceBase mEnvSurface;
    
    private AtomicBoolean 	mStartLock = new AtomicBoolean(false);
    /**TODO 不用于任何渲染，应该设置成任何值都可以*/
    private int 	mDefaultWidth = 10;
    private int 	mDefaultHeight = 10;

	private IMediaSession   mMediaSession = null;

	private int mBitFlag = EglCore.FLAG_RECORDABLE;

	private ArrayList<AbstractYYMediaFilter> mFilterArray = new ArrayList<>();
	public GlManager() {
		//默认不创建opengl资源. 
		long beginMs = System.currentTimeMillis();
		mLooperThread = new Thread(this, SDK_NAME_PREFIX +"GlManager");
		mLooperThread.setPriority(Thread.NORM_PRIORITY+4);
		mLooperThread.start();
		//确保post,quit函数有效.
		//大概5-6ms.
		YYLog.info(this, "[procedure] GlManager constructor cost:" + (System.currentTimeMillis()-beginMs));
	}

	public void waitUntilRun() {
		synchronized(mStartLock) {
			if(!mStartLock.get()) {
				try {
					mStartLock.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public Looper getLooper() { return mGlHandler.getLooper();}

    public long getThreadId() {
    	return mLooperThread.getId();
    }
    
    public Handler getHandler() {
    	return mGlHandler;
    }
    
    public IEglCore getEglCore() {
    	return mEglCore;
    }
    
    public void resetContext() {
    	 mEglCore.makeCurrent(mEnvSurface);
    }

    private void setBitFlag(int bitFlag) {
		mBitFlag = bitFlag;
	}
	
	private void InitEGL() {
        mEglCore = EglFactory.createEGL(null, mBitFlag);
        //每个线程都需要调用一次makeCurrent之后, gl和egl等函数地址数组才能够完成装配， 上层才能使用java
        //层的egl/opengl等函数.	
        
        mEnvSurface = mEglCore.createSurfaceBase();
		mEnvSurface.createOffscreenSurface(mDefaultWidth, mDefaultHeight);
        mEglCore.makeCurrent(mEnvSurface);
        long tid = Thread.currentThread().getId();
        YYLog.info(this, "[procedure] Texture created thread id:"+tid);
	}
	
	private void deInitEGL() {
        if (mEnvSurface != null) {
            mEglCore.makeNothingCurrent();
			mEnvSurface.releaseEglSurface();
            //mEglCore.releaseSurface(mEnvSurface);
            mEnvSurface = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
	}
	
	//TODO. double-check for thread safty.
    public void quit() {
		YYLog.info(this, "[tracer] quit GlManager thread.");
		mGlHandler.post(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				Looper loop = Looper.myLooper();
				if (null != loop) {
					loop.quitSafely();
				}
			}
		});
    }

	public void registerFilter(AbstractYYMediaFilter filter){
		YYLog.info(this,"registerFilter");
		synchronized (mFilterArray){
			mFilterArray.add(filter);
		}
	}

	public void setMediaSession(IMediaSession session) {
		mMediaSession = session;
	}

	private void releaseFilters(){
		YYLog.info(this,"releaseFilters");
		synchronized (mFilterArray){
			for (AbstractYYMediaFilter filter : mFilterArray){
				if (filter != null){
					filter.deInit();
				}
			}
			mFilterArray.clear();
		}
	}

	public void onStart() {
		//
	}

	public void onStop() {

	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		YYLog.info(this, "[procedure] glManager thread begin!!!!!");
	      try {
	            Looper.prepare();
	            mGlHandler = new GlHandler(this);
	            synchronized(mStartLock) {
	            	mStartLock.set(true);
	            	mStartLock.notifyAll();
	            }
	            InitEGL();
	            onStart();
	            Looper.loop();
	        }
	        catch (Throwable t) {
	            t.printStackTrace();
				YYLog.error(this, "[exception] exception occur, "+t.toString());

				//EGL初始化出现异常的情况下，将bitFlag设置为0，能解决部分机型不兼容的问题
				setBitFlag(0);
				EglFactory.setForceUseEgl10(true);
				InitEGL();
				Looper.loop();
	        }
	        finally {
			  releaseFilters();
              try {
                  //TODO.异常情况下, 其它子filter在此线程申请的opengl资源如何办??
                  deInitEGL();
              } catch(Throwable t) {
				  YYLog.error(this, "[exception]deInitEGL exception occur, "+t.toString());
              }
              onStop();

			  if(mMediaSession != null) {
				  mMediaSession.glMgrCleanup();
				  mMediaSession = null;
			  }
	        }

		YYLog.info("[ymrsdk]", "[procedure] glManager thread exit!!!!!");
	}
	
	public boolean checkSameThread() {
		return (Thread.currentThread().getId() == this.getThreadId());
	}
	
	public boolean post(Runnable task) {
		boolean ret = false;
		try {
			ret = mGlHandler.post(task);
			//YYLog.debug(this, " PostRunnable ret:" + ret);
		} catch (Throwable t) {
			YYLog.error(this, "[exception] GlManager PostRunnable exeception:" + t.toString());
		}
		return ret;
	}

	private static class GlHandler extends Handler {
		private WeakReference<GlManager> mWeakGLManager;

		public GlHandler(GlManager encoder) {
			mWeakGLManager = new WeakReference<GlManager>(encoder);
		}

		@Override  // runs on encoder thread
		public void handleMessage(Message inputMessage) {
			int what = inputMessage.what;
			Object obj = inputMessage.obj;

			GlManager encoder = mWeakGLManager.get();
			if (encoder == null) {
				YYLog.warn(TAG, "GLHandler.handleMessage: GlManager is null");
				return;
			}

			switch (what) {
				default:
//					throw new RuntimeException("Unhandled msg what=" + what);
			}
		}
	}
}
