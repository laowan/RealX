//
// Created by Administrator on 2016/9/13.
//

#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>
#include "CommonUtils.h"
#include <sstream>

CValueAvgStat::CValueAvgStat()
: m_min(0x7FFFFFFF)
, m_max(-1)
, m_idx(0)
, m_numAvg(2)
{
    m_totalValue = 0;
    memset(m_Values, 0, sizeof(int) * 256);
}

CValueAvgStat::~CValueAvgStat()
{
}

void CValueAvgStat::init(int numAvg)
{
    m_numAvg = numAvg;
    if (m_numAvg > 256)
    {
        m_numAvg = 256;
    }
    if (m_numAvg < 2)
    {
        m_numAvg = 2;
    }

    m_max = -1;
    m_min = 0x7FFFFFFF;
    m_idx = 0;
    m_totalValue = 0;

    memset(m_Values, 0, sizeof(int) * 256);
}

void CValueAvgStat::AddValue(int value)
{
    int realIdx  = m_idx % m_numAvg;
    int oldValue = m_Values[realIdx];
    m_Values[realIdx] = value;

    if (m_idx >= m_numAvg && (m_min == oldValue || m_max == oldValue))
    {
        m_max = -1;
        m_min = 0x7FFFFFFF;

        for(int i = 0; i < m_numAvg; i++)
        {
            if (m_Values[i] < m_min) m_min = m_Values[i];
            if (m_Values[i] > m_max) m_max = m_Values[i];
        }
    }
    else
    {
        if (value < m_min) m_min = value;
        if (value > m_max) m_max = value;
    }

    m_totalValue += value - oldValue;
    m_idx++;
}

void CValueAvgStat::GetStat(int& avgValue, int& minValue, int& maxValue)
{
    minValue = m_min;
    maxValue = m_max;

    if (m_idx == 0)
    {
        avgValue = 0;
    }
    else if (m_idx > m_numAvg)
    {
        avgValue = (int)(m_totalValue / m_numAvg);
    }
    else
    {
        avgValue = (int)(m_totalValue / m_idx);
    }
}

MediaMutexLock::MediaMutexLock()
: iHandle(malloc(sizeof(pthread_mutex_t)))
, iLockCnt (0)
{
    pthread_mutexattr_t attr;
    pthread_mutexattr_init(&attr);
    pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_RECURSIVE);
    pthread_mutex_init((pthread_mutex_t*)iHandle, &attr);
    pthread_mutexattr_destroy(&attr);
}

MediaMutexLock::~MediaMutexLock()
{
    pthread_mutex_destroy((pthread_mutex_t*)iHandle);
    free(iHandle);
    iHandle = NULL;
}

MediaMutexLock::MediaMutexLock(const MediaMutexLock& other)
{
    m_lastLockId = -1;
}

MediaMutexLock& MediaMutexLock::operator=(const MediaMutexLock& other)
{
    return *this;
}

void MediaMutexLock::Lock(int lastlockid)
{
    pthread_mutex_lock((pthread_mutex_t*)iHandle);
    m_lastLockId = (m_lastLockId << 8) + lastlockid;
    ++iLockCnt;
}

void MediaMutexLock::Unlock()
{
    --iLockCnt;
    m_lastLockId = -1;
    pthread_mutex_unlock((pthread_mutex_t*)iHandle);
}

int MediaMutexLock::GetLockCnt() const
{
    return iLockCnt;
}

MediaMutex::MediaMutex()
{
    pthread_mutexattr_t mutexAttr;
    pthread_mutexattr_init(&mutexAttr);
    pthread_mutexattr_settype(&mutexAttr, PTHREAD_MUTEX_RECURSIVE); //鍚岀嚎绋嬪彲澶氭杩涘叆
    pthread_mutex_init(&m_mutex, &mutexAttr);
    pthread_mutexattr_destroy(&mutexAttr);
}

MediaMutex::~MediaMutex()
{
//    PlatAssert(iLockCnt == 0, "no released");
    pthread_mutex_destroy(&m_mutex);
}

MediaMutex::MediaMutex(const MediaMutex& other)
{
  //  PlatAssert(false, "no implementation");
}

MediaMutex& MediaMutex::operator=(const MediaMutex& other)
{
 //   PlatAssert(false, "no implementation");
    return *this;
}

void MediaMutex::Lock()
{
    pthread_mutex_lock(&m_mutex);
}

void MediaMutex::Unlock()
{
    pthread_mutex_unlock(&m_mutex);
}

/////////// MediaEvent
MediaEvent::MediaEvent()
: iHandle(malloc(sizeof(pthread_mutex_t) + sizeof(pthread_cond_t)))
{
    pthread_mutex_t *mutex = (pthread_mutex_t*)iHandle;
    pthread_cond_t *cond = (pthread_cond_t*)((char*)iHandle + sizeof(pthread_mutex_t));
    pthread_mutex_init(mutex, NULL);
    pthread_cond_init(cond, NULL);
}

MediaEvent::~MediaEvent()
{
    pthread_mutex_t *mutex = (pthread_mutex_t*)iHandle;
    pthread_cond_t *cond = (pthread_cond_t*)((char*)iHandle + sizeof(pthread_mutex_t));

    pthread_cond_destroy(cond);
    pthread_mutex_destroy(mutex);

    free (iHandle);
    iHandle = NULL;
}

MediaEvent::MediaEvent(const MediaEvent& other)
{
 //   PlatAssert(false, "no implementation");
}

MediaEvent& MediaEvent::operator=(const MediaEvent& other)
{
//    PlatAssert(false, "no implementation");
    return *this;
}

bool MediaEvent::Wait(unsigned int timeoutMS)
{
    pthread_mutex_t *mutex = (pthread_mutex_t*)iHandle;
    pthread_cond_t *cond = (pthread_cond_t*)((char*)iHandle + sizeof(pthread_mutex_t));

    pthread_mutex_lock(mutex);
    struct timespec ts;
    timeval value;
    struct timezone time_zone;
    time_zone.tz_minuteswest = 0;
    time_zone.tz_dsttime = 0;
    gettimeofday(&value, &time_zone);

#if defined(__APPLE__)
	TIMEVAL_TO_TIMESPEC(&value, &ts);
#else
	ts.tv_sec = value.tv_sec;
	ts.tv_nsec = value.tv_usec * 1000;
#endif

    ts.tv_sec  += timeoutMS / 1000;
    ts.tv_nsec += (timeoutMS - (timeoutMS / 1000) * 1000) * 1000000;
    pthread_cond_timedwait(cond, mutex, &ts);
    pthread_mutex_unlock(mutex);

    return true;
}

bool MediaEvent::Wait()
{
    pthread_mutex_t *mutex = (pthread_mutex_t*)iHandle;
    pthread_cond_t *cond = (pthread_cond_t*)((char*)iHandle + sizeof(pthread_mutex_t));

    pthread_mutex_lock(mutex);
    pthread_cond_wait(cond, mutex);
    pthread_mutex_unlock(mutex);

    return true;
}

void MediaEvent::Signal()
{
    pthread_cond_t *cond = (pthread_cond_t*)((char*)iHandle + sizeof(pthread_mutex_t));
    pthread_cond_broadcast(cond);
}


/////////////////// MutexStackLock
MutexStackLock::MutexStackLock(MediaMutex &mutex)
: iMutex (mutex)
{
    iMutex.Lock();
}

MutexStackLock::~MutexStackLock()
{
    iMutex.Unlock();
}

MutexStackLock& MutexStackLock::operator=(const MutexStackLock& other)
{
  //  LibAssert(false, "no impl");
    return *this;
}

MutexStackLock::MutexStackLock(const MutexStackLock& other)
: iMutex(other.iMutex)
{
   // LibAssert(false, "no impl");
}

int ConvertIntArrayToString(char* buf, const int bufsize, const unsigned int* ints, const unsigned int num)
{
    buf[0] = 0;
    if (bufsize < (int)(num * 12) || buf == NULL || ints == NULL || num <= 0) return -1; //0xFFFFFFFF

    char* writeptr = buf;
    for(unsigned int i = 0; i < num; i++)
    {
        sprintf(writeptr, " %d", ints[i]);
        writeptr += strlen(writeptr);
    }

    return 0;
}

int AtomicInc(int* _me)
{
    return __sync_fetch_and_add(_me, 1);
}

int AtomicDec(int* _me)
{
    return __sync_fetch_and_sub(_me, 1);
}

int AtomicAdd(int* _me, int val)
{
    return __sync_fetch_and_add(_me, val);
}

int AtomicSub(int* _me, int val)
{
    return __sync_fetch_and_sub(_me, val);
}

int AtomicCmpxChg(int* _me, int oldval, int newval)
{
  return __sync_val_compare_and_swap(_me, oldval, newval);
}

static const uint8_t *avc_find_startcode_internal(const uint8_t *p, const uint8_t *end)
{
	const uint8_t *a = p + 4 - ((intptr_t)p & 3);
	for (end -= 3; p < a && p < end; p++) {
		if (p[0] == 0 && p[1] == 0 && p[2] == 1)
			return p;
	}

	for (end -= 3; p < end; p += 4) {
		uint32_t x = *(const uint32_t*)p;
		if ((x - 0x01010101) & (~x) & 0x80808080) { // generic
			if (p[1] == 0) {
				if (p[0] == 0 && p[2] == 1)
					return p;
				if (p[2] == 0 && p[3] == 1)
					return p + 1;
			}
			if (p[3] == 0) {
				if (p[2] == 0 && p[4] == 1)
					return p + 2;
				if (p[4] == 0 && p[5] == 1)
					return p + 3;
			}
		}
	}

	for (end += 3; p < end; p++) {
		if (p[0] == 0 && p[1] == 0 && p[2] == 1)
			return p;
	}
	return end + 3;
}

const uint8_t *avc_find_startcode(const uint8_t *p, const uint8_t *end)
{
	const uint8_t *out = avc_find_startcode_internal(p, end);
	if (p < out && out < end && !out[-1])
		out--;

	return out;
}

int avc_parse_nal_units(uint8_t *buf_out, const uint8_t *buf_in, int size)
{
	const uint8_t *p = buf_in;
	const uint8_t *end = p + size;
	const uint8_t *nal_start, *nal_end;
	uint8_t *pb = buf_out;
	size = 0;
	nal_start = avc_find_startcode(p, end);

	if (nal_start < end)
	{
		while (!*(nal_start++));
		nal_end = avc_find_startcode(nal_start, end);

		int nal_size = nal_end - nal_start;
		pb[0] = nal_size >> 24;
		pb[1] = nal_size >> 16;
		pb[2] = nal_size >> 8;
		pb[3] = nal_size;
		memcpy(pb + 4, nal_start, nal_size);

		size += 4 + nal_size;
	}

	return size;
}

int avc_copy_nal_units_as_mp4(uint8_t *buf_out, const uint8_t *buf_in, int size)
{
	const uint8_t *nal_start, *end, *nal_end;
	int nCount = 0;
	end = buf_in + size;
	nal_start = avc_find_startcode(buf_in, end);
	while (nal_start < end) {
		while (!*(nal_start++));
		nal_end = avc_find_startcode(nal_start, end);
		int nal_size = nal_end - nal_start;
		memcpy(buf_out + nCount + 4, nal_start, nal_size);
		uint8_t *pb = buf_out + nCount;
		pb[0] = nal_size >> 24;
		pb[1] = nal_size >> 16;
		pb[2] = nal_size >> 8;
		pb[3] = nal_size;
		nCount += nal_size + 4;
		nal_start = nal_end;
	}
	return nCount;
}

RWLock::RWLock()
: m_lock()
{
	pthread_rwlock_init(&m_lock, 0);
}

RWLock::~RWLock()
{
	pthread_rwlock_destroy(&m_lock);
}

void RWLock::acquireLockExclusive()
{
	pthread_rwlock_wrlock(&m_lock);
}

void RWLock::releaseLockExclusive()
{
	pthread_rwlock_unlock(&m_lock);
}

void RWLock::rcquireLockShared()
{
	pthread_rwlock_rdlock(&m_lock);
}

void RWLock::releaseLockShared()
{
	pthread_rwlock_unlock(&m_lock);
}

std::string bin2hex(const char *bin, uint32_t len)
{
	std::ostringstream os;
	for(uint32_t i = 0; i<len; i++){
		char st[4];
		uint8_t c = bin[i];
		sprintf(st, "%02x ", c);
		os << st;
	}
	return os.str();


}
std::string hex2bin(std::string hex)
{
	if (hex.size() % 2 != 0)
	{
		return "";
	}
	
	std::string strBin;
	strBin.resize(hex.size() / 2);
	for (size_t i = 0; i < strBin.size(); i++)
	{
		uint8_t cTemp = 0;
		for (size_t j = 0; j < 2; j++)
		{
			char cCur = hex[2 * i + j];
			if (cCur >= '0' && cCur <= '9')
			{
				cTemp = (cTemp << 4) + (cCur - '0');
			}
			else if (cCur >= 'a' && cCur <= 'f')
			{
				cTemp = (cTemp << 4) + (cCur - 'a' + 10);
			}
			else if (cCur >= 'A' && cCur <= 'F')
			{
				cTemp = (cTemp << 4) + (cCur - 'A' + 10);
			}
			else
			{
				return "";
			}
		}
		strBin[i] = cTemp;
	}

	return strBin;
}

