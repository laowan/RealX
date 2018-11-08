#ifndef  _AUDIO_JITTER_BUFFER_IMPL_H_
#define  _AUDIO_JITTER_BUFFER_IMPL_H_

#include <string>

class IAudioJitterBuffer
{
public:
	virtual void Destroy() = 0;

	virtual bool Get(std::string& frame, uint8_t& codecType, bool &isLBRFrame, uint32_t& timeStamp, uint8_t& ssrc) = 0;
};

#endif