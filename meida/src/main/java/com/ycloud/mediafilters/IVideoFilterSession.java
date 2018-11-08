package com.ycloud.mediafilters;
import com.ycloud.mediacodec.VideoEncoderConfig;
import com.ycloud.mediafilters.IEncoderListener;
import java.util.List;

/**
 * Created by kele on 2017/3/27.
 */

public interface IVideoFilterSession {

    void stopAndRelease();
    /**
     * 启动编码器
     */
    void startEncoder();

    /**
     * 关闭编码器
     */
    void stopEncoder();

    /**
     * 配置编码器(软编硬编都需要配置)
     * @param config
     */
    void setEncoderConfig(VideoEncoderConfig config);

    void setEncoderListener(IEncoderListener listener);


//    void setWaterMark(WaterMark waterMark);
//    void setDynamicTexture(IDynamicTexture dynamicTexture);
//    void takeScreenShot(ScreenShotCallback callback);
//    void setNetworkBitrateSuggest(int bitrate);
//    void adjustEncoderBitrate(int bitRate);
//    void requestIFrame();
//    void setLowDelayMode(final boolean enable);
//    void setResolutionModifyConfigs(final List<ResolutionModifyConfig> configs, final int intervalSecs);
}
