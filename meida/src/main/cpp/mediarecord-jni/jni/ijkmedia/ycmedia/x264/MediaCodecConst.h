//
// Created by Administrator on 2016/9/13.
//

#ifndef TRUNK_MEDIACODECCONST_H
#define TRUNK_MEDIACODECCONST_H

enum MediaLibraryCodecMode
{
    kMediaLibraryDecoder = 0,
    kMediaLibraryEncoder = 1,
};

enum MediaLibraryVideoCodec
{
    kMediaLibraryVideoCodecUnknown  = 0,
    kMediaLibraryVideoCodecPicture  = 1,
    kMediaLibraryVideoCodecH264     = 2,
    kMediaLibraryVideoCodecVP8      = 4,
	kMediaLibraryVideoCodecH265     = 5,
};

#endif //TRUNK_MEDIACODECCONST_H
