#ifndef _AUDIO_ENGINE_IMPL_H_
#define _AUDIO_ENGINE_IMPL_H_

#include "int_type.h"
#include "codec_type.h"
#include "audio_jitter_buffer_impl.h"
#include "audio_toolbox_impl.h"

typedef void (*LogCallback)(const char* pLogStr);

typedef struct
{
	uint8_t frameData[1024];
	uint16_t frameLength;
	uint32_t timestamp;
	uint8_t ssrc;
	uint32_t frameIndex;
	uint8_t codecType;
	uint8_t keyFrame;
	uint8_t discard;
} AudioFrameData;

typedef struct 
{
	uint8_t volumeEnhence;
	uint8_t aecMode; //nlpmode aecm also in it
	uint8_t nsMode;
	uint8_t agcMode;
} AudioProperConfig;

enum VADResultKind 
{
	vadResultIsActive = 0,
	vadResultIsPassive = 1,
	vadResultIsUnknown = 2,
	vadResultIsActiveWithoutVoice = 3
};

enum AudioSourceType
{
	AudioSourceTypeMic = 0,
	AudioSourceTypeOuterPCM,
	AudioSourceTypeMix, // mic and outer-pcm
};

enum AudioEngineFeature_t
{
	AudioEngineFeatureVOIP           = 0,
	AudioEngineFeatureCommon         = 1,
	AudioEngineFeatureSpeechMsg      = 2,
	AudioEngineFeatureLiveBroadcast  = 3,
	AudioEngineFeatureHDVOIP         = 4,
	TotalFeatureMode                 = 5,
};

enum VoiceDetectionMode
{
	voiceDetectionUnknown = -1,
	voiceDetectionVeryLowLikelihood = 0,
	voiceDetectionLowLikelihood,
	voiceDetectionModerateLikelihood,
	voiceDetectionHighLikelihood
};

enum AudioSaverMode
{
	AudioSaverOnlyCapture = 0,
	AudioSaverOnlyRender  = 1, 
	AudioSaverBoth        = 2,
};

enum AppCommonEvent
{
    AppForegroundEvent,
    AppBackgroundEvent,
    AppScreenLockedEvent,
    AppScreenUnlockedEvent
};

class IAudioCaptureNotify 
{
public:
	virtual ~IAudioCaptureNotify() {}
	virtual void OnCaptureAudioData(const void* audioSamples, const uint32_t dataSize, const uint8_t nBytesPerSample, 
		const VADResultKind vadResultKind) = 0;
	virtual bool OnCaptureVolumeVisual(const uint32_t& volume) = 0;
};

class IAudioRenderNotify
{
public:
	virtual ~IAudioRenderNotify() {}
	virtual bool OnRenderAudioData(void* audioSamples, const uint32_t dataSize, const uint8_t nBytesPerSample, const uint16_t playoutDelayMS = 0) = 0;
	virtual void OnRenderAudioFormatChange(uint32_t& newSampleRate, uint32_t& newChannels) = 0;
	virtual bool OnAudioVolumeData(const uint32_t& volume) = 0;
};

class IAudioCapture
{
public:
	virtual ~IAudioCapture(){}
	virtual void Destroy() = 0;
	virtual bool Open(uint32_t sampleRate, uint32_t channels, uint32_t bps) = 0;
	virtual void Start(IAudioCaptureNotify* pNotify) = 0;
	virtual void Stop() = 0;
};

class IAudioRender
{
public:
	virtual ~IAudioRender(){}
	virtual void Destroy() = 0;
	virtual bool Open(uint32_t sampleRate, uint32_t channels, uint32_t bps, uint32_t fadeInMS = 0) = 0;
	virtual void Start(IAudioRenderNotify* pNotify) = 0;
	virtual void Stop() = 0;
	virtual void SetVolume(uint32_t volume) = 0;
	virtual void SetMute(bool mute) = 0;   // only mute render speaker, have no effect on inner volume display 
};

class IAudioFramePackerNotify 
{
public:
	virtual ~IAudioFramePackerNotify() {}
	virtual void OnPackAudioFrame(const uint8_t* pEncodeFrame, const uint32_t encodeFrameSize,
		const VADResultKind vadResultKind, uint32_t timestamp, uint8_t ssrc) = 0;
    virtual void OnAudioCaptureVolume(const uint32_t volume) = 0;
};

struct FramePlayerFeedbackInfo
{
	FramePlayerFeedbackInfo()
	{
		acceleratePlay = false;
		fasterPlayOn = false;
		stretchPlay = false;
		accelerateMS = 0;
		stretchMS = 0;
		playDelayMS = 0;
	}

	// read & write
	bool acceleratePlay;
	bool fasterPlayOn; // only effect when acceleratePlay is true 
	bool stretchPlay;

	// only read 
	double accelerateMS;
	double stretchMS;
	double playDelayMS;
};

class IAudioFramePlayerNotify 
{
public:
	virtual ~IAudioFramePlayerNotify() {}
	virtual bool OnPullAudioFrame(uint32_t id, AudioFrameData& frame, bool forcePull, FramePlayerFeedbackInfo& feedbackInfo) = 0;
    virtual bool OnAudioVolume(const uint32_t volume) = 0;  // TODO: add stream id
};

class IAudioChannel
{
public:
	virtual ~IAudioChannel(){}
	virtual void Destroy() = 0;

	virtual bool StartPacker(IAudioFramePackerNotify* pNotify, int codecType) = 0;
    virtual bool OpenCapture() = 0;
	virtual void StopPacker() = 0;
    virtual void EnablePackerVad(bool enable) = 0;
	virtual bool SetVolume(uint32_t volume) = 0;
    virtual bool PushPcmData(const char* audioSamples, const uint32_t dataSize, const uint32_t timeStamp, const VADResultKind vadResultKind) = 0;

	virtual bool StartPlay(IAudioFramePlayerNotify* pNotify, uint32_t id, uint32_t fs, uint32_t ch) = 0;
	virtual void StopPlay(uint32_t id) = 0;
    virtual bool SetPlayVolume(uint32_t id, uint32_t volume) = 0;
    virtual bool IsPlayStarted(uint32_t id) = 0;
    
    static IAudioChannel* Create();
};

class IAudioFramePacker
{
public:
	virtual ~IAudioFramePacker() {}
	virtual void Destroy() = 0;

	virtual bool Start(IAudioFramePackerNotify* pNotify, int codecType) = 0;  // TODO: need other parameter, can't distinguish bitrate
	virtual bool SetVolume(uint32_t volume) = 0;
    virtual void OpenCapture() = 0; // packer source from capture
	virtual void Stop() = 0;
    virtual void EnableVad(bool enable) = 0;
    virtual void PushPcmData(const char* ptr, const uint32_t length, const uint32_t timeStamp, const VADResultKind vadResultKind) = 0;
    
    static IAudioFramePacker* Create();
};

class IAudioFramePlayer
{
public:
	virtual ~IAudioFramePlayer(){}
	virtual void Destroy() = 0;

	virtual bool Start(IAudioFramePlayerNotify* pNotify, uint32_t id, uint32_t fs, uint32_t ch) = 0;
	virtual void Stop() = 0;
    virtual void SetPlayVolume(uint32_t volume) = 0;
    virtual bool IsPlayStarted() = 0;
    
    static IAudioFramePlayer* Create();
};

class IAudioWizard
{
public:
	virtual ~IAudioWizard(){}
	virtual bool Init() = 0;
	virtual void Uninit() = 0;
	virtual void Destroy() = 0;
	virtual bool StartSpeakerWizard(const char* pFullPathFileName) = 0;
	virtual void StopSpeakerWizard() = 0;
	virtual bool StartMicrophoneWizard() = 0;
	virtual void StopMicrophoneWizard() = 0;
};

class IAudioPreview
{
public:
	virtual ~IAudioPreview(){}
	virtual bool Init() = 0;
	virtual void Uninit() = 0;
	virtual void Destroy() = 0;
	virtual bool StartPreview() = 0;
	virtual void StopPreview() = 0;
	virtual void SetVoiceEffectOption(VoiceEffectOption mode) = 0;
	virtual void SetVoiceBeauify(VoiceBeautifyOption mode, float pitch, float reverb) = 0;
	virtual void SetVoiceBeauifyEnable(bool enable) = 0;
};

enum AudioDeviceErrorType
{
	audioDeviceInitError,
	audioDeviceRuntimeError,
    audioDevicePermissionError,
};

class IAudioEngineNotify
{
public:
	virtual ~IAudioEngineNotify() {}
	virtual void OnAudioCaptureError(AudioDeviceErrorType errorType) = 0;
	virtual void OnAudioRenderError(AudioDeviceErrorType errorType) = 0;
	virtual void OnReceivePhoneCall(bool isInCall) = 0;
	virtual void OnAudioModeChange() = 0;
	virtual void OnHeadsetPlug(bool enable) = 0;
};

class IAudioFilePlayerNotify
{
public:
	virtual ~IAudioFilePlayerNotify() {}
	virtual void OnAudioPlayEnd() = 0;
};

class IAudioFilePlayer
{
public:
	virtual ~IAudioFilePlayer(){}
	virtual void Destroy() = 0;
	virtual bool Open(const char* pFileName) = 0;
	virtual uint32_t Seek(uint32_t timeMS) = 0;
	virtual uint32_t GetTotalPlayLengthMS() = 0;
	virtual void Play() = 0;
	virtual void Stop() = 0;
	virtual void Pause() = 0;
	virtual void Resume() = 0;
	virtual uint32_t GetCurrentPlayTimeMS() = 0;
	virtual bool SetFeedBackToMicMode(AudioSourceType type) = 0;
	virtual void SetPlayerNotify(IAudioFilePlayerNotify *nofity) = 0;
	virtual void SetPlayerVolume(uint32_t volume) = 0;
	virtual void EnableEqualizer(bool enable) = 0;
	virtual void SetEqualizerGain(int nBandIndex, float fBandGain) = 0;	
	virtual void SetSemitone(float val) = 0;
	virtual void SetKaraokeMixGain(float gain) = 0;   // will be removed future
};

class IAudioEngine
{
public:
	virtual ~IAudioEngine(){}

	virtual void Destroy() = 0;

	virtual void Start(AudioEngineFeature_t AudioEngineFeature) = 0;
	virtual void SetMode(AudioEngineFeature_t audioEngineFeature) = 0;
	virtual void Stop() = 0;
	virtual void Reset() = 0;
    virtual bool IsStarted() = 0;
    
	virtual void SetAudioProperConfig(const AudioProperConfig& config) = 0;

	virtual void AddAudioEngineNotify(IAudioEngineNotify* pNotify) = 0;
	virtual void RemoveAudioEngineNotify(IAudioEngineNotify* pNotify) = 0;

	virtual IAudioCapture* CreateAudioCapture() = 0;
	virtual IAudioRender* CreateAudioRender() = 0;
	virtual IAudioFilePlayer* CreateAudioFilePlayer() = 0;
	virtual IAudioWizard* CreateAudioWizard() = 0;
	virtual IAudioPreview* CreateAudioPreview() = 0;

	virtual void StartAudioPreview() = 0;	
	virtual void StopAudioPreview() = 0;
	virtual bool SetSpeakerVolume(uint32_t volume) = 0; 
	virtual uint32_t GetSpeakerVolumeRange() = 0;
	virtual uint32_t GetSpeakerVolume() = 0;
	
	virtual bool SetMicrophoneVolume(uint32_t volume) = 0; //0~100
	virtual bool SetLoudspeakerStatus(bool enable) = 0; //for mobile
	virtual bool GetLoudspeakerStatus() = 0;
	virtual bool SetVirtualSpeakerVolume(uint32_t volume) = 0; //0~100
	virtual bool SetVirtualMicVolume(uint32_t volume) = 0; //0~100

	virtual void SetAudioSourceType(AudioSourceType sourceType) = 0;
	virtual void PushOuterAudioData(const char* dataBlock, int dataSize, int sampleRate, int channel) = 0; // 10ms 

	virtual void EnableAutoGainControl(bool enable) = 0; 
	virtual void ResetPreProc() = 0;
	virtual void SetVoiceDetectionMode(VoiceDetectionMode mode) = 0;

	virtual void SetVeoMode(VoiceEffectOption val) = 0;

	virtual void SetNewNsOn(bool enable) = 0;

	virtual void SetVoiceBeauify(VoiceBeautifyOption val, float pitch, float reverb) = 0;
	virtual void SetVoiceBeauifyEnable(bool enable) = 0;

	virtual void EnableReverb(bool enable) = 0;
	virtual void SetReverbParam(ReverbParam reverbParam) = 0;
	virtual void SetReverbPreset(int mode) = 0;
	virtual void EnableReverbEx(bool enable) = 0;
	virtual void SetReverbParamEx(ReverbExParam reverbParam) = 0;

	virtual void SetEqGains(EqGains gains) = 0;
	virtual void SetCompressorParam(CprParam parameter) = 0;

	virtual void StartLogger(const char* path) = 0;
	virtual void EventLog(const char* log) = 0;
	virtual void StopLogger() = 0;
	virtual bool SetPlaybackModeOn(bool enable) = 0;
	virtual bool SetBuildInMicLocation(int location) = 0;
	virtual bool RecoverAudioMode() =  0;

	virtual void EnablePhoneAdaptation(bool enable) = 0;
	virtual void EnableMicrophone(bool enable) = 0;
	virtual void EnableRenderVolumeVisual(bool enable) = 0;
	virtual void EnableCaptureVolumeVisual(bool enable) = 0;

	virtual void EnableDenoise(bool enable) = 0;

	virtual bool StartAudioSaver(const char* fileName, AudioSaverMode saverMode = AudioSaverOnlyCapture) = 0;
	virtual bool StopAudioSaver() = 0;
	virtual bool CheckPhoneCallState() = 0;
	virtual bool GetHeadSetMode() = 0;
    // Only For Ios
    virtual void NotifyAppCommonEvent(AppCommonEvent appEvent) = 0;
	virtual bool GetAudioCaptureAndEncodeHiidoStatInfo(char*& pCaptureAndEncodeInfo, int& stringLen) = 0;
	virtual bool GetAudioRenderHiidoStatInfo(char*& pRenderInfo, int& stringLen) = 0;
	virtual bool GetAudioStreamDecodeHiidoStatInfo(uint32_t uid, char*& pDecoderInfo, int& stringLen) = 0;

	virtual void EnableReleaseWhenCloseMic(bool enable) = 0;
    virtual void EnableStereoPlayWhenHeadsetIn(bool enable) = 0;
};

IAudioEngine* CreateAudioEngine(const char* arg, int arglen, const char* logPath = NULL, LogCallback logFunc = NULL);

void SetAndroidAudioDeviceObjects(void* javaVM, void* env, void* context, bool useInYCSdk = true);//must be call before CreateAudioEngine

AudioEngineFeature_t GetAudioEngineFeature();

const char* GetSdkVersion();

void RunTestCase();

void OutputDebugInfo(const char* format, ...);

uint32_t GetExactTickCount();

int ConvertFlvAudioObjectType(uint8_t audioObjectType, uint8_t sampleRate, uint8_t channels);

#endif
