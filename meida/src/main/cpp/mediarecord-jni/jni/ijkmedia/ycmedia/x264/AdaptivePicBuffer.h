//
// Created by kele on 2016/10/17.
//

#ifndef TRUNK_ENCODEDDATABUFFERPOOL_H
#define TRUNK_ENCODEDDATABUFFERPOOL_H


#include "Macros.h"

NAMESPACE_YYMFW_BEGIN

//TODO. 类的命令斟酌, 主要实现一个
class AdaptivePicBuffer {
public:
    AdaptivePicBuffer();
    ~AdaptivePicBuffer();

public:
    void*   getBuffer(int size);
    void    freeBuffer(void* buffer);
	void 	clear();

private:
    void    increase_capacty(int size);
	int 	inline capacity() {
		return (m_BufferSize-m_pos);
	}

private:
    void*   m_pBuffer;
    int     m_BufferSize;
	int 	m_pos;
};

NAMESPACE_YYMFW_END

#endif //TRUNK_ENCODEDDATABUFFERPOOL_H
