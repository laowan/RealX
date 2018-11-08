package com.ycloud.utils;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class LogToES {


    public static final String DEFAULT_LOG_PATH = "/yymediarecordersdk";
    public static final String DEFAULT_LOG_NAME = "yymediarecorder_sdk_log.txt";
	
	private static String TAG = "LogToES";
	private static final String BAK_EXT = ".bak";
    public static final int MAX_FILE_SIZE = 2;// M bytes

    // SimpleDateFormat非线程安全
	// private static final SimpleDateFormat logFormater = new SimpleDateFormat("yyyy:MM:dd kk:mm:ss.SSS");
    private static final ThreadLocal<DateFormat> logFormater = new ThreadLocal<DateFormat>() {

        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy:MM:dd kk:mm:ss.SSS", Locale.getDefault());
        }
    };

    /** 10 days. */
    private static final long DAY_DELAY = 10L * 24 * 60 * 60 * 1000;
    /** Back file num limit, when this is exceeded, will delete older logs. */
    private static final int BAK_FILE_NUM_LIMIT = 2;

    private static String s_LogPath = null;
    private static String s_AbsoluteLogPath = null;
    
	public static String getLogPath() {
        return (s_LogPath == null ? DEFAULT_LOG_PATH: s_LogPath);
	}

    public static String getAbsolutionLogPath() {
        return s_AbsoluteLogPath;
    }

    //add sdcard prefix
    public static void setLogPath(String logPath) {
        s_LogPath = File.separator +logPath;
        File esdf = Environment.getExternalStorageDirectory();
        s_AbsoluteLogPath = esdf.getAbsolutePath() +s_LogPath;
    }

	public synchronized static void writeLogToFile(String path, String fileName, String msg)
			throws IOException {
		writeLog(path, fileName, msg);
	}

    public synchronized static void writeLogToCacheFile(String path, String fileName, String msg)
            throws IOException {
        writeLog(path, fileName, msg);
    }

    public static void writeLog(String path, String fileName, String msg) throws IOException {
        Date date = new Date();
        File dirFile = new File(path);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        File logFile = new File(path + File.separator + fileName);
        if (!logFile.exists()) {
            try {
                Log.i(TAG, "try to create new log file:"+logFile.toString() + " path:"+path + " fileName:"+fileName);
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "create new log file fail:"+e.toString());
                return;
            }
        } else {
              long fileSize = (logFile.length() >>> 20);// convert to M bytes
            if (fileSize > MAX_FILE_SIZE) {
                deleteOldLogs();

                SimpleDateFormat simpleDateFormate = new SimpleDateFormat("-MM-dd-kk-mm-ss", Locale.getDefault());
                String fileExt = simpleDateFormate.format(date);

                StringBuilder sb = new StringBuilder(path);
                sb.append(File.separator).append(fileName).append(fileExt)
                        .append(BAK_EXT);

                File fileNameTo = new File(sb.toString());
                logFile.renameTo(fileNameTo);
                
                Log.d("LogToES", "LogToES log keep volume.");
                limitVolume();
            }
        }

        
        String strLog = logFormater.get().format(date);

        StringBuilder sb = new StringBuilder(strLog);
        sb.append(' ');
        sb.append(msg);
        sb.append('\n');
        strLog = sb.toString();

        // we can make FileWriter static, but when to close it
        FileWriter fileWriter = new FileWriter(logFile, true);
        fileWriter.write(strLog);
        fileWriter.flush();

        fileWriter.close();
    }

    private static void deleteOldLogs() {
        File esdf = Environment.getExternalStorageDirectory();
        if (!esdf.exists()) {
            return;
        }
        String dir = esdf.getAbsolutePath() + getLogPath();
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            return;
        }

        long now = System.currentTimeMillis();
        File files[] = dirFile.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().endsWith(BAK_EXT)) {
                long lastModifiedTime = file.lastModified();
                if (now - lastModifiedTime > DAY_DELAY) {
                    file.delete();
                }
            }
        }
    }
    
    private static void limitVolume() {
        File esdf = Environment.getExternalStorageDirectory();
        if (!esdf.exists()) {
            return;
        }
        String dir = esdf.getAbsolutePath() + getLogPath();
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            return;
        }

        final File files[] = dirFile.listFiles();
        if (files == null || files.length <= Math.max(0, BAK_FILE_NUM_LIMIT)) {
            return;
        }

        int numOfDeletable = 0;
        for (int i = 0, N = files.length; i < N; i++) {
            File file = files[i];
            if (file.getName().endsWith(BAK_EXT)) {
                ++numOfDeletable;
            }
        }

        if (numOfDeletable <= 0) {
            // really weird, the naming rule have been changed!
            // this function won't work anymore.
            return;
        }
        
        Log.d("LogToES",
            "LogToES there ARE " + numOfDeletable + " deletables.");
        

        // the logs.txt and uncaught_exception.txt may be missing,
        // so just allocate same size as the old.
        File[] deletables = new File[numOfDeletable];
        int i = 0;
        for (File e : files) {
            if (i >= numOfDeletable) {
                // unexpected case.
                break;
            }
            if (e.getName().endsWith(BAK_EXT)) {
                deletables[i++] = e;
            }
        }

        deleteIfOutOfBound(deletables);
    }

    private static void deleteIfOutOfBound(File[] files) {
        if (files.length <= BAK_FILE_NUM_LIMIT) {
            return;
        }
        
        // sort files by create time(time is on the file name) DESC.
        Comparator<? super File> comparator = new Comparator<File>() {

            @Override
            public int compare(File lhs, File rhs) {
                return rhs.getName().compareTo(lhs.getName());
            }
            
        };
        
        Arrays.sort(files, comparator);
        
        final int filesNum = files.length;
        
        for (int i = 0; i < BAK_FILE_NUM_LIMIT; ++i) {
            Log.d("LogToES", "LogToES keep file " + files[i]);
        }
        
        // delete files from index to size.
        for (int i = BAK_FILE_NUM_LIMIT; i < filesNum; ++i) {
            File file = files[i];
            if (!file.delete()) {
                // NOTE here we cannot call YLog, we are to be depended by YLog.
                Log.e("LogToES", "LogToES failed to delete file " + file);
            }       
            else {
                Log.d("LogToES", "LogToES delete file " + file);
            }
        }
    }
	
	private static final SimpleDateFormat FILE_NAME_FORMAT = new SimpleDateFormat(
			"MM-dd_HH-mm-ss", Locale.getDefault());
	private static final String LOGCAT_CMD[] = { "logcat", "-d", "-v", "time" };

	// to use this method, we should add permission(android.permission.READ_LOGS) in Manifest
	public static void writeAllLogsToFile() {
		new Thread(new Runnable() {
			
			public void run() {
				try {
					Date date = new Date();
					Process process = Runtime.getRuntime().exec(LOGCAT_CMD);
					BufferedReader bufferedReader = new BufferedReader(
							new InputStreamReader(process.getInputStream()),
							1024);
					StringBuilder sb = new StringBuilder();
					String line;
					while ((line = bufferedReader.readLine()) != null) {
						sb.append(line);
						sb.append(System.getProperty("line.separator"));
					}
					bufferedReader.close();
					
					File esdf = Environment.getExternalStorageDirectory();
					String dir = esdf.getAbsolutePath() + getLogPath();
					File dirFile = new File(dir);
					if (!dirFile.exists()) {
						dirFile.mkdirs();
					}
					String fileName = dir + File.separator + FILE_NAME_FORMAT.format(date) + ".log";
					File file = new File(fileName);
					if (!file.exists()) {
						file.createNewFile();
					}
					FileOutputStream fos = new FileOutputStream(file);
					fos.write(sb.toString().getBytes());
					fos.flush();
					fos.close();
				} catch (IOException e) {
					Log.e("yy", "Failed to writeAllLogsToFile " + e);
				}
			}
		}).start();
	}
}
