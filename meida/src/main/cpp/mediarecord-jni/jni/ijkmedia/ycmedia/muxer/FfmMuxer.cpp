#include "FfmMuxer.h"

#include "../ffmpeg_h264_muxer_mp4.h"
#include "x264/Common.h"
#include "x264/CommonUtils.h"


USING_NAMESPACE_YYMFW;

FfmMuxer::FfmMuxer(std::string& filename)
{
    /* Initialize libavcodec, and register all codecs and formats. */
    mFileName = filename;
    mMetaData = "";

	mMuxerHandler = h264MuxerInitOutputPath(filename.c_str(), filename.size());

	mMaxQueueSize = 128;  //cache 64 packet.
	mStop = false;

	mVideoStreamId = 1;
	mAudioStreamId = 2;

	mVideoSize = 0;
	mWriteSize = 0;

	mVideoSEI = true;
	
    //av_register_all();

    //avformat_alloc_output_context2(&mFormatContext, NULL, "mpeg", mFileName.c_str());
    //mOutFormat = mFormatContext->oformat;

    //mVideoStream = NULL;
    //mAudioStream  = NULL;
}

FfmMuxer:: ~FfmMuxer()
{
    //delete all object.
    /*
    if(!(mOutFormat->flags & AVFMT_NOFILE)) {
        avio_closep(&mFormatContext->pb);
    }
     avformat_free_context(mFormatContext);
     */
}

int FfmMuxer::addStream(std::string&  mediaformatStr)
{
    FfmMediaFormat mediaformat;
    mediaformat.initMediaFormat(mediaformatStr);

	std::string unkonw("unknown");
	std::string mediaType = mediaformat.getStringValue(FfmMediaFormat::KEY_MEDIA_TYPE, unkonw);

	LOGD("[ffmux], addStream:%s", mediaType.c_str());
	
	if(mediaType == "video") {
			
		//sps, pps	
		//return addVideoStream(mediaformat);

		int helelo = 0;
		int width = mediaformat.getIntValue(FfmMediaFormat::KEY_WIDTH, 544);
		int height = mediaformat.getIntValue(FfmMediaFormat::KEY_HEIGHT, 960);
		int frameRate = mediaformat.getIntValue(FfmMediaFormat::KEY_FRAME_RATE, 30);
		int biteRate = mediaformat.getIntValue(FfmMediaFormat::KEY_BIT_RATE, 2500000); //2.5M

		std::string defStr("");
		mVideoSps = mediaformat.getStringValue(FfmMediaFormat::KEY_AVC_SPS, defStr);
		mVideoPps = mediaformat.getStringValue(FfmMediaFormat::KEY_AVC_PPS, defStr);

		LOGD("[ffmux], spsSize=%d, ppsSize=%d, frameRate=%d, bitRate=%d", mVideoSps.size(), mVideoPps.size(), frameRate, biteRate);

		h264AddVideoTrack(mMuxerHandler, biteRate, width, height, frameRate, mVideoSps.c_str(), mVideoSps.size(), mVideoPps.c_str(), 
		        mVideoPps.size(), mMetaData.c_str());
		
		return mVideoStreamId;
	}
	//TODO. add audio specifcation....
	else if(mediaType=="audio") {
	
		//return addAudioSteam(mediaformat);

		return mAudioStreamId;
	}

	return -1;
}


int FfmMuxer::addVideoStream(FfmMediaFormat&  mediaformat)
{
       // mVideoStream = avformat_new_stream(mFormatContext, NULL);

		//h264..
		//mVideoStream.codecpar.codec_id =
		/*
		mVideoStream->codecpar->bit_rate = mediaformat.getValue(FfmMediaFormat::KEY_BIT_RATE, 1000);
		mVideoStream->codecpar->width = mediaformat.getValue(FfmMediaFormat::KEY_WIDTH, 0);
		mVideoStream->codecpar->height = mediaformat.getValue(FfmMediaFormat::KEY_HEIGHT, 0);
		mVideoStream->codecpar->codec_type = AVMEDIA_TYPE_VIDEO;

		mVideoSps = mediaformat.getvalue(FfmMediaFormat::KEY_AVC_SPS, "");
		mVideoPps = mediaformat.getValue(FfmMediaFormat::KEY_AVC_PPS, "");

		//parse pps, sps and cache it, then write into files.
	
		return mVideoStream.id;
			*/
}

int FfmMuxer::addAudioSteam(FfmMediaFormat&  mediaformat)
{
		//mAudioStream= avformat_new_stream(mFormatContext, NULL);


    /*
  	mAudioStream->codecpar->codec_type = AVMEDIA_TYPE_AUDIO;
		mAudioStream->codecpar->codec_id = ff_aac_encoder;

		//TODO. take care the audio bit_rate.
		mAudioStream->codecpar->bit_rate = mediaformat.getValue(FfmMediaFormat::KEY_BIT_RATE, 64000);

		mAudioStream->codecpar->sample_rate = mediaformat.getValue(FfmMediaFormat::KEY_SAMPLE_RATE, 44100);
		mAudioStream->codecpar->channels = mediaformat.getValue(FfmMediaFormat::KEY_CHANNEL_COUNT, 1);
		mAudioStream->codecPar->channel_layout = (uint64_t)mediaformat.getValue(FfmMediaFormat::KEY_CHANNEL_MASK, AV_CH_LAYOUT_STEREO);
	
		return mAudioStream.id;
			*/
}


static void* FfmMuxer::mux_thread(void* arg)
{
	FfmMuxer* thiz = (FfmMuxer*)arg;

	while(true) {

		AVPacket pkt;

		//LOGD("[ffmux] before queue recv");
		int ret = av_thread_message_queue_recv(thiz->mMuxMessageQueue, &pkt, 0);
		//LOGD("[ffmux] after queue recv");
		if(ret >=0) {
			bool keyFrame = (pkt.flags & AV_PKT_FLAG_KEY != 0);

			if(pkt.stream_index == thiz->mVideoStreamId) {
					h264MuxerWriteVideo(thiz->mMuxerHandler, pkt.buf->data, pkt.buf->size,  keyFrame,
										thiz->mVideoSps.c_str(), thiz->mVideoSps.size(), thiz->mVideoPps.c_str(), thiz->mVideoPps.size(), pkt.pts, pkt.dts);

					//LOGD("[ffmux] FfmMuxer::write video [write thread]  dataSize=%d, key=%d, pts=%lld, totalSize=%d", pkt.buf->size, keyFrame?1:0, pkt.pts, thiz->mWriteSize);
			} else if(pkt.stream_index == thiz->mAudioStreamId) {
					//LOGD("[ffmux] FfmMuxer::write audio [write thread]  dataSize=%d, key=%d, pts=%lld, totalSize=%d", pkt.buf->size, keyFrame?1:0, pkt.pts, thiz->mWriteSize);
					h264MuxerWriteAudio(thiz->mMuxerHandler,pkt.buf->data,pkt.buf->size);
			}
			
			thiz->mWriteSize += pkt.buf->size;

			//LOGD("[ffmux] FfmMuxer::writeSampleData [write thread]  dataSize=%d, key=%d, pts=%lld, totalSize=%d", pkt.buf->size, keyFrame?1:0, pkt.pts, mWriteSize);
			 
			//LOGD("[ffmux] FfmMuxer::writeSampleData [write thread], sample=%s", bin2hex(pkt.buf->data, min(pkt.buf->size, 32)).c_str());
			
			av_free_packet(&pkt);
		} else if(thiz->mStop) {
			break;
		}
	}

	h264MuxerCloseMp4(thiz->mMuxerHandler);
	thiz->mMuxerHandler = NULL;

	LOGD("[ffmux] mux_thread exit");
}


void FfmMuxer::start()
{
	//start a thread.
	if(av_thread_message_queue_alloc(&mMuxMessageQueue, mMaxQueueSize, sizeof(AVPacket)) < 0) {
		//thow a java exception
	}
	
	if(pthread_create(&mThreadId, NULL, mux_thread, this) < 0) {
		//throw a java exception.
	}
	
	/*
    int ret = 0;
    if (!(mOutFormat->flags & AVFMT_NOFILE)) {
        ret = avio_open(&mFormatContext->pb, mFileName, AVIO_FLAG_WRITE);
        if(ret < 0) {
            //log it and throw a java exception.
        }
    }
    ret = avformat_write_header(mFormatContext, NULL);
    if(ret < 0) {
         //log it.
        //throw a java exception..
    }

	if(!mVideoSps.empty()) {
		//need to output into file?
	}

	if(!mVideoPps.empty()) {
		//need to output into file?
	}
	*/
}

void FfmMuxer::stop()
{
	//flush the muxing queue, and stop the pthread.

	LOGD("[ffmux] FfMuxer stop begin");
	if(mStop)  {
		LOGD("[ffmux] stop, but stoped state already!!");
		return;
	}

	mStop = true;
	av_thread_message_queue_set_err_recv(mMuxMessageQueue, AVERROR_EOF);
	LOGD("[ffmux] FfMuxer stop send a end message");
	pthread_join(mThreadId, NULL);
	LOGD("[ffmux] FfMuxer stop end");
    //write a eof signal and flush the muxer.
     //av_write_trailer(mFormatContext);
}

void FfmMuxer::writeSampleData(int streamID, const char* data, int size, bool keyFlag,  int64_t pts,  int64_t dts)
{
	//push into the queue.
	if(mStop)
		return;

	/*
	if(streamID == mVideoStreamId) {
		if(mVideoSEI) {
			//TODO. dectect the sei
			mVideoSEI = false;
			LOGD("[ffmux] FfmMuxer::writeSampleData discard video sei frame");
			return;
		}
		mVideoSize += size;
	}
	*/


	AVPacket pkt;
	if(av_new_packet(&pkt, size) < 0) {
		//throw a java exception.
		LOGD("[ffmux] av_new packet fail!!!!!!");
	}

			//key frame
	if(keyFlag) {
		pkt.flags |= AV_PKT_FLAG_KEY;
	}

	pkt.pts = pts;
	pkt.dts = dts;
	pkt.stream_index = streamID;
	memcpy(pkt.buf->data, data, size);
	pkt.buf->size = size;

	//LOGD("FfmMuxer::writeSampleData streamId=%d dataSize=%d, key=%d, pts=%lld", streamID, size, (keyFlag?1:0), pkt.pts);
		//LOGD("[ffmux] FfmMuxer::writeSampleData totalSize=%d", mVideoSize);
	
	av_thread_message_queue_send(mMuxMessageQueue, &pkt, 0);
	
    //TODO. how to reuse the avpacket.
    //construct a avpacket, just deep copy
    /*
    AVPacket pkt = { 0 };
    av_init_packet(&pkt);
    pkt.pts = pts;
	pkt->dts = pts;
	pkt->stream_index = streamID;
	//pkt->duration=?

	//pkt->buf... assigne data.

	//key frame
	if(bufFlag & FLAG_KEY_FRAME) {
		pkt->flags |= AV_PKT_FLAG_KEY;
	}
    */
	//TODO. change the time base with pts/dts.
	//av_interleaved_write_frame(mFormatContext, pkt);
}

void FfmMuxer::setMeta(std::string& metaData)
{
    mMetaData = metaData;
}

