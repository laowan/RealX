/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.ycloud.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import android.os.Environment;
import android.util.Log;

import static com.ycloud.common.Constant.SDK_NAME_PREFIX;

/**
 * Utilities to manage executors.
 */
public class ExecutorUtils {

    private static final String TAG = "ExecutorUtils";
    private static final String LOG_PATH=Environment.getExternalStorageDirectory().getPath()+File.separator+"yysdklog";
    private static Map<String, ScheduledExecutorService> sScheduledExecutorServiceMap =new HashMap<String, ScheduledExecutorService>();

	private static final int kMinExecutorThread = 0;
	private static final int kMaxExecutorThread = 8;

	private static AtomicReference<Executor> mBaseSDKExecutor = new AtomicReference<Executor>(null);
	private static AtomicReference<Executor> myExectuor = new AtomicReference<Executor>(null);

    private static ScheduledExecutorService newExecutorService(final String name) {
        return Executors.newSingleThreadScheduledExecutor(new ExecutorFactory(name));
    }

    public synchronized static void setBaseSDKExecutor(Executor executor)
	{
		YYLog.info("[ymrsdk]", "setBaseSDKExecutor");
		mBaseSDKExecutor = new AtomicReference<Executor>(executor);
	}

    private static class ExecutorFactory implements ThreadFactory {
        private final String mName;

        private ExecutorFactory(final String name) {
            mName = name;
        }

        @Override
		public Thread newThread(final Runnable runnable) {
			Thread thread = new Thread(runnable, SDK_NAME_PREFIX +mName);
			thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
				@Override
				public void uncaughtException(Thread thread, Throwable ex) {
					String name = mName + "-" + runnable.getClass().getSimpleName() ;
					handleException(name,ex);
					throw new RuntimeException(ex);
				}
			});
			return thread;
		}
    }
    
    public static void handleException(String name,Throwable ex) {
		File dir = new File(LOG_PATH);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		String filePath = LOG_PATH + File.separator + System.currentTimeMillis();
		File file = new File(filePath);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
			String exceptionString = name + ": " + ex.getMessage();
			fos.write(exceptionString.getBytes());

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				fos = null;
			}
		}

		Log.e(TAG, ex.getMessage());
	}
    
    /**
     * @param name Executor's name.
     * @return scheduled executor service used to run background tasks
     */
    private static ScheduledExecutorService getBackgroundScheduleExecutor(final String name) {
		ScheduledExecutorService scheduledExecutorService = sScheduledExecutorServiceMap.get(name);
    	if(scheduledExecutorService == null ){
			scheduledExecutorService = newExecutorService(name);
    		sScheduledExecutorServiceMap.put(name, scheduledExecutorService);
    	}
    	return scheduledExecutorService;
    }

	static class DefaultThreadFactory implements ThreadFactory {
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;

		DefaultThreadFactory() {
			//SecurityManager s = System.getSecurityManager();
			//group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
			group = Thread.currentThread().getThreadGroup();
			namePrefix = "ymrsdk_pool-t";
		}

		public Thread newThread(Runnable r) {
			Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
			if (t.isDaemon())
				t.setDaemon(false);
			if (t.getPriority() != Thread.NORM_PRIORITY)
				t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}

//    private static int debug = 0;
//	private static Executor mDebugExecutor = null;
	public static Executor getBackgroundExecutor(final String name) {
//		if(debug++ <=0) {
//			mDebugExecutor = Executors.newFixedThreadPool(4);
//			setBaseSDKExecutor(mDebugExecutor);
//		}
		Executor executor = mBaseSDKExecutor.get();
		if(executor != null) {
			YYLog.info("[ymrsdk]", "ExecutorUtil basesdk getBackgroundExecutor:"+name);
			return executor;
		}
		else {
			if(myExectuor.get() == null) {
				synchronized (ExecutorUtils.class) {
					if(myExectuor.get() == null) {
						myExectuor.set(new ThreadPoolExecutor(kMinExecutorThread, kMaxExecutorThread, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
								new DefaultThreadFactory()/*, new ThreadPoolExecutor.DiscardPolicy()*/));    //throw the exception
					}
				}
			}

			YYLog.info("[ymrsdk]", "myExecutorUtil getBackgroundExecutor:"+name);
			return myExectuor.get();
		}
	}

    public static void killTask(final String name) {
        final ScheduledExecutorService executorService = getBackgroundScheduleExecutor(name);
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.wtf(TAG, "Failed to shut down: " + name);
        }
        
        sScheduledExecutorServiceMap.remove(name);
    }
}
