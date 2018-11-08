//
// Created by Administrator on 2016/9/13.
//

#include "IntTypes.h"
extern "C"
{
    #include "libx264/x264.h"
}

#include "Common.h"
#include "x264Encoder.h"
#include "MediaCodecConst.h"
#include "CommonUtils.h"
#include "Mediabase.h"
#include "DumpUtil.h"
#include <math.h>
#include <errno.h>
#include "AdaptivePicBuffer.h"

USING_NAMESPACE_YYMFW;

struct X264Encoder
{
    x264_param_t * param;
    x264_t *handle;
    x264_picture_t * picture;
    x264_nal_t  *nal;
};

static void x264_log( void *p_unused, int i_level, const char *psz_fmt, va_list args )
{
    char buf[512];
    vsnprintf(buf, 512 - 1, psz_fmt, args);

    buf[512 - 1] = 0;
    //LOGD(buf);
}

static const char *presetStringFromInt(int n)
{
    switch (n) {
        case 1:
            return "ultrafast";
        case 2:
            return "superfast";
        case 3:
            return "veryfast";
        case 4:
            return "faster";
        case 5:
            return "fast";
        case 6:
            return "medium";
        case 7:
            return "slow";
        case 8:
        	return "yymedium";
        case 9:
        	return "yyfast";
        case 10:
        	return "yyfaster";
        case 11:
        	return "yyveryfast";
        default:
            return "superfast";
    }
}

CX264Encoder::CX264Encoder()
{
	m_pX264Encoder = NULL;
	m_nPicW        = 0;
	m_nPicH        = 0;
	m_nFps		   = 0;
	m_nBitrate     = 0;
	m_nProfile     = -1;
	m_pSps         = NULL;
	m_pPps         = NULL;
	m_nSpsLen      = 0;
	m_nPpsLen      = 0;
    m_rateFactor   = 0;

	/** h264ç›®å‰çš„ç¼–ç æ˜¯åŒæ­¥çš? æœ€å¤šä¸€æ¬¡è¾“å‡?ä¸ªæ•°æ®å¸§ */
	m_outputList = new MediaLibrary::VideoEncodedList();
	m_outputList->iPicData = (MediaLibrary::VideoEncodedData*)  MediaLibrary::AllocBuffer(3 * sizeof(MediaLibrary::VideoEncodedData));
	m_outputList->iSize = 0;
	m_outputList->capacity = 3;

	m_PicDataBuffer = new AdaptivePicBuffer();
	m_ppsBuffer = new AdaptivePicBuffer();
	m_spsBuffer = new AdaptivePicBuffer();

	mRepeateHeader = false;
}

CX264Encoder::~CX264Encoder()
{
	if(m_outputList) {
		MediaLibrary::FreeBuffer(m_outputList->iPicData);
	}
	delete  m_outputList;

	delete  m_PicDataBuffer;
	delete  m_ppsBuffer;
	delete  m_spsBuffer;

	clearPicBufferList();
}

int CX264Encoder::CodecMode()
{
	return kMediaLibraryEncoder;
}

const char* CX264Encoder::CodecDescribe()
{
	return "X264 Soft Encoder v2.0";
}

int  CX264Encoder::CodecID()
{
	return  kMediaLibraryVideoCodecH264;
}

int CX264Encoder::CodecLevel()
{
	return 0;
}

int  CX264Encoder::Init(void* pParam, std::string configStr)
{
	MediaLibrary::VideoStreamFormat* pStreamFormat = (MediaLibrary::VideoStreamFormat*) pParam;
	if(pStreamFormat->iPicFormat != kMediaLibraryPictureFmtI420) {
        LOGE("X264 encode picture format is not YUV420!");
		return -1;
	}

	m_pSps         = NULL;
	m_pPps         = NULL;
	m_nSpsLen      = 0;
	m_nPpsLen      = 0;
	m_nPicW        = pStreamFormat->iWidth;
	m_nPicH        = pStreamFormat->iHeight;
	m_nFps		   = pStreamFormat->iFrameRate;
	m_nBitrate     = pStreamFormat->iBitRate;
	m_nProfile     = pStreamFormat->iProfile;
	m_nPicFormat   = pStreamFormat->iPicFormat;
    if (m_nProfile > 5) {
		m_nProfile = 5;
    }

    X264Encoder *pEn = new X264Encoder;
	pEn->param		 = (x264_param_t *)   MediaLibrary::AllocBuffer(sizeof(x264_param_t));
	pEn->picture	 = (x264_picture_t *) MediaLibrary::AllocBuffer(sizeof(x264_picture_t));

	LOGI("CX264Encoder:: picW=%d, picH=%d, fps=%d, bitrate=%d, profile=%d", m_nPicW, m_nPicH, m_nFps, m_nBitrate, m_nProfile);

#if 1
    std::size_t presetPos = configStr.find("preset=");
    std::string paramStr;
    if (presetPos != std::string::npos)
    {
        std::size_t posStart = configStr.find(":");
        std::string presetConfig = configStr.substr(0, posStart);
        paramStr = configStr.substr(posStart+1);

        std::size_t pos = presetConfig.find("=");
        std::string preset = presetConfig.substr(pos + 1);

        int presetRet = x264_param_default_preset(pEn->param, preset.c_str(), "");
		LOGI("CX264Encoder::Init preset from config server is %s, presetRet %d", preset.c_str(), presetRet);
    } else {
        const char *presetString = presetStringFromInt(pStreamFormat->iEncodePreset);
        int presetRet = x264_param_default_preset(pEn->param, presetString, "");
		LOGI("CX264Encoder::Init preset by default is %s, presetRet %d", presetString, presetRet);

        paramStr = configStr;
    }

	LOGI("CX264Encoder:: x264 params: %s", paramStr.c_str());

	//x264_param_apply_profile(pEn->param, x264_profile_names[m_nProfile]);
	pEn->param->i_width				 = m_nPicW;		 //set frame width
	pEn->param->i_height			 = m_nPicH;		 //set frame height
#if 0
	pEn->param->rc.i_bitrate	     = m_nBitrate;
    pEn->param->rc.i_vbv_max_bitrate = m_nBitrate;
	pEn->param->rc.i_vbv_buffer_size = m_nBitrate;
	pEn->param->rc.f_vbv_buffer_init = 0.5;
	//pEn->param->rc.f_rate_tolerance  = 1.0;
	pEn->param->rc.i_rc_method       = X264_RC_ABR;
#else
	pEn->param->rc.i_vbv_max_bitrate = m_nBitrate;
	pEn->param->rc.i_vbv_buffer_size = 3 * m_nBitrate;
	pEn->param->rc.i_rc_method       = X264_RC_CRF;
	pEn->param->rc.f_rf_constant	 = 23;
#endif
	pEn->param->i_fps_num			 = m_nFps;
	pEn->param->i_fps_den			 = 1;
	pEn->param->i_timebase_num 		 = 1;
	pEn->param->i_timebase_den       = 1000;
	pEn->param->i_keyint_max         = m_nFps * 3;
	pEn->param->i_keyint_min         = m_nFps * 3;
	pEn->param->b_repeat_headers  	 = 0;


	mRepeateHeader = (pEn->param->b_repeat_headers != 0);

    char * pCsConfig = new char[paramStr.size() + 1];
	char *pCsConfigTmp = pCsConfig;
	std::copy(paramStr.begin(), paramStr.end(), pCsConfig);
	pCsConfig[paramStr.size()] = '\0'; // don't forget the terminating 0

	// char *pCsConfig = configStr.c_str();
	char param[256]={'\0'}, val[256]={'\0'};

	if(strlen(pCsConfig) > 0) {
		while(pCsConfig){
			if(sscanf(pCsConfig, "%255[^:=]=%255[^:=]", param, val) == 1){
				x264_param_parse(pEn->param, param, "1");
			}else{
				x264_param_parse(pEn->param, param, val);
			}
			pCsConfig= strchr(pCsConfig, ':');
			pCsConfig+=!!pCsConfig;
		}
	}

	delete [] pCsConfigTmp;

	char *p = NULL;
	p = x264_param2string(pEn->param, 1);
	if(p) {
		LOGI("X264 encode param: %s", p);
		free(p);
	}
#else
    const char *presetString = presetStringFromInt(pStreamFormat->iEncodePreset);
    int presetRet = x264_param_default_preset(pEn->param, presetString, "");
    LOGI("CX264Encoder::Init presetRet %d", presetRet);

	x264_param_apply_profile(pEn->param,x264_profile_names[m_nProfile]);
    pEn->param->i_threads            = 2;
    pEn->param->b_sliced_threads     = 1;
    pEn->param->b_deterministic      = 1;
	pEn->param->i_csp                = X264_CSP_I420;
	pEn->param->i_width				 = m_nPicW;		 //set frame width
	pEn->param->i_height			 = m_nPicH;		 //set frame height
	pEn->param->rc.i_lookahead		 = 0;
    pEn->param->i_bframe             = 0;

	pEn->param->i_fps_num			 = m_nFps;
	pEn->param->i_fps_den			 = 1;

	pEn->param->rc.i_rc_method       = X264_RC_ABR;
    pEn->param->rc.f_rf_constant     = 23;
	pEn->param->rc.i_qp_max          = 42;
	pEn->param->rc.i_qp_min          = 12;
	pEn->param->rc.i_qp_step         = 4;

    pEn->param->rc.i_bitrate	     = m_nBitrate;
    pEn->param->rc.i_vbv_max_bitrate = m_nBitrate;
	pEn->param->i_keyint_max         = 3 * m_nFps;
	pEn->param->i_keyint_min         = 3 * m_nFps;
	pEn->param->i_scenecut_threshold = 0;
	pEn->param->b_intra_refresh      = 0;

    LOGI("[statistic] X264 encode bitrate : %8d; w,h:%4d,%4d, profile=%d", m_nBitrate, m_nPicW, m_nPicH, m_nProfile);

	pEn->param->rc.f_rate_tolerance = 0.1;
	pEn->param->rc.i_vbv_buffer_size = pEn->param->rc.i_vbv_max_bitrate*2;
	pEn->param->rc.f_vbv_buffer_init = 0.5;

	pEn->param->vui.b_fullrange = 0;
	pEn->param->vui.i_colorprim = 5;
	pEn->param->vui.i_transfer = 5;
	pEn->param->vui.i_colmatrix = 5;

	pEn->param->pf_log = x264_log;
	pEn->param->p_log_private = NULL;
	pEn->param->i_log_level = X264_LOG_WARNING;
	pEn->param->b_vfr_input = 0;
	pEn->param->i_timebase_num = 1;
	pEn->param->i_timebase_den = 1000;

	pEn->param->b_repeat_headers = 1;

	LOGI("[statistic] [x264e](veryfast+zerolatency) key rate control params(method:%u,bitrate:%u,framerate:%u)",
        pEn->param->rc.i_rc_method,
        pEn->param->rc.i_bitrate,
        pEn->param->i_fps_num);
#endif

	if ((pEn->handle = x264_encoder_open(pEn->param)) == 0) {
		char * resStr = x264_param2string( pEn->param, 1 );
        LOGE("CX264Encoder::Init, open encoder failed!, errno=%d, res=%s",errno, resStr);
		return -1;
	}

	char * resStr = x264_param2string( pEn->param, 1 );
	LOGI("CX264Encoder::Init x264_param2string:%s ",resStr);

	/* Create a new pic */
	x264_picture_alloc(pEn->picture, X264_CSP_I420, pEn->param->i_width, pEn->param->i_height);

	m_pX264Encoder = pEn;

	x264_nal_t *headers = NULL;
	int i_nal;

	int n = x264_encoder_headers(pEn->handle, &headers, &i_nal);
	if(n < 0){
        LOGE("X264 encode, get encoder headers failed!");
		return -1;
	}


  /** keep  AVC NAL start code  */
	int sps_size = headers[0].i_payload ;
	int pps_size = headers[1].i_payload;
	uint8_t *sps = headers[0].p_payload;
    uint8_t *pps = headers[1].p_payload ;

	if(sps) {
		LOGD("sps_size len %d", sps_size);
		m_pSps = (unsigned char*)MediaLibrary::AllocBuffer(sps_size);
		memcpy(m_pSps, sps, sps_size);
		m_nSpsLen = sps_size;

		std::string binStr = DumpUtil::bin2hex((const char*)m_pSps,sps_size);
		LOGD("sps = %s", binStr.c_str());
		//debug info logcat..
	}

    if(pps) {
		LOGD("pps_size len %d", pps_size);
		m_pPps = (unsigned char*)MediaLibrary::AllocBuffer(pps_size);
		memcpy(m_pPps, pps, pps_size);
		m_nPpsLen = pps_size;

		std::string binStr = DumpUtil::bin2hex((const char*)m_pPps,pps_size);
		LOGD("pps = %s", binStr.c_str());
		//debug info logcat.
	}

	LOGD("enc w = %d h = %d", m_nPicW, m_nPicH);
	m_isFirstFrame = true;
	return 0;
}


const uint8_t * CX264Encoder::find_startcode_internal(const uint8_t *p, const uint8_t *end)
{
	const uint8_t *a = p + 4 - ((intptr_t)p & 3);
	for (end -= 3; p < a && p < end; p++) {
		if (p[0] == 0 && p[1] == 0 && p[2] == 1)
			return p;
	}

	for (end -= 3; p < end; p += 4) {
		uint32_t x = *(const uint32_t*)p;
		if ((x - 0x01010101) & (~x) & 0x80808080) { // generic
			if (p[1] == 0) {
				if (p[0] == 0 && p[2] == 1)
					return p;
				if (p[2] == 0 && p[3] == 1)
					return p + 1;
			}
			if (p[3] == 0) {
				if (p[2] == 0 && p[4] == 1)
					return p + 2;
				if (p[4] == 0 && p[5] == 1)
					return p + 3;
			}
		}
	}

	for (end += 3; p < end; p++) {
		if (p[0] == 0 && p[1] == 0 && p[2] == 1)
			return p;
	}
	return end + 3;
}

const uint8_t* CX264Encoder::find_startcode(const uint8_t *p, const uint8_t *end)
{
	const uint8_t *out = find_startcode_internal(p, end);
	if (p < out && out < end && !out[-1])
		out--;
	return out;
}

void  CX264Encoder::packEncodedList(const uint8_t* p, uint32_t size, unsigned int pts, unsigned int dts, MediaLibrary::VideoFrameType frameType)
{
	if(size == 0)
		return;
	
	//int cnt = size < 32 ? size : 32;
	//LOGD("CX264Encoder::packEncodedList begin, size=%u, pts=%u, frameType=%d %s", size, pts, frameType, bin2hex(p,cnt).c_str());

	const uint8_t * end = p + size-1;
	const uint8_t *nal_start, *last_nal_start;
	const uint8_t * frame_start;
	uint32_t frame_size = 0;
	nal_start = find_startcode(p, end);

	while(nal_start < end ) {
		last_nal_start = nal_start;
		while (!*(nal_start++) && nal_start < end);
		nal_start = find_startcode(nal_start, end);
		frame_start = last_nal_start;
		frame_size = nal_start < end ? (nal_start - last_nal_start) : (end-last_nal_start+1);

		MediaLibrary::VideoEncodedData data;
		data.iData = (void*)frame_start;
		data.iDataLen = frame_size;
		data.iPts = pts;
		data.iDts = dts;
		data.iPicHeight = m_nPicH;
		data.iPicWidth = m_nPicW;
		data.iFrameType = frameType;

		uint32_t len = data.iDataLen < 24 ? data.iDataLen : 24;
		//LOGD("X264SoftEncoder_Process packEncodedList, size=%d, frameType=%d nal:%s", frame_size, frameType,  DumpUtil::bin2hex((const char*)data.iData,len).c_str());
		pushVideoEncodedData(&data);
	}
}

int   CX264Encoder::convert_to_x264_frame_type(MediaLibrary::VideoFrameType frameType)
{
	switch (frameType) {
		case MediaLibrary::kVideoIDRFrame:
			return X264_TYPE_IDR;
			break;
		case MediaLibrary::kVideoIFrame:
			return  X264_TYPE_I;
			break;
		case MediaLibrary::kVideoPFrame:
			return  X264_TYPE_P;
			break;
		case MediaLibrary::kVideoBFrame:
			return X264_TYPE_B;
			break;
		default:
			return X264_TYPE_AUTO;
			break;
	}
}

MediaLibrary::VideoFrameType CX264Encoder::convert_to_yy_frame_type(int x264FrameType)
{
	switch (x264FrameType) {
		case X264_TYPE_IDR:
			return MediaLibrary::kVideoIDRFrame;
			break;
		case X264_TYPE_I:
			return MediaLibrary::kVideoIFrame;
			break;
		case X264_TYPE_P:
			return MediaLibrary::kVideoPFrame;
			break;
		case X264_TYPE_B :
			return MediaLibrary::kVideoBFrame;
			break;
		default:
			return MediaLibrary::kVideoIDRFrame;
			break;
	}
}

void  CX264Encoder::pushVideoEncodedData(MediaLibrary::VideoEncodedData* data)
{
	if(m_outputList->capacity <= m_outputList->iSize) {
		//reallocate memory.
		do {
			m_outputList->capacity *= 2;
		} while(m_outputList->capacity <= m_outputList->iSize);

		MediaLibrary::VideoEncodedData* oldData = m_outputList->iPicData;
		m_outputList->iPicData = (MediaLibrary::VideoEncodedData*)  MediaLibrary::AllocBuffer(m_outputList->capacity * sizeof(MediaLibrary::VideoEncodedData));
		for(int i = 0; i < m_outputList->iSize; i++)
		{
			m_outputList->iPicData[i] = oldData[i];
		}
		MediaLibrary::FreeBuffer(oldData);
	}
	m_outputList->iPicData[m_outputList->iSize] = *data;
	m_outputList->iSize++;
}


void CX264Encoder::clearPicBufferList()
{
	std::list<AdaptivePicBuffer*>::iterator it = m_PicDataBufferList.begin();
	while(it != m_PicDataBufferList.end()) {
		delete *it;
		it++;
	}

	m_PicDataBufferList.clear();
}



int CX264Encoder::fetchFrame(void** pOutDes, int nNal, void *picOut, AdaptivePicBuffer  *picBuffer)
{

	x264_picture_t* pic_out = (x264_picture_t*)picOut;
	X264Encoder * pEn = m_pX264Encoder;
	
	int nInx = 0;
	if(m_isFirstFrame){
		m_isFirstFrame = false;

		unsigned char* pSPSData = (unsigned char*)m_spsBuffer->getBuffer(m_nSpsLen);
		memcpy(pSPSData, m_pSps, m_nSpsLen);


		LOGD("X264SoftEncoder_Process sps:%s", DumpUtil::bin2hex(m_pSps,m_nSpsLen).c_str());
		MediaLibrary::VideoEncodedData data;
		data.iData    = pSPSData;
		data.iDataLen = m_nSpsLen;
		data.iPts     = 0;
		data.iDts     = 0;
		data.iFrameType = MediaLibrary::kVideoSPSFrame;
		data.iPicWidth = m_nPicW;
		data.iPicHeight = m_nPicH;
		pushVideoEncodedData(&data);

		//pps
		//unsigned char* pPPSData = (unsigned char*)MediaLibrary::AllocBuffer(m_nPpsLen);
		unsigned char* pPPSData = (unsigned char*)m_ppsBuffer->getBuffer(m_nPpsLen);
		memcpy(pPPSData, m_pPps, m_nPpsLen);

		data.iData    = pPPSData;
		data.iDataLen = m_nPpsLen;
		data.iPts     = 0;
		data.iDts     = 0;
		data.iFrameType = MediaLibrary::kVideoPPSFrame;
		data.iPicWidth = m_nPicW;
        data.iPicHeight = m_nPicH;
		pushVideoEncodedData(&data);

		*pOutDes = m_outputList;

		if(nNal > 0 && mRepeateHeader) {
			*pOutDes = m_outputList;
			nInx = 2;
		}
 
	}
 

	if(nNal > 0) {
			int nBufSize = 0;
			for (int i = nInx; i < nNal; i++) {
				nBufSize += pEn->nal[i].i_payload;
			}
	
			//unsigned char* pAVCData = (unsigned char*)MediaLibrary::AllocBuffer(nBufSize);
			unsigned char* pAVCData = (unsigned char*)picBuffer->getBuffer(nBufSize);
			int nCount = 0;
			for (int i = nInx; i < nNal; i++){
				//nCount += avc_parse_nal_units(pAVCData + nCount, pEn->nal[i].p_payload, pEn->nal[i].i_payload);
				memcpy(pAVCData+nCount,  pEn->nal[i].p_payload, pEn->nal[i].i_payload);
				nCount += pEn->nal[i].i_payload;
			}
	
			MediaLibrary::VideoFrameType  frameType = convert_to_yy_frame_type(pic_out->i_type);
	
			packEncodedList(pAVCData, nCount, pic_out->i_pts, pic_out->i_dts, frameType);
			//LOGD("nal=%d, pic_out.i_type=%d, frame_type=%d, size=%d, pts=%d, dts=%d, size=%d",
				//	nNal, pic_out.i_type, frameType, nCount, (int32_t)(pic_out.i_pts), (int32_t)(pic_out.i_dts), nCount);
			*pOutDes = m_outputList;
	
	}

	return 0;
}


int  CX264Encoder::flush(void** pOutDes)
{
	LOGD("X264SoftEncoder_Flush begin");
	m_outputList->iSize = 0;
	MediaLibrary::VideoEncodedList* pOutData = m_outputList;
	X264Encoder * pEn = m_pX264Encoder;
	if(!pEn) {
		LOGD("X264SoftEncoder_Flush X264Encoder is NULL");
		return -1;
	}

	*pOutDes = NULL;
	int cnt = 0;
	clearPicBufferList();

	while(x264_encoder_delayed_frames(pEn->handle) > 0) {
		int nInx = 0;
		x264_picture_t pic_out;
		int nNal=-1;

		if (x264_encoder_encode( pEn->handle, &(pEn->nal), &nNal, NULL, &pic_out) < 0) {
    		LOGD("X264SoftEncoder_Flush x264_encoder_encode return -1");
			return -1;
    	}

		AdaptivePicBuffer* picBuffer = new AdaptivePicBuffer();

		fetchFrame(pOutDes, nNal, &pic_out, picBuffer);
		m_PicDataBufferList.push_back(picBuffer);

		if(++cnt % 20 == 0) {
			LOGD("X264SoftEncoder_Flush get frame count: %d", cnt);
		}
	}

	LOGD("X264SoftEncoder_Flush get frame total count: %d", cnt);
	return 0;
}


//Notice, è€ƒè™‘å†…å­˜é—®é¢˜, ä¸å†åšçš„çº¿ç¨‹å®‰å…¨, ä¸€ä¸ªencoderåªæ˜¯ä¾›ä¸€ä¸ªçº¿ç¨‹ä½¿ç”?
int CX264Encoder::Process(const unsigned char *pData, unsigned int nDataLen, void* pInDes, void** pOutDes)
{
	m_outputList->iSize = 0;
	MediaLibrary::FrameDesc*  pFrameDesc = (MediaLibrary::FrameDesc*)pInDes;
	//MediaLibrary::VideoEncodedList* pOutData  = (MediaLibrary::VideoEncodedList*)pOutDes;
	MediaLibrary::VideoEncodedList* pOutData = m_outputList;
	X264Encoder * pEn = m_pX264Encoder;	
	if(!pEn) {
		LOGD("X264SoftEncoder_Process X264Encoder is NULL");
		return -1;
	}

	//YUY420
	int nPicSize= pEn->param->i_width * pEn->param->i_height;
	uint8_t * y = pEn->picture->img.plane[0];
	uint8_t * v = pEn->picture->img.plane[1];
	uint8_t * u = pEn->picture->img.plane[2];
	memcpy(y, pData, nPicSize);
	memcpy(v, pData + nPicSize, nPicSize / 4);
	memcpy(u, pData + nPicSize + nPicSize / 4,  nPicSize / 4);

	pEn->picture->i_type = convert_to_x264_frame_type(pFrameDesc->iFrameType);
	pEn->picture->i_qpplus1 = 0;
	pEn->picture->i_pts = pFrameDesc->iPts;

	x264_picture_t pic_out;
	int nNal=-1;

	m_PicDataBuffer->clear();
    if (x264_encoder_encode( pEn->handle, &(pEn->nal), &nNal, pEn->picture ,&pic_out) < 0) {
    	LOGD("X264SoftEncoder_Process x264_encoder_encode return -1");
		return -1;
    }

	fetchFrame(pOutDes, nNal, &pic_out, m_PicDataBuffer);
	return 0;
}

void CX264Encoder::DeInit()
{
	X264Encoder * pEn = m_pX264Encoder;
	if(!pEn) return ;

	if(pEn->picture) {
		x264_picture_clean(pEn->picture);
		MediaLibrary::FreeBuffer(pEn->picture);
		pEn->picture	= 0;
	}
	if(pEn->param) {
		MediaLibrary::FreeBuffer(pEn->param);
		pEn->param=0;
	}
	if(pEn->handle) {
		x264_encoder_close(pEn->handle);
	}
    delete pEn;
    m_pX264Encoder = NULL;

	if(m_pSps) {
		MediaLibrary::FreeBuffer(m_pSps);
		m_pSps         = NULL;
	}

	if(m_pPps) {
		MediaLibrary::FreeBuffer(m_pPps);
		m_pPps         = NULL;
	}
}

#ifdef ANDROID
double log2(double x)
{
    return log(x) / log(2.0);
}
#endif

void CX264Encoder::SetTargetBitrate(int bitrateInKbps)
{
    X264Encoder * pEn = m_pX264Encoder;
    if(!pEn) {
        LOGE("Invalid X264Encoder, didn't init encoder!");
        return;
    }

    float rf = bitrateInKbps * 1.0 / m_nBitrate;

    if(m_rateFactor == rf) {
        return ;
    }

    m_rateFactor = rf;
    if(m_rateFactor < 0.3) {
        m_rateFactor = 0.3;
    } else if(m_rateFactor > 2.0){
        m_rateFactor = 2.0;
    }

    float rfDelta = log2(m_rateFactor) * 7;
    pEn->param->rc.f_rf_constant = 23 - rfDelta;
    pEn->param->rc.i_bitrate = m_nBitrate * m_rateFactor;
    pEn->param->rc.i_vbv_max_bitrate = m_nBitrate * m_rateFactor;
	pEn->param->rc.i_vbv_buffer_size = pEn->param->rc.i_vbv_max_bitrate * 2;
	int err = x264_encoder_reconfig(pEn->handle, pEn->param);
    if(err != 0) {
        LOGE("fail to reconfig x264 param rateFactor delta %f, bitRateInKpbs=%d", rfDelta, bitrateInKbps);
    } else {
		LOGDXXX("[statistic] succeed to reconfig x264 param rateFactor delta %f, bitRateInKpbs=%d", rfDelta, bitrateInKbps);
	}
}

/*
MediaCodec* x264Encoder()
{
	MediaCodec*  ac = new MediaCodec();
	ac->Create      = CreateX264Encoder;
	ac->Destroy     = ReleaseX264Encoder;
	ac->nCodecID    = kMediaLibraryVideoCodecH264;
	ac->nType       = kMediaLibraryEncoder;
	ac->nLevel      = 0;
	return  ac;
}
 */