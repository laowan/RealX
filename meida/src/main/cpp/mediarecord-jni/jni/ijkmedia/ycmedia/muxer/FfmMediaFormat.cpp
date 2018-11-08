#include "FfmMediaFormat.h"
#include <jni.h>
#include "x264/Common.h"
#include "x264/CommonUtils.h"


USING_NAMESPACE_YYMFW;


const std::string FfmMediaFormat::KEY_AAC_PROFILE("aac-profile");
const std::string FfmMediaFormat::KEY_BIT_RATE("bitrate");
const std::string FfmMediaFormat::KEY_CHANNEL_COUNT("channel-count");
const std::string FfmMediaFormat::KEY_CHANNEL_MASK("channel-mask");
const std::string FfmMediaFormat::KEY_COLOR_FORMAT("color-format");
const std::string FfmMediaFormat::KEY_DURATION("durationUs");
const std::string FfmMediaFormat::KEY_FLAC_COMPRESSION_LEVEL("flac-compression-level");
const std::string FfmMediaFormat::KEY_FRAME_RATE("frame-rate");
const std::string FfmMediaFormat::KEY_HEIGHT("height");
const std::string FfmMediaFormat::KEY_IS_ADTS("is-adts");
const std::string FfmMediaFormat::KEY_IS_AUTOSELECT("is-autoselect");
const std::string FfmMediaFormat::KEY_IS_DEFAULT("is-default");
const std::string FfmMediaFormat::KEY_IS_FORCED_SUBTITLE("is-forced-subtitle");
const std::string FfmMediaFormat::KEY_I_FRAME_INTERVAL("i-frame-interval");
const std::string FfmMediaFormat::KEY_LANGUAGE("language");
const std::string FfmMediaFormat::KEY_MAX_HEIGHT("max-height");
const std::string FfmMediaFormat::KEY_MAX_INPUT_SIZE("max-input-size");
const std::string FfmMediaFormat::KEY_MAX_WIDTH("max-width");
const std::string FfmMediaFormat::KEY_MIME("mime");
const std::string FfmMediaFormat::KEY_PUSH_BLANK_BUFFERS_ON_STOP("push-blank-buffers-on-shutdown");
const std::string FfmMediaFormat::KEY_REPEAT_PREVIOUS_FRAME_AFTER("repeat-previous-frame-after");
const std::string FfmMediaFormat::KEY_SAMPLE_RATE("sample-rate");
const std::string FfmMediaFormat::KEY_WIDTH("width");
const std::string FfmMediaFormat::KEY_STRIDE("stride");
const std::string FfmMediaFormat::KEY_MEDIA_TYPE("media-type");

const std::string  FfmMediaFormat::KEY_AVC_SPS("csd-0");
const std::string  FfmMediaFormat::KEY_AVC_PPS("csd-1");




FfmMediaFormat::FfmMediaFormat()
{

}

FfmMediaFormat::~FfmMediaFormat()
{


}


// mediaformat:  "mediaformat=:width=10, height=1234, bitrate=233". etc.....
void FfmMediaFormat::initMediaFormat(std::string& mediaformat)
{
	if(mediaformat.empty())
		return;

	LOGD("FfmMediaFormat::initMediaFormat: %s", mediaformat.c_str());

	size_t 		startPos = 0;
	std::size_t splitPos = mediaformat.find(":");

	while(splitPos != std::string::npos) {
		//LOGD("startPos=%u, splitPos=%u", startPos, splitPos);
		std::string item =  mediaformat.substr(startPos, splitPos-startPos);
		if(!item.empty()) {
			//LOGD("item=%s", item.c_str());
			std::size_t keyPos = item.find("=");
			if(keyPos != std::string::npos) {				
				std::string key = item.substr(0, keyPos);
				std::string value = item.substr(keyPos+1, splitPos);

				LOGD("FfmMediaFormat::initMediaFormat: key=%s, value=%s", key.c_str(), value.c_str());

				if(key == FfmMediaFormat::KEY_AVC_SPS) {
					LOGD("csd-0 hex: %s", value.c_str());
					std::string csd0value = hex2bin(value);
					mFormatMap.insert(std::make_pair(key, csd0value));

					std::string hex = bin2hex(csd0value.c_str(), csd0value.size());
					LOGD("csd0 bin2hex: %s", hex.c_str());
					
				} else if(key == FfmMediaFormat::KEY_AVC_PPS) {
					LOGD("csd-1 hex: %s", value.c_str());
					std::string csd1value = hex2bin(value);
					mFormatMap.insert(std::make_pair(key, csd1value));

					std::string hex = bin2hex(csd1value.c_str(), csd1value.size());
					LOGD("csd1 bin2hex: %s", hex.c_str());
					
				} else {
					mFormatMap.insert(std::make_pair(key, value));
				}
			}
		}

		if(splitPos != mediaformat.size()) {	
			startPos = splitPos + 1;
			splitPos = mediaformat.find(":", startPos, 1);
			//LOGD("end startPos=%u, splitPos=%u", startPos, splitPos);
		} else {
			splitPos = std::string::npos;
			//LOGD("end111 startPos=%u, splitPos=%u", startPos, splitPos);
		}

		
	}
	
}


int FfmMediaFormat::getIntValue(std::string key, int defaultVal)
{
	 std::map<std::string,std::string>::iterator it = mFormatMap.find(key);
	 if(it != mFormatMap.end() && !it->second.empty()) {
		return atoi(it->second.c_str()) ;
	 }
	 return defaultVal;
}

long FfmMediaFormat::getLongValue(std::string key, long defaultVal)
{
	std::map<std::string,std::string>::iterator it = mFormatMap.find(key);
	if(it != mFormatMap.end() && !it->second.empty()) {
	   return atol(it->second.c_str()) ;
	} 

    return defaultVal;
}

std::string FfmMediaFormat::getStringValue(std::string key, std::string& defaultVal) 
{

	 std::map<std::string,std::string>::iterator it = mFormatMap.find(key);
	 if(it != mFormatMap.end()) {
		return it->second;
	 } 

	 return defaultVal;
	

}







