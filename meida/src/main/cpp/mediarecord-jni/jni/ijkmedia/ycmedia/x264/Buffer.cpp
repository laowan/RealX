//
// Created by Administrator on 2016/9/13.
//


#include "Common.h"

#include "CommonUtils.h"
#include "Mediabase.h"
#include <map>
#include <list>

///// NOTICE : current implementation doesn't support alignment.

#define MaxBufferCacheListCnt   (8)

struct BufferHeader
{
    unsigned int iHeadSignature;
    unsigned int iBufSize;
    unsigned int iTailSignature;
};

struct BufferTail
{
    unsigned int iTailSignature;
};

static unsigned int gHeadSignature = 0xEAAEEAAE;
static unsigned int gTailSignature = 0xCDCEECDC;


typedef std::list<void*> stdPointerList;
struct BufferCacheInfo
{
    unsigned int    iRefCnt;
    unsigned int    iBufSize;
    stdPointerList  iBufList;
};

// key : cache size.
typedef std::map<unsigned int, BufferCacheInfo*> stdCacheMap;

static MediaMutex   gCacheLock;
static stdCacheMap  gCacheInfos;


MediaLibrary::BufferCacheHandle MediaLibrary::CreateBufferCache(unsigned int bufferSize, int alignment)
{
    BufferCacheHandle ret = InvalidBufferCacheHandle;
    if (bufferSize > 0)
    {
        ret = bufferSize;

        gCacheLock.Lock();
        stdCacheMap::iterator found = gCacheInfos.find(bufferSize);
        if (found != gCacheInfos.end())
        {
            ++found->second->iRefCnt;
        }
        else
        {
            BufferCacheInfo *cache = new BufferCacheInfo();
            cache->iBufSize = bufferSize;
            cache->iRefCnt = 1;
            gCacheInfos[bufferSize] = cache;
        }
        gCacheLock.Unlock();
    }
    return ret;
}

void MediaLibrary::DestoryBufferCache(BufferCacheHandle handle)
{
    if (handle != InvalidBufferCacheHandle)
    {
        unsigned int bufsize = handle & (unsigned int)-1;
        BufferCacheInfo *released = NULL;
        gCacheLock.Lock();
        stdCacheMap::iterator iter = gCacheInfos.find(bufsize);
        if (iter != gCacheInfos.end())
        {
            if (--iter->second->iRefCnt == 0)
            {
                // release this cache.
                released = iter->second;
                gCacheInfos.erase(iter);
            }
        }
        gCacheLock.Unlock();

        if (released)
        {
            for (stdPointerList::iterator iter = released->iBufList.begin();
                 iter != released->iBufList.end();
                 ++iter)
            {
                free(*iter);
            }
            delete released;
        }
    }
}

void* MediaLibrary::AllocBufferFromCache(BufferCacheHandle handle, bool clear)
{
    void *ret = NULL;
    unsigned int size = handle & (unsigned int)-1;

    if (size > 0)
    {
        gCacheLock.Lock();
        stdCacheMap::iterator iter = gCacheInfos.find(size);
        if (iter != gCacheInfos.end())
        {
            if (iter->second->iBufList.size() > 0)
            {
                ret = iter->second->iBufList.back();
                iter->second->iBufList.pop_back();
            }
        }
        gCacheLock.Unlock();

        if (ret == NULL)
            ret = AllocBuffer(size, clear, 0);
        else
        {
            BufferHeader *header = (BufferHeader*)ret;
            BufferTail *tail = (BufferTail*)((char*)ret + sizeof(BufferHeader) + size);
            //PlatAssert(header->iHeadSignature == gHeadSignature && header->iTailSignature == gTailSignature &&
                     //  header->iBufSize == size, "signature");
           // PlatAssert(tail->iTailSignature == gTailSignature, "signaturetail");

            ret = (char*)ret + sizeof(BufferHeader);
        }
    }

    return ret;
}

void* MediaLibrary::AllocBuffer(unsigned int size, bool clear, int alignment)
{
    unsigned int realsize = size + sizeof(BufferHeader) + sizeof(BufferTail);
    if (size > 0)
    {
        BufferHeader *header = (BufferHeader*)malloc(realsize);
        if (header == NULL)
        {
            LOGT("ERROR! Alloc Failed with size %d", realsize);
			return NULL;
        }

        header->iHeadSignature = gHeadSignature;
        header->iTailSignature = gTailSignature;
        header->iBufSize = size;

        void *ret = (header + 1);
        if (clear)
            memset(ret, 0, size);

        BufferTail *tail = (BufferTail*)((char*)ret + size);
        tail->iTailSignature = gTailSignature;
        return ret;
    }

    return NULL;
}

void MediaLibrary::FreeBuffer(void *buffer)
{
    if (buffer)
    {
        BufferHeader *header = (BufferHeader*)((char*)buffer - sizeof(BufferHeader));
        //PlatAssert(header->iHeadSignature == gHeadSignature && header->iTailSignature == gTailSignature, "signature");
        unsigned int size = header->iBufSize;

        //BufferTail *tail = (BufferTail*)((char*)buffer + size);
        //PlatAssert(tail->iTailSignature == gTailSignature, "signaturetail");

        gCacheLock.Lock();
        stdCacheMap::iterator iter = gCacheInfos.find(size);
        if (iter != gCacheInfos.end() && iter->second->iBufList.size() < MaxBufferCacheListCnt)
        {
            iter->second->iBufList.push_back(header);
            buffer = NULL;
        }
        gCacheLock.Unlock();

        if (buffer)
		{
            free(header);   // not buffer !!
		}
    }
}

void MediaLibraryFreeBuffer(void *buffer)
{
    MediaLibrary::FreeBuffer(buffer);
}

unsigned int MediaLibrary::GetAllocatedBufferSize(void *buffer)
{
    if (buffer)
    {
        BufferHeader *header = (BufferHeader*)((char*)buffer - sizeof(BufferHeader));
        //PlatAssert(header->iHeadSignature == gHeadSignature && header->iTailSignature == gTailSignature, "signature");
        return header->iBufSize;
    }
    return 0;
}