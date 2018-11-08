//
// Created by kele on 2016/10/17.
//

#include "AdaptivePicBuffer.h"
#include "Common.h"

USING_NAMESPACE_YYMFW;

AdaptivePicBuffer::AdaptivePicBuffer()
{
    m_pBuffer = NULL;
    m_BufferSize = 0;
	m_pos = 0;
}

AdaptivePicBuffer::~AdaptivePicBuffer()
{
    if(m_pBuffer) {
        freeBuffer(m_pBuffer);
    }
}

void*   AdaptivePicBuffer::getBuffer(int size)
{
    if(size > 0 && size > capacity()) {
        increase_capacty(size);
    }
    void* ret =  (m_pBuffer+m_pos);
	m_pos += size;
	return ret;
}


void AdaptivePicBuffer::clear()
{
	m_pos = 0;
}

void    AdaptivePicBuffer::freeBuffer(void* buffer)
{
    if(m_pBuffer != NULL && m_pBuffer == buffer) {
        //LOGDXXX("AdaptivePicBuffer, free buffer!!!, buffer size:%d", m_BufferSize);
        free(m_pBuffer);
        m_pBuffer = NULL;
        m_BufferSize = 0;
		m_pos = 0;
    }
}

void  AdaptivePicBuffer::increase_capacty(int size)
{
    //视频压缩的特点, 头一个帧数据会是一个很大的数据, 所以后续增长可以比较慢.
    // size + m_BufferSize,  所以最大的buffer的大小将会目前发现最大帧的2倍.
    //浪费的内存并不多.
    if(capacity() >= size ||size  <=0)
        return;

    int result = 2*size + m_BufferSize;

	//relloc run some problem...
	void *p = m_pBuffer;
    m_pBuffer = (void*)malloc(result);
	if(p != NULL) {
		memcpy(m_pBuffer, p, m_BufferSize);
		free(p);
	}
	m_BufferSize = result;
    //LOGDXXX("AdaptivePicBuffer, malloc buffer!!!, size=%d", m_BufferSize);

}
