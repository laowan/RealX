#ifndef _CODEC_TYPE_H_
#define _CODEC_TYPE_H_

#include <vector>
#include <list>
#include "int_type.h"

const unsigned long dwASSEMBLE_CODEC_TYPE_BASE = 6;
const unsigned long dwCUSTOM_CODEC_TYPE_BASE = 20;
enum CodecRate{
	CODEC_NONE = -1,
	VOICE_MID = 0,//speex
	MUSIC_MID = 1,//aacplus
	VOICE_HIGHER = 2,//silk
	VOICE_HIGH = 3,//amrwb
	VOICE_HIGHER_JITTER = 4,
	MUSIC_MID_JITTER = 5,
	//麦序高音质
	VOICE_MID_AND_MUSIC_MID = dwASSEMBLE_CODEC_TYPE_BASE + 1,				//7,低音质为VOICE_MID，高音质为MUSIC_MID
	VOICE_HIGH_AND_MUSIC_MID = dwASSEMBLE_CODEC_TYPE_BASE + 2,				//8,低音质为VOICE_HIGH，高音质为MUSIC_HIGH
	VOICE_HIGHER_AND_MUSIC_MID_JITTER = dwASSEMBLE_CODEC_TYPE_BASE + 3,
	VOICE_HIGHER_AND_MUSIC_MID = dwASSEMBLE_CODEC_TYPE_BASE + 4,			//10,低音质为VOICE_HIGHER(16K)，高音质为MUSIC_MID
	VOICE_HIGHER_QUALITY1_AND_MUSIC_MID = dwASSEMBLE_CODEC_TYPE_BASE + 5,	//11,低音质为VOICE_HIGHER_QUALITY1(24k)，高音质为MUSIC_MID
	//自定义音质属性
	VOICE_LOW_FOR_MOBILE = dwCUSTOM_CODEC_TYPE_BASE + 1,
	VOICE_HIGHER_QUALITY1 = dwCUSTOM_CODEC_TYPE_BASE + 2,
	VOICE_LOW_FOR_MOBILE_QUALITY2 = dwCUSTOM_CODEC_TYPE_BASE + 3,
	MUSIC_MID_1_32KBR = dwCUSTOM_CODEC_TYPE_BASE + 4,
	MUSIC_MID_1_40KBR = dwCUSTOM_CODEC_TYPE_BASE + 5,
	MUSIC_MID_1_48KBR = dwCUSTOM_CODEC_TYPE_BASE + 6,
	MUSIC_MID_2_24KBR = dwCUSTOM_CODEC_TYPE_BASE + 7,
	MUSIC_MID_2_32KBR = dwCUSTOM_CODEC_TYPE_BASE + 8,
	MUSIC_MID_2_40KBR = dwCUSTOM_CODEC_TYPE_BASE + 9,
	MUSIC_MID_2_48KBR = dwCUSTOM_CODEC_TYPE_BASE + 10,
	MUSIC_MID_2_24KBR_VBR = dwCUSTOM_CODEC_TYPE_BASE + 11,
	MUSIC_MID_2_32KBR_VBR = dwCUSTOM_CODEC_TYPE_BASE + 12,
	MUSIC_MID_2_40KBR_VBR = dwCUSTOM_CODEC_TYPE_BASE + 13,
	MUSIC_MID_2_48KBR_VBR = dwCUSTOM_CODEC_TYPE_BASE + 14,
	MUSIC_AAC_44100HZ_STEREO_128KBPS = dwCUSTOM_CODEC_TYPE_BASE + 15,
	MUSIC_AAC_44100HZ_STEREO_160KBPS = dwCUSTOM_CODEC_TYPE_BASE + 16,
	MUSIC_AAC_44100HZ_STEREO_192KBPS = dwCUSTOM_CODEC_TYPE_BASE + 17,
	MUSIC_AACELD_44100HZ_STEREO = dwCUSTOM_CODEC_TYPE_BASE + 18,
	MUSIC_AACELDSBR_44100HZ_STEREO = dwCUSTOM_CODEC_TYPE_BASE + 19,
	MUSIC_AAC_48000HZ_STEREO = dwCUSTOM_CODEC_TYPE_BASE + 20,
	MUSIC_AAC_44100HZ_MONO = dwCUSTOM_CODEC_TYPE_BASE + 21,
	MUSIC_AAC_48000HZ_MONO = dwCUSTOM_CODEC_TYPE_BASE + 22,
	MUSIC_AACELD_16000HZ_MONO = dwCUSTOM_CODEC_TYPE_BASE + 23,
	MUSIC_AACELD_32000HZ_MONO = dwCUSTOM_CODEC_TYPE_BASE + 24
};

enum SampleRateMode//3bit
{
	eSampleRate8000Hz = 0,
	eSampleRate12000Hz,
	eSampleRate16000Hz,
	eSampleRate24000Hz,
	eSampleRate32000Hz,
	eSampleRate48000Hz,
	eSampleRate22050Hz,
	eSampleRate44100Hz
};

enum ChannelMode//1bit 
{
	eChannelMono = 0,
	eChannelStereo
};

enum CodecId//4bit
{
	eCodecPcm = 0,
	eCodecSpeex,
	eCodecAmrwb,
	eCodecSilk,
	eCodecEaacPlus,
	eCodecOpus,
	eCodecG711
};

typedef unsigned char CodecType;

#if defined(__APPLE__) && defined(__arm__)      
#define ARMV7Neon
#endif

enum AudioCodec
{
	kAudioCodecUnknown  = 0x00,
	kAudioCodecSpeex    = 0x01,
	kAudioCodecAMRWB    = 0x02,
	kAudioCodecSilk     = 0x04,
	kAudioCodecMP3      = 0x08,
	kAudioCodecAAC      = 0x10,
	kAudioCodecPCM      = 0x20,
	kAudioCodecAACHIGH  = 0x40,
	kAudioCodecOPUS     = 0x80,
	kAudioCodecEldAAC   = 0x100, 
};

enum AudioLibraryCodecMode
{
	kAudioLibraryDecoder = 0,
	kAudioLibraryEncoder = 1,
};

struct AudioStreamFormat
{
	AudioCodec iCodec;
	uint32_t iFlag;

	uint32_t iSampleRate;
	uint32_t iNumOfChannels;
	uint32_t iBitsOfSample;
	uint32_t iBitRate;

	/// how many decoded samples per channel in one audio frame. always 1 for PCM.
	/// may not be zero for non-PCM codec.
	/// for example: EAAC 2 channels, 44.1KHz, iSamplesOfFrame = 2048, 46ms / per frame.
	uint32_t iSamplesOfFrame;

	/// ignored for PCM.
	/// for some codec, like AAC, profile means the encoder's capabilities.
	/// AAC profile:(AAC:1, EAAC:5, AAC_ADTS:101, EAAC_ADTS:105)
	int iProfile;

	/// ignored for PCM.
	/// for some codec, like SPEEX AMRWB AAC, profile means the encoder's qulity(0-10).
	int iQuality;

	/// ignored for PCM.
	/// for some codec, like Speex/Silk, they have constant encoded bitrate for one frame.
	int iBytesOfCodedFrame;

	uint32_t iNumOfFramesInPacket; //how many audio data will be send to encoder once

	int iRawCodecId;

	unsigned GetFrameDuration()
	{
		return (iSamplesOfFrame * 1000 / iSampleRate);
	}

	static bool IsSupportedSampleRate(uint32_t samplerate)
	{
		static const uint32_t supported[] = { 8000, 11025, 16000, 22050, 24000, 32000, 44100, 48000 };
		for (uint32_t i = 0; i < sizeof(supported) / sizeof(supported[0]); ++i)
		if (samplerate == supported[i])
			return true;
		return false;
	}

	static bool IsSupportedSampleBits(uint32_t bits)
	{
		return bits == 8 || bits == 16 || bits == 32;
	}

	uint32_t GetBytesOfInterleavedSample() const
	{
		return iNumOfChannels * iBitsOfSample / 8;
	}

	static bool IsPCMFormatEquals(const AudioStreamFormat &format1, const AudioStreamFormat &format2)
	{
		bool ret = true;
		if (format1.iFlag != format2.iFlag || format1.iNumOfChannels != format2.iNumOfChannels ||
			format1.iBitsOfSample != format2.iBitsOfSample || format1.iSampleRate != format2.iSampleRate)
		{
			ret = false;
		}
		return ret;
	}
};

class CAudioCodec
{
public:
	virtual ~CAudioCodec(){};
	/*
		nProfile  :
		1.kAudioCodecSpeex (nProfile = 0 -> SPEEX_MODEID_NB
		nProfile = 1 -> SPEEX_MODEID_WB
		nProfile = 2 -> SPEEX_MODEID_UWB)
		*/
	virtual bool Init(AudioStreamFormat* audioFormat) = 0;

	/*
		nInputMaxSize:
		*nOutBufferMaxSize: some frame need buflen
		if nFrameSize is zero, *nOutBufferMaxSize returned the buffer size for 8 frames.
		ret: 0 OK
		*/
	virtual int  CalcBufSize(int *nOutBufferMaxSize, int nFrameSize) = 0;

	/*
		pIn     :  when pIn = NULL recovering one frame
		nInLen  :
		pOut    :
		nOutLen :
		ret     : <0 err ; >=0 consumed data len
		*/
	virtual int  Process(const unsigned char* pIn, int nInLen, unsigned char* pOut, int* nOutLen) = 0;

	//0: decoder 1: encoder
	virtual int  CodecMode() = 0;
	virtual int  CodecID() = 0;
	virtual int  CodecLevel() = 0;
	virtual void DeInit() = 0;
	virtual const char* CodecDescribe() { return ""; }
	virtual bool IsAvailable() { return true; }
	virtual int CodecDelay() { return 0; }   // return ms 
	virtual int GetEncoderSize() { return 0; }
};

enum AudioLibError
{
	kAudioErrNone = 0,        // success.
	kAudioErrUnknown = -1000,
	kAudioErrArgument,
	kAudioErrNoImpl,
	kAudioErrNotActived,
	kAudioErrNoAddress,
	kAudioErrNotAvailable,
	kAudioErrNotInit,
	kAudioErrNoLink,
	kAudioErrNotSupported,
	kAudioErrAudioDevice,
	kAudioErrAudioMixer, //-990
	kAudioErrAudioProcessor,
	kAudioErrAudioFmt,
	kAudioErrAudioSession,
	kAudioErrAudioCategory,
	kAudioErrAudioResample,
	kAudioErrAudioProperty,
	kAudioErrAudioDecoder,
	kAudioErrAudioEncoder,
	kAudioErrVideoDevice,
	kAudioErrVideoEncoder, //-980
	kAudioErrNoPermission,
	kAudioErrTimeout,
	kAudioErrClosed,
	kAudioErrSocket,
	kAudioErrDuplicate,
	kAudioErrNoCodec,
	kAudioErrNoDevice,
	kAudioErrNotOpened,
	kAudioErrNotStarted,
	kAudioErrNotFound, //-970
	kAudioErrNoRoom,
	kAudioErrNoData,
	kAudioErrAudioInput,
	kAudioErrAudioOutput,
	kAudioErrSampleRate,
	kAudioErrIndex,
	kAudioErrAlready,
	kAudioErrState,
	kAudioErrNotConnected,
	kAudioErrFatal, //-960
	kAudioErrCookie,
	kAudioErrTimestamp,
	kAudioErrLate,
	kAudioErrMemory,
	kAudioErrFile,
	kAudioErrHint,
	kAudioErrId,
	kAudioErrSubView,
	kAudioErrVideoDecoder,
	kAudioErrInProgress,
	kAudioErrGPUFilter,///EK 2015-9-9 USING_GPU_PROC_FOR_VIDEO_STREAM
};

enum AudioProcessorParamName
{
	// get/set current enabled filters.
	// param - AudioFilter
	kAudioProcessorParamFilter = 1,

	// get/set filter's param
	// param - AudioFilterParam
	// to get filter's param, set iType in input
	kAudioProcessorParamFilterParam = 2,

	// get output audio format
	// param - AudioStreamFormat
	kAudioProcessorParamOutputFormat = 3,

	// get input audio format
	// param - AudioStreamFormat
	kAudioProcessorParamInputFormat = 4,

	// only for encoder to get the input frame volume of the last encoded frame. read-only.
	// param - float.
	kAudioProcessorEncodeFrameVolume = 5,

	// get encode process count
	kAudioProcessorEncodeCount = 6,

	// get encode process succeed count
	kAudioProcessorEncodeSucceedCount = 7,

	// get decode process count
	kAudioProcessorDecodeCount = 8,

	// get encode process succeed count
	kAudioProcessorDecodeSucceedCount = 9,

	// get codec id
	kAudioProcessorCodecId = 10,

	// get stream uid
	kAudioProcessorStreamUid = 11,
};

enum AudioProcessorProcessOption
{
	kAudioProcessorOptionNone = 0,

	// recover number of empty frames out, inLength is the number of frames.
	kAudioProcessorOptionRecover = 1,
};

/// NOTICE : the max decoded/encoded voice data duration is 100ms per calling ProcessData. Otherwise, unexpected exception occurred.
#define MaxDurationOfAudioProcessData   (100)
class AudioProcessor
{
public:

	/// create a processor with initialized input/output format and the filter type to be enabled.
	/// the filter parameter is initialized to default value.
	static int Create(const AudioStreamFormat &inputDesc, const AudioStreamFormat &outputDesc, AudioProcessor*& pProcessor);
	static void Release(AudioProcessor *&processor);

	/// get platform's supported for codecs.
	static int GetSupportedCodec();     // return enum AudioCodec

	/// reset the internal buffer, not changed for filter type and its parameters.
	virtual void Reset() = 0;

	// 1. if outData is null, return how many bytes needed outside to outLength.
	// 2. outData is same with inData, process inplace.
	// 3. when kAudioProcessorOptionRecover is set on option, inData is ignored, inLength is the number of frames to recover.
	// return kErrNone if processing suc, otherwise, no data is processed.
	// inLength - input: length of inData; output: length of consumed inData.
	virtual int ProcessData(void *inData, uint32_t *inLength, void *outData, uint32_t *outLength, uint32_t uid = 0, 
		int option = kAudioProcessorOptionNone) = 0;

	static int GetDataFrameLength(AudioStreamFormat& infmt, void *data, uint32_t *outLength);

	virtual int GetParameter(uint32_t name, void *value) const = 0;
	virtual int SetParameter(uint32_t name, void *value) = 0;
	virtual int CodecDelay() { return 0; }

protected:
	AudioProcessor();
	virtual ~AudioProcessor();
};

enum AudioFormatFlag
{
	kAudioFmtFlagNone = 0,

	/// if no data type is set, the default sample is of signed integer.
	kAudioFmtFlagFloat = 1,
	kAudioFmtFlagUnsignedInteger = 2,
	kAudioFmtFlagDataTypeMask = 3,

	/// by default, the samples are interleaved.
	/// !!! reserved, we only support interleaved samples in platform for now !!!
	kAudioFmtFlagNotInterleaved = 4,
};

#endif
