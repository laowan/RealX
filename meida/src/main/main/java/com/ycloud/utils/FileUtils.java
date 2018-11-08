package com.ycloud.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.ycloud.common.Dev_MountInfo;
import com.ycloud.common.Dev_MountInfo.DevInfo;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

public class FileUtils {
	static private String TAG = "FileUtils";
	private static String mAppDiskCacheDir;
	public static final String[] SNAPSHOT_EXTENSIONS = { "bmp", "jpg", "jpeg", "png" };
	public static final String[] FILTER_VIDEO_EXTENSIONS = { "mp4", "ts" };
	public static final String[] FILTER_IMG_EXTENSIONS = { "png", "jpg", "jpeg", "bmp", "gif" };
	public static final String[] CONCAT_VIDEO_EXTENSIONS = { "mp4", "ts", "mp3", "wav" };
	public static final String[] TRANSCODE_AUDIO_EXTRACE_EXTENSIONS = { "mp3", "wav" };
	public static final String[] VIDEO_SUPPORT_EXTENSIONS = { "mov", "mp4", "3gp" };
	private static final HashSet<String> mHashSnapshot;
	private static final HashSet<String> mHashFilterVideo;
	private static final HashSet<String> mHashFilterImg;
	private static final HashSet<String> mHashConcatVideo;
	private static final HashSet<String> mHashTranscodeAudioExtract;
	private static final HashSet<String> mHashSupportVideoType;
	static {
		mHashSnapshot = new HashSet<String>(Arrays.asList(SNAPSHOT_EXTENSIONS));
		mHashFilterVideo = new HashSet<String>(Arrays.asList(FILTER_VIDEO_EXTENSIONS));
		mHashFilterImg = new HashSet<String>(Arrays.asList(FILTER_IMG_EXTENSIONS));
		mHashConcatVideo = new HashSet<String>(Arrays.asList(CONCAT_VIDEO_EXTENSIONS));
		mHashTranscodeAudioExtract = new HashSet<String>(Arrays.asList(TRANSCODE_AUDIO_EXTRACE_EXTENSIONS));
		mHashSupportVideoType = new HashSet<String>(Arrays.asList(VIDEO_SUPPORT_EXTENSIONS));
	}

	public static boolean isVideoTypeSupport(String extension) {
		return mHashSupportVideoType.contains(extension);
	}

	public static boolean isSnapshotSupport(String extension) {
		return mHashSnapshot.contains(extension);
	}

	public static boolean isConcatVideoSupport(String path) {
		String extension = getPathExtension(path);
		return mHashConcatVideo.contains(extension);
	}

	public static boolean isFilterVideoSupport(String path) {
		String extension = getPathExtension(path);
		return mHashFilterVideo.contains(extension);
	}

	public static boolean isFilterImgSupport(String path) {
		String extension = getPathExtension(path);
		return mHashFilterImg.contains(extension);
	}

	public static boolean isTranscodeAudioExtractSupport(String path) {
		String extension = getPathExtension(path);
		return mHashTranscodeAudioExtract.contains(extension);
	}

	public static boolean isMp4OrTs(String path) {
		String extension = getPathExtension(path);
		return (extension.equalsIgnoreCase("mp4") || extension.equalsIgnoreCase("ts"));
	}

	public static boolean createDir(String path) {
		File file = new File(path);
		boolean mk = true;
		if (!file.exists()) {
			mk = file.mkdirs();
		}
		return mk;
	}

	public static boolean createFile(String path) {
		if (path == null) {
			YYLog.error(TAG, " createFile Error, path is null");
		}

		File file = new File(path);
		boolean mk = false;
		if (!file.exists()) {
			try {
				mk = file.createNewFile();
			} catch (IOException e) {
				YYLog.info(TAG, path + " createFile Error");
				e.printStackTrace();
			}
		} else
			return true;
		return mk;
	}

	/** 检测文件是否可用 */
	public static boolean checkFile(String path) {
		if (!TextUtils.isEmpty(path)) {
			File f = new File(path);
			if (f.isFile())
				return true;
		}
		return false;
	}

	/** 检测文件是否可用 */
	public static boolean checkFile(File f) {
		if (f != null && f.exists() && f.canRead() && (f.isDirectory() || (f.isFile() && f.length() > 0))) {
			return true;
		}
		return false;
	}

	/** 检测文件是否可用 */
	public static boolean checkPath(String path) {
		if (!TextUtils.isEmpty(path)) {
			File f = new File(path);
			if (f != null && f.exists() && f.canRead() && (f.isDirectory() || (f.isFile() && f.length() > 0)))
				return true;
		}
		YYLog.error(TAG, "File " + path + " not Exist! ");
		return false;
	}

	public static String getPathExtension(String path) {
		if (!TextUtils.isEmpty(path)) {
			int i = path.lastIndexOf('.');
			if (i > 0 && i < path.length() - 1) {
				return path.substring(i + 1).toLowerCase();
			}
		}
		return "";
	}

	static public String getFileDir() {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = null;
		if (Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
			mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "");
		} else {
			Dev_MountInfo dev = Dev_MountInfo.getInstance();
			DevInfo info = dev.getInternalInfo();// Internal SD Card Informations
			info = dev.getExternalInfo();// External SD Card Informations
			if (null != info)
				mediaStorageDir = new File(info.getPath() + "/Movies/");
			else
				mediaStorageDir = new File("/mnt/sdcard2/Movies/");
		}
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d(TAG, "failed to create directory");
				return null;
			}
		}

		return mediaStorageDir.getPath();
	}

	public static void deleteDir(String filepath) {
		File dirf = new File(filepath);
		if (dirf.isDirectory()) {
			File[] childs = dirf.listFiles();
			for (int i = 0; i < childs.length; i++) {
				deleteDir(childs[i].getAbsolutePath());
			}
		}
		dirf.delete();
	}

	public void deleteFileOrDirSafely(File file) {
		if (file.isFile()) {
			deleteFileSafely(file);
			return;
		}
		if (file.isDirectory()) {
			File[] childFiles = file.listFiles();
			if (childFiles == null || childFiles.length == 0) {
				deleteFileSafely(file);
				return;
			}
			for (int i = 0; i < childFiles.length; i++) {
				deleteFileOrDirSafely(childFiles[i]);
			}
			deleteFileSafely(file);
		}
	}

	/**
	 * 安全删除文件.
	 * 
	 * @param file
	 * @return
	 */
	public static boolean deleteFileSafely(File file) {
		if (file != null) {
			String tmpPath = file.getParent() + File.separator + System.currentTimeMillis();
			File tmp = new File(tmpPath);
			file.renameTo(tmp);
			return tmp.delete();
		}
		return false;
	}

	public static void copyFile(String sourceDir, String targetDir) {
		File fileIn = new File(sourceDir);
		FileInputStream in = null;
		FileChannel fileChannelIn = null;
		FileOutputStream out = null;
		FileChannel fileChannelout = null;
		try {
			in = new FileInputStream(fileIn);
			fileChannelIn = in.getChannel();

			File fileOut = new File(targetDir);
			if (!fileOut.exists()) {
				if (fileOut.isDirectory()) {
					fileOut.mkdirs();
				} else {
					fileOut.getParentFile().mkdirs();
					fileOut.createNewFile();
				}
			}
			out = new FileOutputStream(new File(targetDir));
			fileChannelout = out.getChannel();

			ByteBuffer buffer = ByteBuffer.allocate(1024 * 2);

			while (true) {
				buffer.clear();
				int r = fileChannelIn.read(buffer);

				if (r <= 0) {
					break;
				}

				buffer.flip();
				fileChannelout.write(buffer);
			}
		} catch (IOException e) {

			e.printStackTrace();

		} finally {

			try {
				in.close();
				fileChannelIn.close();
				out.close();
				fileChannelout.close();

				in = null;
				fileChannelIn = null;
				out = null;
				fileChannelout = null;

			} catch (IOException e) {

				e.printStackTrace();

			}
		}
	}

	public static String getOutputMediaFileName(int type) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = null;
		if (Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
			mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "");
		} else {
			Dev_MountInfo dev = Dev_MountInfo.getInstance();
			DevInfo info = dev.getInternalInfo();// Internal SD Card Informations
			info = dev.getExternalInfo();// External SD Card Informations
			if (null != info)
				mediaStorageDir = new File(info.getPath() + "/Movies/");
			else
				mediaStorageDir = new File("/mnt/sdcard2/Movies/");
		}
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d("CameraSample", "failed to create directory");
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
		String mediaFile;
		if (type == MEDIA_TYPE_AUDIO) {
			mediaFile = mediaStorageDir.getPath() + File.separator + "AUD_" + timeStamp;
		} else if (type == MEDIA_TYPE_VIDEO) {
			mediaFile = mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp;
		} else {
			return null;
		}

		return mediaFile;
	}

	public static int MEDIA_TYPE_AUDIO = 0;
	public static int MEDIA_TYPE_VIDEO = 1;

	public static void writeFileSdcard(String fileName, String message) {
		FileOutputStream fout = null;
		try {
			fout = new FileOutputStream(fileName);
			byte[] bytes = message.getBytes();
			fout.write(bytes);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (null != fout) {
				try {
					fout.close();
					fout = null;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public static void writeFileSdcard(String fileName, String[] array) {
		File file = null;
		BufferedWriter bw = null;
		file = new File(fileName);
		if (!file.exists() != false) {
			try {
				file.createNewFile();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			bw = new BufferedWriter(new FileWriter(file));
			for (int i = 0; i < array.length; i++) {
				bw.write(array[i]);
				bw.newLine();
				bw.flush();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				bw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void contentToTxt(String filePath, String content) {
		String str = new String(); // 原有txt内容
		String s1 = new String();// 内容更新
		try {
			File f = new File(filePath);
			if (f.exists()) {
				System.out.print("文件存在");
			} else {
				System.out.print("文件不存在");
				f.createNewFile();// 不存在则创建
			}
			BufferedReader input = new BufferedReader(new FileReader(f));

			while ((str = input.readLine()) != null) {
				s1 += str + "\n";
			}
			System.out.println(s1);
			input.close();
			s1 += content;

			BufferedWriter output = new BufferedWriter(new FileWriter(f));
			output.write(s1);
			output.close();
		} catch (Exception e) {
			e.printStackTrace();

		}
	}

	public static double round(double d, int len) {
		BigDecimal b1 = new BigDecimal(d);
		BigDecimal b2 = new BigDecimal(1);
		// 四舍五入的操作
		return b1.divide(b2, len, BigDecimal.ROUND_HALF_UP).doubleValue();
	}

	public static double roundDown(double d, int len) {
		BigDecimal b1 = new BigDecimal(d);
		BigDecimal b2 = new BigDecimal(1);
		// 不进行四舍五入的操作，直接去掉尾数
		return b1.divide(b2, len, BigDecimal.ROUND_DOWN).doubleValue();
	}

	public static boolean isExternalStorageRemovable() {
		if (hasGingerbread())
			return Environment.isExternalStorageRemovable();
		else
			return Environment.MEDIA_REMOVED.equals(Environment.getExternalStorageState());
	}

	public static boolean hasGingerbread() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
	}

	public static final String[] EXT_SDCARD_PATHS = { "/sdcard/", "/mnt/extSdCard", "/storage/sdcard1", "/storage/external_storage/sda1", "/storage/ext_sd", "/mnt/sdcard/sdcard1",
			"/mnt/sdcard2", "/mnt/D", "/mnt/sdcard/extern_sd", "/mnt/sdcard/extStorages/SdCard", "/mnt/sdcard/external-sd", "/mnt/sdcard/sdcard2", "/mnt/extern-sd",
			"/mnt/ext_sdcard", "/mnt/external1" };

	public static String getDiskCacheDir(Context context) {
		File file = null;
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !isExternalStorageRemovable()) {
			file = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
			if (file == null)
				file = context.getExternalCacheDir();
		} else {
			file = context.getFilesDir();
			if (file == null)
				file = context.getCacheDir();
		}

		if (file == null) {
			File tmpFile;
			for (int i = 0; i < EXT_SDCARD_PATHS.length; i++) {
				tmpFile = new File(EXT_SDCARD_PATHS[i]);
				if (tmpFile.exists() && tmpFile.canWrite()) {
					file = tmpFile;
				}
			}
			if (file != null) {
				String pkgName = context.getPackageName();
				// String pkgPath = context.getPackageCodePath();
				file = new File(file.getPath(), "Android/data/" + pkgName);
				if (!file.exists()) {
					file.mkdir();
				}
			}
		}
		return file == null ? null : file.getAbsolutePath();
	}

	public static boolean compressBitmap(Bitmap bm, Bitmap.CompressFormat format, String localPath, int qulity) {
        try {
            FileOutputStream fos = new FileOutputStream(localPath);
            boolean ret = bm.compress(format, qulity, fos);
            fos.flush();
            fos.close();
            return ret;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
	
	public static File getFileFromBytes(byte[] b, String outputFile){
        BufferedOutputStream stream = null;
        File file = null;
        try {
            file = new File(outputFile);
            FileOutputStream fstream = new FileOutputStream(file);
            stream = new BufferedOutputStream(fstream);
            stream.write(b);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (stream != null) {
                try{
                    stream.close();
                } catch (IOException e1){
                    e1.printStackTrace();
                }
            }
        }
        return file;
    }
	/**
	 * 获取内置SD卡路径
	 * @return
	 */
	public static String getInnerSDCardPath() {
		return Environment.getExternalStorageDirectory().getPath();
	}

	/**
	 * 获取app的缓存目录
	 * @return
	 */
	public static String getAppDiskCacheDir(Context context) {
		if (context == null)
			return mAppDiskCacheDir;
		if (TextUtils.isEmpty(mAppDiskCacheDir))
			mAppDiskCacheDir = getDiskCacheDir(context);
		return mAppDiskCacheDir;
	}

	public static boolean externalStorageExist() {
		boolean ret = false;
		String state = Environment.getExternalStorageState();
		if (state != null) {
			ret = state.equalsIgnoreCase(Environment.MEDIA_MOUNTED);
		}
		return ret;
	}
}
