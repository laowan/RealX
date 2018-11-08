//
// Created by Administrator on 2016/9/13.
//

#ifndef TRUNK_COMMONUTILS_H
#define TRUNK_COMMONUTILS_H

#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include "IntTypes.h"
#include <string>


template <typename T>
inline T MaxValue(T v1, T v2)
{
	return v1 > v2 ? v1 : v2;
};

template <typename T>
inline T MinValue(T v1, T v2)
{
	return v1 > v2 ? v2 : v1;
};

#define SafeDelete(ptr)     do { delete (ptr); (ptr) = NULL; } while(0)

#define IsFlagSet(e, f)     (((e) & (f)) != 0)
#define SetFlag(e, f)       do { (e) |= (f); } while(0)
#define ClearFlag(e, f)     do { (e) &= ~(f); } while(0)

#define FilterFlag(e, m)    ((e) & (m))
class CValueAvgStat
{
public:
	CValueAvgStat();
	~CValueAvgStat();

	void init(int numAvg);
	void AddValue(int value);
	void GetStat(int& avg, int& minV, int& maxV);

private:
	int m_min;
	int m_max;
	int m_idx;
	int m_numAvg;
	int m_Values[256];
	long long m_totalValue;
};

class MediaMutexLock
{
public:
	explicit MediaMutexLock();
	~MediaMutexLock();

	void Lock(int lastlockid = 0);
	void Unlock();

	// return count of recusive locked.
	int GetLockCnt() const;

private:
	MediaMutexLock(const MediaMutexLock& other);
	MediaMutexLock& operator=(const MediaMutexLock& other);

	void* iHandle;
	int iLockCnt;
	int m_lastLockId;
};

/// synchronization

/// recusive mutex.
class MediaMutex
{
public:
	explicit MediaMutex();
	~MediaMutex();

	void Lock();
	void Unlock();

private:
	MediaMutex(const MediaMutex& other);
	MediaMutex& operator=(const MediaMutex& other);

	pthread_mutex_t m_mutex;
};

class MediaEvent
{
public:
	// the event is of state reset by default.
	explicit MediaEvent();
	~MediaEvent();

	/// timeoutMS - reserved. always return true.
	bool Wait(unsigned int timeoutMS);
    bool Wait();

	/// release all threads that are waiting on this event.
	/// and then event is reset autoly.
	void Signal();

private:
	MediaEvent(const MediaEvent& other);
	MediaEvent& operator=(const MediaEvent& other);

	void *iHandle;
};


class MutexStackLock
{
public:
	MutexStackLock(MediaMutex &mutex);
	~MutexStackLock();

private:
	MutexStackLock& operator=(const MutexStackLock& other);
	MutexStackLock(const MutexStackLock& other);

	MediaMutex &iMutex;
};

class RWLock
{
public:
	RWLock();
	~RWLock();

	void acquireLockExclusive();
	void releaseLockExclusive();

	void rcquireLockShared();
	void releaseLockShared();

private:
	pthread_rwlock_t m_lock;
};

class ReadLockScoped
{
public:
	ReadLockScoped(RWLock& rwLock)
	: m_rwLock(rwLock)
	{
		m_rwLock.rcquireLockShared();
	}

	~ReadLockScoped()
	{
		m_rwLock.releaseLockShared();
	}

private:
	RWLock& m_rwLock;
};

class WriteLockScoped
{
public:
	WriteLockScoped(RWLock& rwLock)
	: m_rwLock(rwLock)
	{
		m_rwLock.acquireLockExclusive();
	}

	~WriteLockScoped()
	{
		m_rwLock.releaseLockExclusive();
	}

private:
	RWLock& m_rwLock;
};

int ConvertIntArrayToString(char* buf, const int bufsize, const unsigned int* ints, const unsigned int num);

int AtomicInc(int* _me);
int AtomicDec(int* _me);
int AtomicAdd(int* _me, int val);
int AtomicSub(int* _me, int val);

std::string bin2hex(const char *bin, uint32_t len);
std::string hex2bin(std::string hex);

const uint8_t *avc_find_startcode(const uint8_t *p, const uint8_t *end);
int avc_parse_nal_units(uint8_t *buf_out, const uint8_t *buf_in, int size);
int avc_copy_nal_units_as_mp4(uint8_t *buf_out, const uint8_t *buf_in, int size);

//return *_me
int AtomicCmpxChg(int* _me, int oldval, int newval);


#endif //TRUNK_COMMONUTILS_H
