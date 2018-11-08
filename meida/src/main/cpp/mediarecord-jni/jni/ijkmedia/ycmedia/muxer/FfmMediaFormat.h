
#ifndef _FFMMEDIAFORMAT_H
#define _FFMMEDIAFORMAT_H


#include "x264/Mediabase.h"
#include "x264/Macros.h"
#include <string>
#include <map>

NAMESPACE_YYMFW_BEGIN


/** Compatible with MediaFormt of andorid sdk*/
class FfmMediaFormat {
	//copy from andoid source code.

public:

	static const std::string KEY_AAC_PROFILE;
	static const std::string KEY_BIT_RATE;
	static const std::string KEY_CHANNEL_COUNT;
	static const std::string KEY_CHANNEL_MASK;
	static const std::string KEY_COLOR_FORMAT;
	static const std::string KEY_DURATION;
	static const std::string KEY_FLAC_COMPRESSION_LEVEL;
	static const std::string KEY_FRAME_RATE;
	static const std::string KEY_HEIGHT;
	static const std::string KEY_IS_ADTS;
	static const std::string KEY_IS_AUTOSELECT;
	static const std::string KEY_IS_DEFAULT;
	static const std::string KEY_IS_FORCED_SUBTITLE;
	static const std::string KEY_I_FRAME_INTERVAL;
	static const std::string KEY_LANGUAGE;
	static const std::string KEY_MAX_HEIGHT;
	static const std::string KEY_MAX_INPUT_SIZE;
	static const std::string KEY_MAX_WIDTH;
	static const std::string KEY_MIME;
	static const std::string KEY_PUSH_BLANK_BUFFERS_ON_STOP;
	static const std::string KEY_REPEAT_PREVIOUS_FRAME_AFTER;
	static const std::string KEY_SAMPLE_RATE;
	static const std::string KEY_WIDTH;
	static const std::string KEY_STRIDE;
	static const std::string KEY_MEDIA_TYPE;

	static const std::string  KEY_AVC_SPS;
    static const std::string  KEY_AVC_PPS;

    static const int FLAG_KEY_FRAME = 0x01;


public:
	FfmMediaFormat();
	~FfmMediaFormat();

	// mediaformat as following 
	//"width=10:height=120:stride=133:bitrate=31:"
	void initMediaFormat(std::string& mediaformat);

public:
	int 			getIntValue(std::string key, int defaultVal);
	long 			getLongValue(std::string key, long defaultVal);
	std::string 	getStringValue(std::string key, std::string& defaultVal);



private:
	std::map<std::string, std::string> 	mFormatMap; 	

};



NAMESPACE_YYMFW_END



#endif