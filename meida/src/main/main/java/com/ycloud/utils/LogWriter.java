package com.ycloud.utils;

/**
 * Created by zhangbin on 2016/11/16.
 */

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


 class LogWriter {

    private static final String TAG = LogWriter.class.getSimpleName();
    private static String PATH ="/sdcard/yymediarecordersdk/yymediarecorder_sdk_log.txt";

     private static final String DEBUG ="D";
     private static final String INFO = "I";
     private static final String WARN ="W";
     private static final String ERROR="E";
     private static final String VERBOSE="V";

     public static int e(String tag, String message) {
         logToFile(ERROR,tag, message);
         return 1;
     }

     public static void v(String tag, String message) {
         logToFile(VERBOSE,tag, message);
     }

     public static int d(String tag, String message) {
         logToFile(DEBUG,tag, message);
         return 1;
     }

     public static int i(String tag, String message) {
         logToFile(INFO,tag, message);
         return 1;
     }

     public static int w(String tag, String message) {
         logToFile(WARN,tag, message);
         return 1;
     }

    private static String getDateTimeStamp() {
        Date dateNow = Calendar.getInstance().getTime();
        return (DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.US).format(dateNow));
    }

    private static void logToFile(String logLevel,String tag, String message) {
        try {
            File logFile = new File(PATH);
            if (!logFile.exists()) {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            }

            if (logFile.length() > 2097152) { // 2 MB
                logFile.delete();
                logFile.createNewFile();
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            StringBuilder sb = new StringBuilder();
            sb.append(getDateTimeStamp()+" ");
            sb.append(logLevel+"/"+tag+": ");
            sb.append(message+"\r\n");
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Unable to log exception to file.", e);
        }
    }
}