package com.ycloud.audio;

import android.text.TextUtils;

import com.ycloud.utils.YYLog;

import java.io.File;

/**
 * Created by Administrator on 2018/1/4.
 */

public class AudioFileCacheMgr {
    private static AudioFileCacheMgr ourInstance = new AudioFileCacheMgr();
    public static AudioFileCacheMgr getInstance() {return ourInstance;}
    static final String TAG = "AudioFileCacheMgr";

    private File mCacheDir;
    private int mMagicNumber;
    private static String TMP_SUFFIX = ".t";

    private AudioFileCacheMgr() {

    }

    public void init(String cacheDir) {
        if (mCacheDir == null) {
            mCacheDir = new File(cacheDir, "magic_audio_cache");
            //mCacheDir = new File("/sdcard/", "magic_audio_cache");
            if (!mCacheDir.exists()) {
                mCacheDir.mkdirs();
            }
            // clear temp file
            if (mCacheDir.isDirectory()) {
                for (File file : mCacheDir.listFiles()) {
                    if (file.isFile() && file.getName().contains(TMP_SUFFIX)) {
                        YYLog.info(TAG, " clear cache tmp file " + file.getPath());
                        file.delete();
                    }
                }
            }
        }
    }

    public String getCacheFilePath(String path, int samplerate, int channels) {
        File dir = mCacheDir;
        if (dir == null) {
            return null;
        }
        String[] infos = path.split(File.separator);
        String name;
        if (infos.length > 0) {
            name = infos[infos.length - 1];
        }else {
            return null;
        }

        //处理前摇，后摇，中摇其中有空的情况
        if (TextUtils.isEmpty(name)) {
            return null;
        }

        int pos = name.lastIndexOf(".");
        int hashCode = path.hashCode();
        name = Integer.toString(hashCode) + "_" + name.substring(0, pos);
        samplerate = samplerate / 1000;
        File cacheFile = new File(dir, name + samplerate + "s" + channels + "c" + ".wav");
        String cacheFilePath = cacheFile.getPath();
        return cacheFilePath;
    }

    public String getCacheTmpFilePath(String path, int samplerate, int channels) {
        String cachePath = getCacheFilePath(path, samplerate, channels);
        File cacheTmpFile = new File(cachePath + TMP_SUFFIX + mMagicNumber++);
        return cacheTmpFile.getPath();
    }

    public String finishCache(String tmpPath ) {
        int pos = tmpPath.lastIndexOf(".");
        String cachePath = tmpPath.substring(0, pos);
        YYLog.info(TAG, " finishCache " + cachePath + " >> " + tmpPath);
        File cacheFile = new File(cachePath);
        File tmpFile = new File(tmpPath);
        if (cacheFile.exists()) {
            tmpFile.delete();
        }else {
            if (tmpFile.exists()) {
                tmpFile.renameTo(cacheFile);
            }
        }

        return cachePath;
    }
}
