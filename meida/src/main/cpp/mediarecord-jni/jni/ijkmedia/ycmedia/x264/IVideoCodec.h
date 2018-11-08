//
// Created by Administrator on 2016/9/13.
//

#ifndef TRUNK_IVIDEOCODEC_H
#define TRUNK_IVIDEOCODEC_H

#include "Common.h"

class IVideoCodec
{
public:
	virtual ~IVideoCodec(){}
	virtual int  Init(void* pParam, std::string configStr) = 0;
	virtual int  Process(const unsigned char *pData, unsigned int nDataLen, void* pInDes, void** pOutDes) = 0;
	virtual int  flush(void** pOutDes) = 0;
	virtual int  CodecMode()                              = 0;
	virtual int  CodecID()                                = 0;
	virtual int  CodecLevel()                             = 0;
	virtual void DeInit()                                = 0;
	virtual const char* CodecDescribe() {return "";}
	virtual bool  IsHardware() { return false; }
	virtual bool  IsAvailable() {return true;}
	virtual bool  exapiSupport() { return false; }
	virtual int   exapiProcess(const unsigned char *pData, unsigned int nDataLen, void* pInDes, void*& pOutDes, int& numPics) { return -1 ; }
	virtual uint32_t GetDecodeDelay() { return 0; }
	virtual void SetTargetBitrate(int bitrateInKbps) { };
    virtual int GetVideoWidth() { return 0; }
    virtual int GetVideoHeight() { return 0; }
};

#endif //TRUNK_YYVIDEOCODEC_H_H
