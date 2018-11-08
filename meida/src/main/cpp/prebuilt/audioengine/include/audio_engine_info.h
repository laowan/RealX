#ifndef _AUDIO_ENGINE_INFO_H_
#define _AUDIO_ENGINE_INFO_H_

#include <string>

struct AudioRecordInfo {
	int isRecording;
	int recordingThreadCount;
	int voiceActiveCount; // not use
	int maxDataEnergy;
};
struct AudioPlayoutInfo {
	int isPlayout;
	int playoutThreadCount;
	int maxDataEnergy;
};
struct AudioPeripheralInfo {
	bool isHeadSetPlugin;
	bool isBluetoothConnect;
	bool isPhoneCallIn;
	bool isLoudSpeakerOn;
};
//current may not correct
struct AudioPreProcessInfo {
	bool isAECOn;
	bool isAGCOn;
	bool isNSOn;
};
struct AudioDeviceConfigInfo {
	bool isOpenSLESOn;
	bool isSamsungSDKOn;
};
struct AudioEncodeInfo {
	int codecID;
	int processCount;
	int processSucceedCount;
};
struct AudioDecodeInfo {
	int codecID;
	int processCount;
	int processSucceedCount;
};
#define MAX_DECODE_INFO_LIST 20
struct AudioDecodeInfoList {
	int m_size;
	AudioDecodeInfo m_audDecodeInfoArray[MAX_DECODE_INFO_LIST];
	AudioDecodeInfoList() : m_size(0) {
		memset((char*)&m_audDecodeInfoArray[0], 0, (MAX_DECODE_INFO_LIST * sizeof(AudioDecodeInfo)));
	}

	bool empty() const {
		return (m_size == 0);
	}

	void clear() {
		m_size = 0;
		memset((char*)&m_audDecodeInfoArray[0], 0, (MAX_DECODE_INFO_LIST * sizeof(AudioDecodeInfo)));
	}

	bool push_back(AudioDecodeInfo &audDecInfo) {
		if (m_size >= MAX_DECODE_INFO_LIST) {
			return false;
		}
		m_audDecodeInfoArray[m_size % MAX_DECODE_INFO_LIST] = audDecInfo;
		++m_size;
		return true;
	}
};
#endif