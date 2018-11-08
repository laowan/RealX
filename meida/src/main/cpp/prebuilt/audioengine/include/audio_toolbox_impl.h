#ifndef _AUDIO_TOOLBOX_IMPL_H_
#define _AUDIO_TOOLBOX_IMPL_H_

#include <string>
#include <vector>
#include "int_type.h"

typedef void(*DspLogCallback)(const char* pLogStr);

void SetDspLogFunction(DspLogCallback logFunc = NULL);

struct ReverbParam
{
	float roomsize;
	float revtime;
	float damping;
	float inputbandwidth;
	float drylevel;
	float earlylevel;
	float taillevel;
};

struct ReverbExParam
{
	double mRoomSize;//0~100
	double mPreDelay;//0~200
	double mReverberance;//0~100
	double mHfDamping;//0~100
	double mToneLow;//0~100
	double mToneHigh;//0~100
	double mWetGain;//-20~10
	double mDryGain;//-20~10
	double mStereoWidth;//0~100
};

struct CprParam
{
	int threshold;
	int makeupGain;
	int ratio;
	int knee;
	int releaseTime;
	int attackTime;
};

const int kEqBandCount = 10;
typedef float EqGains[kEqBandCount];

enum REVERBPRESET {
	QUICKFIX = 0,
	SMALLHALL,
	NICEHALL,
	SEWER,
	CHURCH,
	SMALLROOM,
	KROK_MEDIUMHALL,
	KROK_BIGHALL,

	REVERBPRESET_NUM
};

const ReverbParam kReverbPreset[] =
{
	{ 40.0f, 4.0f, 0.9f, 0.75f, 0.0f, -22.0f, -28.0f },    //QuickFix
	{ 50.0f, 100.0f, 0.1f, 0.75f, -1.5f, -10.0f, -20.0f }, //SmallHall
	{ 40.0f, 20.0f, 0.5f, 0.75f, 0.0f, -10.0f, -30.0f },  //NiceHall
	{ 6.0f, 15.0f, 0.9f, 0.1f, -10.0f, -10.0f, -10.0f },  //Sewer
	{ 200.0f, 9.0f, 0.7f, 0.8f, -20.0f, -15.0f, -8.0f },  //Church  
	{ 190.0f, 3.0f, 0.8f, 0.8f, -8.0f, -25.0f, -51.0f },   // SmallRoom   
	{ 10.0f, 5.0f, 0.15f, 0.75f, 0.0f, -5.0f, -28.0f },    // Krok MediumHall
	{ 90.0f, 20.0f, 0.03f, 0.75f, -2.0f, -5.0f, -28.0f },  // Krok BigHall   
};

const ReverbExParam kReverbExPreset[] =
{
	{ 70, 20, 40, 99, 100, 50, -12, 0, 70 },    //Vocal I
	{ 50, 0, 50, 99, 50, 100, -1, -1, 70 }, //Vocal II
	{ 16, 8, 80, 0, 0, 100, -6, 0, 100 },  //Bathroom
	{ 30, 10, 50, 50, 50, 100, -1, -1, 100 },  //Small Room Bright
	{ 30, 10, 50, 50, 100, 0, -1, -1, 100 },  //Small Room Dark 
	{ 75, 10, 40, 50, 100, 70, -1, -1, 70 },   // Medium Room 
	{ 85, 10, 40, 50, 100, 80, 0, -6, 90 },    // Large Room
	{ 90, 32, 60, 50, 100, 50, 0, -12, 100 },  // Church Hall  
	{ 90, 16, 90, 50, 100, 0, 0, -20, 100 },  // Cathedral  
	{ 75, 10, 50, 50, 100, 100, -1, -1, 100 },  // Default  
};

enum DSP_TYPES{
	DSP_VOICECHANGE = 0,
	DSP_VOICECHANGE_EX = 1,
	DSP_REVERB = 2,
	DSP_CREVERBEX = 3,
	DSP_EREVERBEX = 4,
	DSP_EQUALIZER = 5,
	DSP_COMPRESSOR = 6,
	DSP_BEATTRACK = 7,
	DSP_ACCELERATE = 8,
	DSP_MUSICDETECT = 9,
};

class IDSP
{
public:
	virtual ~IDSP(){}

	virtual void Destroy() = 0;

	virtual int Process(char* pData, int nSampleCount, int nSampleRate, int nChannels, int nBitPerSample) = 0;

	virtual void Flush() = 0;

	virtual bool GetEnabled() = 0;

	virtual void SetEnabled(bool bEnabled) = 0;

	virtual bool IsActive() = 0;
};

class IVoiceChanger : public IDSP
{
public:
	virtual void SetSemitone(float val) = 0;
	virtual float GetSemitone() const = 0;
	virtual int GetAlgoId() const = 0;
};

class IReverb : public IDSP
{
public:
	virtual void SetReverbParam(ReverbParam reverbParam) = 0;
	virtual void SetReverbExParam(ReverbExParam reverbExParam) = 0;
	virtual void SetPreset(int mode) = 0;
};

class IBeatTracker : public IDSP
{
public:
	virtual int GetLastBeat() = 0;
	virtual int GetLastBPM() = 0;
	virtual float GetLastPower() = 0;
	virtual void SetDetectLevel(double k) = 0;
	virtual void SetPeriodTightness(double k) = 0;
	virtual void SetDelayDecision(int d) = 0;
};

class IMusicDetector : public IDSP
{
public:
	virtual int GetMusicState() = 0;//0,no 1,yes
	virtual void SetDetectThreshold(int th) = 0;
	virtual void SetTriggerMs(int trigger) = 0;
	virtual void SetLastMs(int last) = 0;
};

class IEqualizer : public IDSP
{
public:
	virtual void SetGain(int nBandIndex, float fBandGain) = 0;
};

class ICompressor : public IDSP
{
public:
	virtual void SetParam(int threshold, int makeupGain, int ratio, int knee, int releaseTime, int attackTime) = 0;
};

class IAudioAccelerate : public IDSP
{
public:
	virtual void SetTempoChange(double newtempo) = 0;
};

typedef std::vector<std::string*> AudioBlockList;

//only for the same samplerate and channels
class IAudioBlockMixer
{
public:
	virtual ~IAudioBlockMixer(){}
	virtual void Destroy() = 0;
	virtual bool Process(const AudioBlockList& inBlocks, std::string& outBlock) = 0;
};

class IAudioResamplerEx
{
public:
	virtual ~IAudioResamplerEx() {}
	// src_frames, dst_frames: per channel
	virtual bool Convert(const int16_t* pSrc, uint32_t srcSamples, int16_t* dst, uint32_t dstSamples) = 0;
	virtual bool IsFormatChange(uint32_t src_fs, uint32_t src_ch, uint32_t dst_fs, uint32_t dst_ch) = 0;
	virtual uint32_t GetDestSamples() = 0;
	virtual uint32_t GetSrcSamples() = 0;

	// don't support 22050, 10ms
	static IAudioResamplerEx* Create(uint32_t src_frames, uint32_t src_fs, uint32_t src_channels, uint32_t dst_frames, uint32_t dst_fs, uint32_t dst_channels, const char* create_place = NULL);
    static void Destroy(IAudioResamplerEx*& resampler);  // will Set NULL
};

enum DenoiseLevel
{
	DenoiseLevelNo    = 0,
	DenoiseLevelLight = 1,
	DenoiseLevelHeavy = 2, 
};

class IAudioDenoise
{
public:
	virtual ~IAudioDenoise(){}
	virtual void Destroy() = 0;
	virtual bool Process(int16_t* pData) = 0;//10ms
	virtual void EnableAGC(bool enable) = 0;
	virtual void EnableLightDenoise(bool enable) = 0;
	virtual void SetDenoiseLevel(DenoiseLevel level) = 0;
};

class IAudioLevel
{
public:
	virtual ~IAudioLevel() {}
	virtual void Reset() = 0;
	virtual bool ComputeLevel(const void* audioSamples, const uint32_t dataSize, const uint8_t nBytesPerSample) = 0;
	virtual int  GetLevel() = 0;
};

class IBeatTrackNotify
{
public:
	virtual ~IBeatTrackNotify(){}
	virtual void NotifyBeat(int bpm, float power) = 0;
	virtual void NotifyStartFive() = 0;
};

class IBeatTrack
{
public:
	virtual ~IBeatTrack(){}
	virtual void Destroy() = 0;
	virtual int Start(int mode, IBeatTrackNotify* pNotify, int delaydecision) = 0;
	virtual int Detectbeat(int16_t* sample, int size) = 0;
	virtual void Stop() = 0;
};

/*
	Speech Message Player 
*/
enum SPEECHMSG_PLAYER_STATUS {
	SPEECHMSG_PLAYER_ERROR_NONE = 0,
	SPEECHMSG_PLAYER_ERROR_FILE_OPEN = -1,
	SPEECHMSG_PLAYER_ERROR_HEADER_LENGTH = -2,
	SPEECHMSG_PLAYER_ERROR_UNKNOWN_CODEC = -3,
	SPEECHMSG_PLAYER_ERROR_DECODER_INIT = -4,
};

class ISpeechMsgPlayerNotify
{
public:
	virtual ~ISpeechMsgPlayerNotify(){}
	virtual void OnAudioPlayStatus(uint32_t nowPlayTime, uint32_t filePlayTime, uint32_t volume) = 0;
	virtual void OnAudioPlayError() = 0;
	virtual void OnFirstStartPlayData() = 0;
	virtual void OnStopPlayData(uint32_t nowPlayTime, uint32_t filePlayTime) = 0;
	virtual void OnReachMaxPlayTime(uint32_t nowPlayTime, uint32_t filePlayTime) = 0;
};

class ISpeechMsgPlayer
{
public:
	static ISpeechMsgPlayer* CreateAudioPlayer(const char* pFileName);

	virtual ~ISpeechMsgPlayer(){}
	virtual SPEECHMSG_PLAYER_STATUS Init() = 0;
	virtual void Start(ISpeechMsgPlayerNotify *pNotify) = 0;
	virtual void Stop() = 0;
	virtual void Destroy() = 0;
	virtual int  GetRecordFileTime() = 0;
	virtual void SetAudioSemitone(float val) = 0;
	virtual void SetReverbParam(ReverbParam reverbParam) = 0;
	virtual void SaveToFile(const char* pFileName) = 0;
};

/*
	Speech Message Recorder
*/
enum SPEECHMSG_RECORD_STATUS {
	SPEECHMSG_RECORD_ERROR_NONE          = 0,
	SPEECHMSG_RECORD_ERROR_UNKNOWN_CODEC = -1,
	SPEECHMSG_RECORD_ERROR_ENCODER_INIT  = -2,
	SPEECHMSG_RECORD_ERROR_FILE_OPEN_FAIL= -3,
};

enum SpeechMsgCodecType {
	SpeechMsgCodecUnknown  = -1,
	SpeechMsgCodecSilk     = 0,
	SpeechMsgCodecSpeex    = 1,
	SpeechMsgCodecEaacPlus = 2,
	SpeechMsgCodecLcAAC    = 3,
	SpeechMsgCodecWav      = 4,
};

class ISpeechMsgRecorderNotify
{
public:
	virtual ~ISpeechMsgRecorderNotify(){}
	virtual void OnAudioVolumeVisual(uint32_t recordTime, uint32_t volume) = 0;//0~100
	virtual void OnAudioRecordError() = 0;
	virtual void OnGetFirstRecordData() = 0;
	virtual void OnStopRecordData(uint32_t recordTime, uint32_t maxDuration) = 0;
	virtual void OnReachMaxDuration(uint32_t recordTime, uint32_t maxDuration) = 0;
};

class ISpeechMsgRecorder
{
public:
	static ISpeechMsgRecorder* CreateAudioRecorder(const char* pFileName, int uid, SpeechMsgCodecType codecType, const uint32_t maxDuration/*ms*/);

	virtual ~ISpeechMsgRecorder(){}
	virtual SPEECHMSG_RECORD_STATUS Init() = 0;
	virtual void Start(ISpeechMsgRecorderNotify* pNotify) = 0;
	virtual void Stop() = 0;
	virtual void Destroy() = 0;
};

/*  multi-channel pcm stream mixer, each stream length fixed to 20ms, reduce delay
 *  before call Process, each stream is put together, and add stream header
 *  each stream will be converted to dstFs,dstChannel, and be mixed to one stream
 *
 *	One Path Header Structure:
 *	|<-              Fs                   ->|<- channel->|<- precision ->|<-    Data length    ->|
 *	|<- 4th ->|<- 3th ->|<- 2th ->|<- 1th ->|<-  1byte ->|<-   1byte   ->|<- 1byte ->|<- 1byte ->|
 */
class IPushPcmModule
{
public:
	static IPushPcmModule* CreatePuchPcmModule(uint32_t dstFs, uint32_t dstChannel, uint32_t streamCount);
	static std::string MakeStreamInfo(uint32_t* fs_array, uint32_t* channel_array, uint32_t* precision_array, uint32_t* length_array, uint32_t streamCount);

	virtual ~IPushPcmModule() {}
	virtual bool Process(const uint8_t* pushData, const uint32_t dataSize, const uint32_t streamCount, const bool enDenoise, uint8_t* outData, uint32_t* outLen) = 0;
	virtual void Destroy() = 0;
};

enum VoiceEffectOption
{
	VeoNone = 0,
	VeoEthereal = 1,   // ¿ÕÁé
	VeoThriller = 2,   // ¾ªã¤
	VeoLuBan = 3,      // Â³°à
	VeoLorie = 4,      // ÂÜÀò
	VeoUncle = 5,      // ´óÊå
	VeoDieFat = 6,     // ËÀ·Ê×Ð
	VeoBadBoy = 7,     // ÐÜº¢×Ó
	VeoWarCraft = 8,   // Ä§ÊÞÅ©Ãñ
	VeoHeavyMetal = 9, // ÖØ½ðÊô
	VeoCold = 10,      // ¸ÐÃ°
	VeoHeavyMechinery = 11, // ÖØ»úÐµ
	VeoTrappedBeast = 12,   // À§ÊÞ
	VeoPowerCurrent = 13    // Ç¿µçÁ÷
};

enum VoiceBeautifyOption
{
	mode1 = 0,   // ÈáºÍ
	mode2 = 1,   // ÇåÎú
	mode3 = 2,   // ÎÂÅ¯
	mode4 = 3,   // ÌðÃÀ
	mode5 = 4,   // Ã÷ÁÁ
	mode6 = 5    // »ëºñ
};

class IVoiceChangerToolbox
{
public:
	// Note: only support mono now 
	static IVoiceChangerToolbox* Create(int sampleRate, int channels);

	virtual ~IVoiceChangerToolbox() {}
	virtual int Process(int16_t* inoutData, int16_t *nSamples) = 0;
	virtual bool SetVoiceEffectOption(VoiceEffectOption mode) = 0;
};

class IAudioVoiceBeautify : public IDSP
{
public:
	
	static IAudioVoiceBeautify* Create();

	virtual ~IAudioVoiceBeautify() {}
	virtual int VoiceBeautifyProcess(uint16_t* pData, int SampleCount, int SampleRate, int  Channels) = 0;
	virtual bool SetVoiceBeautifyOption(VoiceBeautifyOption mode,float pitch,float reverb) = 0;
};

IAudioBlockMixer* CreateAudioBlockMixer(int sampleCount, int channels);

IAudioDenoise* CreateDenoise(int sampleRate, int channels);

IAudioLevel* CreateAudioLevel(bool newModule = false);

//input and output are in wav format
//sample_rate_hz must be one of 8000 || 16000 || 32000 || 48000;
//return 0:success, 1:no accelerator, -1, error occur
//NOTICE: input_length MUST BE 30ms data
int AccelerateSoundProcess(uint32_t sample_rate_hz, uint32_t num_channels, const int16_t* input, uint32_t input_length, int16_t* output, uint32_t* output_length, bool fastMode = false);
int StretchSoundProcess(uint32_t sample_rate_hz, uint32_t num_channels, const int16_t* input, uint32_t input_length, int16_t* output, uint32_t* output_length);

IDSP* CreateAudioDSP(DSP_TYPES type);

IVoiceChanger* CreateVoiceChanger();

IReverb* CreateReverb();

IReverb* CreateCReverbEx();//MUST Process 10ms pData

IReverb* CreateEReverbEx();//MUST Process 10ms pData

IEqualizer* CreateEqualizer();

ICompressor* CreateCompressor();

IBeatTracker* CreateBeatTracker();

IBeatTrack* CreateBeatTrack(int samplerate, int channles);

IMusicDetector* CreateMusicDetector();

IAudioAccelerate* CreateAudioAccelerate();

#endif
