package com.ycloud.api.videorecord;

import android.graphics.Rect;
import android.hardware.Camera;

import com.ycloud.api.config.AspectRatioType;
import com.ycloud.api.config.TakePictureConfig;
import com.ycloud.api.config.TakePictureParam;
import com.ycloud.camera.utils.ICameraEventListener;
import com.ycloud.facedetection.IFaceDetectionListener;
import com.ycloud.gpuimagefilter.filter.RecordFilterSessionWrapper;
import com.ycloud.mediarecord.IBlurBitmapCallback;
import com.ycloud.mediarecord.VideoRecordException;

/**
 * 视频录制接口类
 */
public interface IVideoRecord {
    /**
     * 设置录制生成视频文件的路径
     *
     * @param outputPath
     */
    void setOutputPath(String outputPath);

    /**
     * 录制进度回调，包括start，progress，end
     *
     * @param listener 进度回调，详见IVideoRecordListener接口
     */
    void setRecordListener(IVideoRecordListener listener);

    /**
     * 音频录制相关回调
     *
     * @param listener 目前主要回调音频振幅相关的参数
     */
    void setAudioRecordListener(IAudioRecordListener listener);

    /**
     * 录制过程中请求media sample附加信息的回调，业务层可以通过此回调，给录制的数据帧附加一些额外信息
     *
     * @param listener
     */
    void setMediaInfoRequireListener(IMediaInfoRequireListener listener);

    /**
     * 开始录制
     *
     * @param isSnapShot:录制时是否实时获取截图
     */
    void startRecord(boolean isSnapShot);

    /**
     * 设置录制的速度
     *
     * @param recordSpeed
     */
    void setRecordSpeed(float recordSpeed);

    /**
     * @param deviceLevel
     * @deprecated 设置orange filter level
     */
    void setOfDeviceLevel(int deviceLevel);

    /**
     * 结束录制
     */
    void stopRecord();

    /**
     * 业务层通知sdk删除了上一段录制，sdk更新保存的截图delta
     */
    void deleteLastRecordSnapshot();

    /**
     * 设置前/后置摄像头
     *
     * @param Id
     */
    void setCameraID(int Id) throws VideoRecordException;

    /**
     * 设置闪光灯模式
     *
     * @param flashMode The flashMode value can only be the values in Class FlashMode
     */
    void setFlashMode(String flashMode);

    /**
     * 暂停录制
     */
    void onPause();

    /**
     * 恢复录制
     *
     * @throws VideoRecordException
     */
    void onResume() throws VideoRecordException;


    /**
     * 开始相机预览，并监听预览相关的回调
     *
     * @param listener
     */
    void startPreview(IVideoPreviewListener listener) throws VideoRecordException;

    /**
     * 设置是否录制音频
     **/
    void setEnableAudioRecord(boolean enable_audio);

    /**
     * 切换前后摄向头
     **/
    void switchCamera();

    /**
     * 获得摄像头参数
     *
     * @return
     */
    Camera.Parameters getCameraParameters();

    /**
     * 设置摄像头参数
     *
     * @param parameters
     * @return true设置成功，false 设置失败
     */
    boolean setCameraParameters(Camera.Parameters parameters);

    /**
     * 获得当前摄像头相关信息，如当前为前置摄像头还是后置摄像头
     *
     * @return
     */
    Camera.CameraInfo getCameraInfo();

    /**
     * 释放VideoRecord相关资源
     */
    void release();

    /**
     * 设置触摸监听
     * @param touchListener
     */
//    void setTouchListener(VideoRecordTouchListener touchListener);

    /**
     * 分辨率设置
     *
     * @param width
     * @param height
     */
    void setVideoSize(int width, int height);

    /**
     * 返回该机器是否支持录制
     *
     * @return
     */
    boolean isRecordEnabeled();

    /**
     * 设置人脸检测回调接口.
     *
     * @param listener
     */
    void setFaceDetectionListener(final IFaceDetectionListener listener);

    /**
     * @param snapShotPath:录制截图存放的目录
     * @param fileNamePrefix：录制截图的文件名前缀
     * @param snapFrequency:截图帧率
     */
    void setRecordSnapShot(String snapShotPath, String fileNamePrefix, float snapFrequency);

    /**
     * 设置手Y的版本号
     *
     * @param yyVersion
     */
    void setYyVersion(String yyVersion);

    /**
     * 设置录制需要的编码模式，从而sdk内部提供不同的编码参数。默认是RECORD_ENCODE_TWICE，即录制时候编码一次，发布的时候再编码一次。
     *
     * @param encodeType RECORD_ENCODE_ONCE：业务逻辑只会进行一次硬编码  RECORD_ENCODE_TWICE：业务逻辑会进行两次编码  RECORD_SOFT_ENCODE_ONCE：业务逻辑只会进行一次软编吗
     */
    void setEncodeType(int encodeType);


    void setBlurBitmapCallBack(IBlurBitmapCallback blurBitmapCallBack);

    /**
     * 获取zoom的程度
     */
    int getZoom();

    int getMaxZoom();

    /**
     * 设置zoom的程度,范围是1~ getMaxZoom；
     */
    void setZoom(int factor);

    /**
     * 设置点测光和对焦，
     *
     * @param x
     * @param y
     * @param autoCancel true：调用后恢复连续对焦和测光模式；false：调用后对焦和测光的位置被锁定
     */
    void focusAndMetering(float x, float y, boolean autoCancel);

    /**
     * 获取录制页的滤镜接口类
     *
     * @return
     */
    RecordFilterSessionWrapper getRecordFilterSessionWrapper();

    /**
     * 获取相机当前预览效果的截图
     *
     * @param blurBitmapCallback
     */
    void getCameraBitmap(IBlurBitmapCallback blurBitmapCallback);

    /**
     * 设置拍照的相关参数，必须在打开摄像头前设置，最好在new NewVideoRecord() 后马上设置，
     * onResume 中SDK会打开摄像头
     *
     * @param config
     */
    void setTakePictureConfig(TakePictureConfig config);

    /**
     * @param config 拍照相关参数设置
     */
    void takePicture(TakePictureParam config);

    /**
     * 点击拍照后，系统会自动停止预览，需要重新调用该接口启动预览
     */
    void recoveryPreview();

    /**
     * 预览页视频宽高比动效完成后，通知业务层
     *
     * @param listener
     */
    void setAspectRatioListener(IChangeAspectRatioListener listener);

    /**
     * @param bEffect 宽高比变化过程是否开启动态效果
     */
    void setAspectWithDynamicEffect(boolean bEffect);

    /**
     * 设置预览界面的宽高比，不需要重新打开摄像头，SDK做对应裁剪
     *
     * @param aspectRatio 宽高比
     * @param x_offset    预览画面 x 轴偏移
     * @param y_offset    预览画面 y 轴偏移， 如要让居中位置下移，设置Y为负值，上移，设置Y为正值
     *                    <p>
     *                    注意：此处X,Y 对应的原点为左下角
     */
    void setAspectRatio(AspectRatioType aspectRatio, int x_offset, int y_offset);

    /**
     * 获取当前预览画面的显示区域矩形, 包含 setAspectRatio 设置的 x,y 偏移
     * 注意：此处X,Y 对应的原点为左下角
     *
     * @return
     */
    Rect getCurrentVideoRect();


    /**
     * 只用于在 getFinalPreviewRectByAspect 接口前同步当前view 的宽高。
     * 解决View宽高改变后，由于PreviewFilter中SurfaceChange处理是异步的，getFinalPreviewRectByAspect 获取矩形不准问题
     *
     * @param surfaceWidth
     * @param surfaceHeight
     */
    public void SyncFinalPreviewRect(int surfaceWidth, int surfaceHeight);

    /**
     * 获取宽高比为 aspectRatio 时，预览画面居中显示时的矩形区域， 不包含 setAspectRatio 设置的 x,y 偏移
     * 用于切换 aspect ratio 前获取矩形区域计算不同手机上的偏移值，传递给 setAspectRatio 使用
     *
     * @return
     */
    Rect getFinalPreviewRectByAspect(AspectRatioType aspectRatio);

    /**
     * 设置截取预览画面截图回调
     *
     * @param listener
     */
    void setPreviewSnapshotListener(IPreviewSnapshotListener listener);

    /**
     * 截取预览画面，包含美颜 贴纸等特效， 截图结果通过 IPreviewSnapshotListener 回调
     *
     * @param path            截图文件保存全路径
     * @param width           截图图片宽度
     * @param height          截图图片高度
     * @param ImageEncodeType 截图编码类型（0-JPEG， 1- PNG)
     * @param quality         图片编码质量（0 ~ 100）
     * @param flipX           是否水平镜像
     */
    void takePreviewSnapshot(String path, int width, int height, int ImageEncodeType, int quality, boolean flipX);


    /**
     * 设置截取 原始图像 预览画面截图回调
     *
     * @param listener
     */
    void setOriginalPreviewSnapshotListener(IOriginalPreviewSnapshotListener listener);

    /**
     * 截取预览画面的原始图像，不包含美颜 贴纸等特效，
     *
     * @param path
     * @param width
     * @param height
     * @param ImageEncodeType
     * @param quality
     * @param flipx
     */
    void takeOriginalPreviewSnapshot(String path, int width, int height, int ImageEncodeType, int quality, boolean flipx);

    /**
     * 该接口已弃用
     * 设置预览画面视频显示区域左上角坐标偏移
     * 注意：这个偏移是视频按宽高比上下居中后设置的偏移，如要让居中位置下移，设置Y为负值，上移，设置Y为正值
     *
     * @param x_offset
     * @param y_offset
     */
    @Deprecated
    void setPreviewRectOffset(int x_offset, int y_offset);

    /**
     * 预览画面水平镜像，每调一次改变一次镜像,在预览的时候可以多次设置
     */
    void setPreviewFlipX();

    /**
     * 摄像头操作事件回调
     *
     * @param listener
     */
    void setCameraEventListener(ICameraEventListener listener);

    /**
     * 设置获取摄像头原始数据回调
     *
     * @param listener
     */
    void setPreviewCallbackListener(ICameraPreviewCallbackListener listener);

}
