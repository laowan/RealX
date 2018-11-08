
#ifndef FFMMUXER_H
#define FFMMUXER_H

#include "../x264/Mediabase.h"
#include "../x264/Macros.h"
#include <jni.h>
#include "FfmMediaFormat.h"
#include <string>

extern "C"
{
    #include <libavformat/avformat.h>
	#include "libavutil/fifo.h"
	#include "libavutil/pixfmt.h"
	#include "libavutil/rational.h"
	#include "libavutil/threadmessage.h"

	typedef struct H264MuxerHandler  H264MuxerHandler;
}


NAMESPACE_YYMFW_BEGIN

class FfmMuxer {

public:
    FfmMuxer(std::string& path);
    ~FfmMuxer();

public:
    int addStream(std::string&  mediaformatStr);
    void start();
    void stop();
    void writeSampleData(int streamID, const char* data, int size,  bool keyFlag,   int64_t pts,  int64_t dts);
    void setMeta(std::string& metaData);

private:
    int addVideoStream(FfmMediaFormat&  mediaformat);
    int addAudioSteam(FfmMediaFormat&  mediaformat);

	static void* mux_thread(void* arg);

public:
	bool mStop;

private:
    std::string     mFileName;
	std::string 	mVideoSps;
	std::string 	mVideoPps;
    std::string     mMetaData;

    //AVOutputFormat  *mOutFormat;
    //AVFormatContext *mFormatContext;

    //AVStream        *mVideoStream;
    //AVStream        *mAudioStream;
	AVThreadMessageQueue * 	mMuxMessageQueue;
    pthread_t 				mThreadId;           /* thread reading from this file */

	H264MuxerHandler*		mMuxerHandler;

	
	int mMaxQueueSize;
	int mVideoStreamId;
	int mAudioStreamId;

	bool mVideoSEI;

	int mVideoSize;
	int mWriteSize;
	
};


NAMESPACE_YYMFW_END

#endif



