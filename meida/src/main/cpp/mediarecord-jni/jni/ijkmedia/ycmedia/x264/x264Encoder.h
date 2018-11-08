//
// Created by Administrator on 2016/9/13.
//

#ifndef TRUNK_X264ENCODER_H
#define TRUNK_X264ENCODER_H

#include "IVideoCodec.h"
#include "Mediabase.h"
#include "Macros.h"
#include <list>

NAMESPACE_YYMFW_BEGIN
class AdaptivePicBuffer;
NAMESPACE_YYMFW_END

struct X264Encoder;
class CX264Encoder : public IVideoCodec
{
public:
	CX264Encoder();
	virtual ~CX264Encoder();

	int  Init(void* pParam, std::string configStr);
	int  Process(const unsigned char *pData, unsigned int nDataLen, void* pInDes, void** pOutDes);
	int  flush(void** pOutDes);
    int  CodecMode();
    int  CodecID();
	int  CodecLevel();
	void DeInit();
    const char* CodecDescribe();
	void SetTargetBitrate(int bitrateInKbps);

private:
	const uint8_t * find_startcode_internal(const uint8_t *p, const uint8_t *end);
	const uint8_t* find_startcode(const uint8_t *p, const uint8_t *end);
	void  packEncodedList(const uint8_t* p, uint32_t size,unsigned int pts, unsigned int dts, MediaLibrary::VideoFrameType frameType);
	void  pushVideoEncodedData(MediaLibrary::VideoEncodedData* data);
	int   convert_to_x264_frame_type(MediaLibrary::VideoFrameType frameType);
	MediaLibrary::VideoFrameType convert_to_yy_frame_type(int x264FrameType);

	int fetchFrame(void** pOutDes, int nNal, void *pic_out, NAMESPACE_YYMFW::AdaptivePicBuffer  *picBuffer);
	void clearPicBufferList();

private:
	X264Encoder * m_pX264Encoder;
	int m_nPicW;
	int m_nPicH;
	int m_nFps;
	int m_nBitrate;
	int m_nProfile;
	int m_nPicFormat;
	unsigned char* m_pSps;
	int m_nSpsLen;
	unsigned char* m_pPps;
	int m_nPpsLen;
	bool m_isFirstFrame;
    float m_rateFactor;

	bool mRepeateHeader;

	std::list<NAMESPACE_YYMFW::AdaptivePicBuffer  *> m_PicDataBufferList;

	MediaLibrary::VideoEncodedList* m_outputList ;
	NAMESPACE_YYMFW::AdaptivePicBuffer  *m_PicDataBuffer;
	NAMESPACE_YYMFW::AdaptivePicBuffer  *m_ppsBuffer;
	NAMESPACE_YYMFW::AdaptivePicBuffer  * m_spsBuffer;
};
#endif //TRUNK_X264ENCODER_H
