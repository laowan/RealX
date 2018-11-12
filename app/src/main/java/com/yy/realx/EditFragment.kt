package com.yy.realx

import android.app.ProgressDialog
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.ycloud.api.process.IMediaListener
import com.ycloud.api.process.VideoExport
import com.ycloud.mediaprocess.VideoFilter
import com.ycloud.player.widget.MediaPlayerListener
import com.ycloud.svplayer.SvVideoViewInternal
import com.yy.android.ai.audiodsp.IOneKeyTunerApi
import kotlinx.android.synthetic.main.fragment_edit.*
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate

class EditFragment : Fragment() {
    companion object {
        private val TAG = EditFragment::class.java.simpleName
        private const val PERIOD: Long = 32
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        prepareEditView()
    }

    private val mModel: RealXViewModel by lazy {
        ViewModelProviders.of(activity!!).get(RealXViewModel::class.java)
    }

    private lateinit var mViewInternal: SvVideoViewInternal

    /**
     * 初始化播放器
     */
    private fun prepareEditView() {
        Log.d(TAG, "prepareEditView()")
        if (video_view.videoViewInternal !is SvVideoViewInternal) {
            throw IllegalArgumentException("Only support SvVideoViewInternal, please check.")
        }
        mViewInternal = video_view.videoViewInternal as SvVideoViewInternal
        //设定播放器
        mViewInternal.setLayoutMode(SvVideoViewInternal.LAYOUT_SCALE_FIT)
        mViewInternal.setMediaPlayerListener {
            Log.d(TAG, "setMediaPlayerListener():${it.what}")
            when (it.what) {
                MediaPlayerListener.MSG_PLAY_PREPARED -> {
                    //todo: 准备完成，开始播放
                }
                MediaPlayerListener.MSG_PLAY_COMPLETED -> {
                    mViewInternal.start()
                }
                MediaPlayerListener.MSG_PLAY_SEEK_COMPLETED -> {
                    //todo: seek完成，准备播放
                }
            }
        }
        //设定数据
        val video = mModel.video.value
        checkNotNull(video)
        val path = video.path
        Log.d(TAG, "VideoPath():$path")
        mViewInternal.setVideoPath(path)
        val audio = video.audio
        Log.d(TAG, "AudioPath():${audio.path}")
        val music = VideoFilter(context)
        music.setBackgroundMusic(audio.path, 0.0f, 1.0f, audio.start)
        mViewInternal.setVFilters(music)
        //功能
        export_video.setOnClickListener {
            exportVideoWithParams(video)
        }
        tunerWithModes(video)
    }

    private fun tunerWithModes(video: VideoSettings) {
        Log.d(TAG, "tunerWithModes()")
        val dialog = ProgressDialog.show(context, "", "变声中...", false)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { dialog, keyCode, event -> true }
        val log = video.path.replace(".mp4", ".log")
        Log.d(TAG, "LogPath():$log")
        IOneKeyTunerApi.CreateOneKeyTuner(log)
        var current = 0
        val list = mutableListOf<IOneKeyTunerApi.ModeAndPosInfo>()
        video.segments.forEach {
            val mode = IOneKeyTunerApi.ModeAndPosInfo()
            mode.mode = IOneKeyTunerApi.GetTunerModeVal(it.tuner)
            mode.startPos = current
            current += it.duration
            mode.endPos = current
            list.add(mode)
        }
        IOneKeyTunerApi.OneKeyTuneWithListStartThread(list, video.audio.path, video.audio.tuner)
        mTimer.scheduleAtFixedRate(0, 100) {
            val progress = IOneKeyTunerApi.GetProgress()
            when (progress) {
                -1 -> {
                    Log.d(TAG, "VolProcessError()$progress")
                    IOneKeyTunerApi.Destroy()
                    cancel()
                    dialog.dismiss()
                }
                100 -> {
                    Log.d(TAG, "VolProcessFinish()$progress")
                    IOneKeyTunerApi.Destroy()
                    cancel()
                    dialog.dismiss()
                    //设置背景音乐
                    updateTunerByNow(video.audio)
                }
                else -> {
                    Log.d(TAG, "VolProcessProcess():$progress")
                    dialog.progress = progress
                }
            }
        }
    }

    /**
     * 导出视频
     */
    private fun exportVideoWithParams(video: VideoSettings) {
        val out = video.export
        val audio = video.audio
        val dialog = ProgressDialog.show(context, "", "导出中...", false)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { dialog, keyCode, event -> true }
        val filter = VideoFilter(context)
        filter.exportBgm = audio.tuner ?: audio.path
        filter.setBackgroundMusic(filter.exportBgm, 0.0f, 1.0f, audio.start)
        val export = VideoExport(context, video.path, out, filter)
        export.setMediaListener(object : IMediaListener {
            override fun onProgress(progress: Float) {
                Log.d(TAG, "VideoExport.onProgress():$progress")
                dialog.progress = (100 * progress).toInt()
            }

            override fun onError(errType: Int, errMsg: String?) {
                Log.d(TAG, "VideoExport.onError():$errMsg, $errMsg")
                export.cancel()
                export.release()
                dialog.dismiss()
            }

            override fun onEnd() {
                Log.d(TAG, "VideoExport.onEnd()")
                export.cancel()
                export.release()
                dialog.dismiss()
                //进入分享页面
                activity!!.runOnUiThread {
                    mModel.transitTo(Stage.SHARE)
                }
            }
        })
        val config = mViewInternal.playerFilterSessionWrapper.filterConfig
        Log.d(TAG, "exportVideoWithParams():$config")
        export.fFmpegFilterSessionWrapper.setFilterJson(config)
        mTimer.schedule(0) {
            export.export()
        }
    }

    /**
     * 变更背景音乐
     */
    private fun updateTunerByNow(audio: AudioSettings) {
        var music = VideoFilter(context)
        val path = audio.tuner
        music.setBackgroundMusic(path, 0.0f, 1.0f, audio.start)
        mViewInternal.setVFilters(music)
        //重新开始
        seekTo(0)
    }

    private var listener: TimerTask? = null

    private val mTimer: Timer by lazy {
        Timer("Edit_Timer", false)
    }

    /**
     * 开始播放
     */
    private fun start() {
        Log.d(TAG, "start()")
        mViewInternal.start()
        listener = mTimer.scheduleAtFixedRate(0, PERIOD) {
            val position = mViewInternal.currentVideoPostion
            val duration = mViewInternal.duration
            Log.d(TAG, "onProgress():$position, $duration")
            //todo: 播放进度，回调通知
        }
    }

    /**
     * 在指定位置播放
     *
     * @param
     */
    private fun seekTo(position: Int) {
        mViewInternal.pause()
        mViewInternal.seekTo(position)
        start()
    }

    /**
     * 暂停播放
     */
    private fun pause() {
        Log.d(TAG, "pause()")
        listener?.cancel()
        mViewInternal.pause()
    }

    /**
     * 释放资源
     */
    private fun release() {
        Log.d(TAG, "release()")
        pause()
        mViewInternal.stopPlayback()
    }

    //----------------------生命周期------------------------//

    override fun onResume() {
        super.onResume()
        start()
    }

    override fun onPause() {
        pause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        release()
        mTimer.cancel()
    }
}