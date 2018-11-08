#ifndef _AUDIOENGINE_DEBUG_INTERFACE_H_
#define _AUDIOENGINE_DEBUG_INTERFACE_H_

#include <stdint.h>
#ifdef __cplusplus
#include <string>
#endif



#ifdef __cplusplus
extern "C"
{
#endif
	typedef struct AecCoreParameter
	{
		//aec
		//aec fir
		int32_t filterlengthmode;       //default 1.  bigger then longer
		//aec echosuppress(es)
		int32_t disableechosuppress;    //0:enable 1:disable //default 0
		int32_t nlpoverdrive;           //default 2.   0,1,2  bigger stronger
		int32_t disableeshighband;      //0:enable 1:disable //default 0
		int32_t eshighbandstartband;    //default 32
		//aec comfortnoise(cn)
		int32_t disablecomfortnoise;    //0:enable 1:disable //default 0
		int32_t disablecnhighband;      //0:enable 1:disable //default 0
		float cnhighbandscale;           //default 0.4
	} AecCoreParameter;
	void aeccoreparameter_setdefault(AecCoreParameter* acpp);
	extern AecCoreParameter g_AecCoreConfig;
#ifdef __cplusplus
}
#endif

#ifdef __cplusplus

struct CEchoDelayEstParameters
{
	//aec delay estimate
	int32_t disabledelayestimate;   //0:enable 1:disable //default 0
	int32_t delayconservecnt;       //10ms block count //default 2
	int32_t delayestimatelength;    //default 180
	int32_t delayestimatelookahead; //default 15
	//notice (delayestimatelookahead+delayestimatelength) maximum is 600
	int32_t decalcintervalms;       //default 1000
	int32_t destablecnt;            //default 8
	int32_t robustvalidation;       //default 1     0:disable 1:enable
	int32_t rvallowoffset;          //default 6     robustvalidation forward stable range
};

struct CEnvironmentAudioParameters
{
	//for android
	int32_t isandroid;              //1:yes 0:no //default 1
	int32_t androidmediajni;        //1:enable 0:disable //default 1
	int32_t androidopensles;         //1:enable 0:disable //default 0
	int32_t androidsamsungsdk;      //1:enable 0:disable //default 0
	int32_t usehardaec;             //1:enable 0:disable //default 0

	int32_t audioloudmode;          //default 3(MODE_IN_COMMUNICATION). see core/audio_adaptation.cc for full information
	int32_t inputloudstreamtype;    //default -128(NONE OP). see core/audio_adaptation.cc for full information
	int32_t outputloudstreamtype;   //default 0(STREAM_VOICE_CALL). see core/audio_adaptation.cc for full information

	int32_t audioearmode;          //default 3(MODE_IN_COMMUNICATION). see core/audio_adaptation.cc for full information
	int32_t inputearstreamtype;    //default -128(NONE OP). see core/audio_adaptation.cc for full information
	int32_t outputearstreamtype;   //default 0(STREAM_VOICE_CALL). see core/audio_adaptation.cc for full information

	int32_t audiohdmode;          //default 0(MODE_NORMAL). see core/audio_adaptation.cc for full information
	int32_t inputhdstreamtype;    //default 1(AUDIOSOURCE_MIC). see core/audio_adaptation.cc for full information
	int32_t outputhdstreamtype;   //default 3(STREAM_MUSIC). see core/audio_adaptation.cc for full information
};

struct CAudioProcessingImpParameters
{
	int32_t usewebrtcaec;           //0:disable 1:enable //default 0
	int32_t usebuildinvoiceprocess; //0:disable 1:enable //default 0
	int32_t usescrapreduction;      //0:disable 1:enable //default 0
};

void echodelayestparameters_defaultvalue(CEchoDelayEstParameters* edep);
void environmentaudioparameters_defaultvalue(CEnvironmentAudioParameters* eap);
void caudioprocessingimpparameters_defaultvalue(CAudioProcessingImpParameters* apip);

extern CEchoDelayEstParameters g_EchoDelayConfig;
extern CEnvironmentAudioParameters g_DeviceConfig;
extern CAudioProcessingImpParameters g_AudioProcessConfig;

void setglobal_config(char* arg, int arglen);

void generate_config(char* outputconfig, int& outlen, CEnvironmentAudioParameters* eap, CAudioProcessingImpParameters* apip, CEchoDelayEstParameters* edep, AecCoreParameter* acp);//if output outlen become bigger means fail
std::string audioprocserializetostr(CEnvironmentAudioParameters* eap, CAudioProcessingImpParameters* apip, CEchoDelayEstParameters* edep, AecCoreParameter* acp);
void audioprocdeserializefromstr(std::string dat, CEnvironmentAudioParameters* eap, CAudioProcessingImpParameters* apip, CEchoDelayEstParameters* edep, AecCoreParameter* acp);


////Test interface

class IAudioSourcer
{
public:
	virtual void initframe(int16_t* data_, int& samples_per_channel_, int& sample_rate_hz_, int& num_channels_) = 0;
	virtual int getoneframe(int16_t* data_, int& samples_per_channel_, int& sample_rate_hz_, int& num_channels_) = 0;//return 0 ok, 1 no data
};

class IAudioAECTest
{
public:
	virtual ~IAudioAECTest(){}
	virtual void Destroy() = 0;
	virtual void BasicGenerateProcessing(IAudioSourcer* pFarendFileName, IAudioSourcer* pNearendFileName, const char* pOutFileName,int processsamplerate, const char* configdata, int cfgdatalen, int& framesprocess, const char* analysis_basepath) = 0;
};

IAudioAECTest* CreateAudioAECTestInterface();


/////////////////////
class IAudioDelayTestCallBack
{
public:
    virtual int32_t OnAudioInput(const void* audioSamples,
                                            const uint32_t nSamples,
                                            const uint8_t nBytesPerSample,
                                            const uint8_t nChannels,
                                            const uint32_t samplesPerSec) = 0;
    virtual int32_t OnAudioOutputRequest(const uint32_t nSamples,
                                     const uint8_t nBytesPerSample,
                                     const uint8_t nChannels,
                                     const uint32_t samplesPerSec,
                                     void* audioSamples) = 0;
};

class IAudioDelayTest
{
public:
	virtual void Destroy() = 0;

	virtual void StartDeviceEngine(const char* arg, int arglen,IAudioDelayTestCallBack* cb) = 0;
	virtual void StopDeviceEngine() = 0;
	virtual void StartHDDeviceEngine(const char* arg, int arglen,IAudioDelayTestCallBack* cb) = 0;
	virtual void StopHDDeviceEngine() = 0;
	virtual void StartFullEngineInvalidAEC(const char* arg, int arglen,IAudioDelayTestCallBack* cb) = 0;
	virtual void StopFullEngineInvalidAEC() = 0;
	virtual void StartHDFullEngineInvalidAEC(const char* arg, int arglen,IAudioDelayTestCallBack* cb) = 0;
	virtual void StopHDFullEngineInvalidAEC() = 0;
};
IAudioDelayTest* CreateAudioDelayTestInterface();

#endif

#endif //_AUDIOENGINE_DEBUG_INTERFACE_H_
