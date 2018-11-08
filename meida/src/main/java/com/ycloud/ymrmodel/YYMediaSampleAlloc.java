package com.ycloud.ymrmodel;

import android.annotation.SuppressLint;


import com.ycloud.utils.YYLog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@SuppressLint("NewApi")
public class YYMediaSampleAlloc {
	
	private static volatile YYMediaSampleAlloc s_instance = null;
	private static Object mLock = new Object();
	AtomicLong		mIndex = new AtomicLong(30);

	public static class SampleStats
	{
		protected int mAllocCnt = 0;
		protected int mFreeCnt = 0;
		protected int mHoldCnt = 0;
		protected int mPoolAllocCt = 0;
		protected int mPoolFreeCnt = 0;
	}

	HashMap<String, SampleStats>  mStackInfo2SampleCnt = new HashMap<>(0);
	
	static {
		YYMediaSampleAlloc.instance();
	}
	
	public static YYMediaSampleAlloc instance() {
		if(s_instance == null) {
			synchronized (mLock) {
				if(s_instance == null) {
					s_instance = new YYMediaSampleAlloc();
				}
			}
		}
		return s_instance;
	}

	private ConcurrentLinkedQueue<YYMediaSample> mFreeDeque = new ConcurrentLinkedQueue<YYMediaSample>();

	private YYMediaSampleAlloc() {
		init(30);
	}

	private void addStackInfoSample(String stackTrackInfo, boolean fromPool) {
		if(stackTrackInfo == null || stackTrackInfo.isEmpty()) {
			YYLog.error(this, "YYMediaSampleAlloc.addStackInfoSample, stackTrackInfo is invalid");
			return;
		}

		synchronized (this) {
			SampleStats stat = mStackInfo2SampleCnt.get(stackTrackInfo);
			if (stat == null) {
				stat = new SampleStats();
				mStackInfo2SampleCnt.put(stackTrackInfo, stat);
			}

			stat.mAllocCnt += 1;
			stat.mHoldCnt += 1;
			if(fromPool) {
				stat.mPoolAllocCt += 1;
			}
		}
	}

	private void removeSampleStackTraceStats(String stackTrackInfo, boolean bAllocFromPool)
	{
		if(stackTrackInfo == null || stackTrackInfo.isEmpty()) {
			YYLog.error(this, "YYMediaSampleAlloc.removeSampleStackTraceStats, stackTrackInfo is invalid");
			return;
		}

		synchronized (this) {
			SampleStats stat = mStackInfo2SampleCnt.get(stackTrackInfo);
			if (stat != null) {
				stat.mHoldCnt -= 1;
				stat.mFreeCnt += 1;

				if(bAllocFromPool) {
					stat.mPoolFreeCnt += 1;
				}
			} else {
				YYLog.error(this, "YYMediaSampleAlloc.removeSampleStackTraceStats, but no stats found in map, stackTrackInfo:"+stackTrackInfo);
			}
		}
	}

	private void dumpSampleStackInfo() {
		synchronized (this) {
			Iterator<Map.Entry<String, SampleStats>> it = mStackInfo2SampleCnt.entrySet().iterator();
			while(it.hasNext()) {
				Map.Entry<String, SampleStats> entry = it.next();
				YYLog.info(this, "YYMediaSampleAlloc.dumpSampleStackInfo: " + entry.getKey()+" -> "
						+ " allocCnt:" +entry.getValue().mAllocCnt
						+ " freeCnt:" + entry.getValue().mFreeCnt
						+ " holdCnt:" + entry.getValue().mHoldCnt
						+ " allocFromPoolCnt:" + entry.getValue().mPoolAllocCt
						+ " freePoolCnt:" + entry.getValue().mPoolFreeCnt
						+ " freeQueueSize:"+mFreeDeque.size()
				 		+ " stackMap.size="+mStackInfo2SampleCnt.size());
			}
		}
	}

	public  YYMediaSample	alloc() {
		//TODO. use debug
		//YYLog.debug(this, "alloc, size ="+mFreeDeque.size());
		YYMediaSample sample = null;
		try {
			sample = mFreeDeque.poll();
			if(sample != null) {
				sample.mBAllocFromPool = true;
			}
		} catch (NoSuchElementException e) {
			YYLog.info(this, "fail allocate a sample buffer, no buffer in pool, e="+e.toString());
			sample = null;
		}

		if (sample == null) {
			sample = newMediaSample();
			sample.mBAllocFromPool = false;

			if(mIndex.addAndGet(1) % 30 == 0) {
				YYLog.info(this, "alloc, new sample=" + mFreeDeque.size() + " allocCnt=" + mIndex.addAndGet(1));
			}
		}

		/* diagnosis
		synchronized(this) {
				StackTraceElement[] elements = Thread.currentThread().getStackTrace();
				if (elements != null && elements.length > 3) {
					sample.mStackTraceInfo = elements[3].getClassName() + " " + elements[3].getMethodName();
					addStackInfoSample(sample.mStackTraceInfo, sample.mBAllocFromPool);
				}

		}
			if(mIndex.get() %20 == 0) {
			dumpSampleStackInfo();
		}
		*/
		sample.addRef();
		return sample;
	}
	
	public  void free(YYMediaSample sample) {
		//YYLog.debug(this, "free");
		/*diagnosis
		if(sample.mStackTraceInfo != null) {
			removeSampleStackTraceStats(sample.mStackTraceInfo, sample.mBAllocFromPool);
		}
		*/

		resetSample(sample);
		if(!mFreeDeque.add(sample))
		{
			YYLog.error(this, "YYMediaSampleAlloc.free failed");
		}
		int free_size = mFreeDeque.size();
		if(free_size > 200) {
			YYLog.error(this, "memory leak!!!!, free_size="+free_size);
		}
	}
	
	private void resetSample(YYMediaSample sample) {
		sample.reset();
	}
	
	private YYMediaSample newMediaSample() {
		YYMediaSample sample = new YYMediaSample();
		resetSample(sample);
		return sample;
	}
	
	private void init(int capacity) {
		for(int i = 0; i < capacity; i++) {
			YYMediaSample sample = newMediaSample();
			mFreeDeque.add(sample);
		}
	}
	
}
