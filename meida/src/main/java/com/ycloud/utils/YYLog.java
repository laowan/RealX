package com.ycloud.utils;

import android.os.Process;
import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class YYLog {

   private static final String DEFAULT_LOG_NAME = "yymrsdk_log.txt";

    private static final String DEFAULT_LOG_PATH="yymobile/logs/sdklog";

	private static ILog mLogger = null;
	private static boolean mIsDebug = false;
	private static boolean mIsSaveToFile = false;
	private static String mLogPath = "yymobile/logs/sdklog";
	
    private static String tag() {
        return "[ymrsdk]";
    }
    
    public static boolean isDebuggable() {
        return mIsDebug;
    }
    
    private static boolean isSaveToFile() {
    	return mIsSaveToFile;
    }

    static {
        setFilePath(DEFAULT_LOG_PATH);
    }

    /**
     *  sdk单独打日志文件的路径，logPath/mnt/sdcard之后的目录路径, 譬如说videosdk, 则
     *  日志文件目录为/sdcard/videosdk, 如果注册了日志回调，可以设置为null
     * @param path
     */
    public static void setFilePath(String path) {
    	mLogPath = path;
        LogToES.setLogPath(path);
    }
    //设置true 打印包括debug所有日志
    public static void setDebug(boolean enable) {
    	//mIsDebug = enable;
    }
    
    //设置true将日志保存到文件中，只影响java层日志
    public static void setSaveToFile(boolean enable) {
    	mIsSaveToFile = enable;
    }
    
    //UI注册logger对象
    public static void registerLogger(Object logger) {
    	mLogger = (ILog)logger;
    }
    /**
     * Output verbose debug log.
     * The log will only be output onto DDMS, not onto file.
     * This version aims to improve performance by
     * removing the string concatenated costs on release version.
     * Exception will be caught if input arguments have format error.
     * @param obj
     * @param format    The format string such as "This is the %d sample : %s".
     * @param args      The args for format.
     *
     * Reference :
     * boolean : %b.
     * byte, short, int, long, Integer, Long : %d.
     *    NOTE %x for hex. 
     * String : %s.
     * Object : %s, for this occasion, toString of the object will be called,
     * and the object can be null - no exception for this occasion.
     *
     */
    public static void verbose(Object obj, String format, Object... args) {
        if (isDebuggable()) {
            try {
                String msg = String.format(format, args);
//                int line = getCallerLineNumber();
//                String filename = getCallerFilename();
                int line = -1;
                String filename = "";
                String logText = msgForTextLog(obj, filename, line, msg);
                if(mLogger!=null) {
                	mLogger.verbose(tag(), logText);
                }
                else{
                	Log.v(tag(), logText);
                }
            }catch (java.util.IllegalFormatException e) {
                Log.e(tag(), "IllegalFormatException happened: ", e);
            } catch (NullPointerException e){
                Log.e(tag(), "NullPointerException happened: ", e);
            }
        }
    }

    public static void verbose(Object obj, String msg) {
        if (isDebuggable()) {
//            int line = getCallerLineNumber();
//            String filename = getCallerFilename();
            int line = -1;
            String filename = "";
            String logText = msgForTextLog(obj, filename, line, msg);
            if(mLogger!=null) {
            	mLogger.verbose(tag(), logText);
            }
            else{
            	Log.v(tag(), logText);
            }
        }
    }

    /**
     * Output debug log.
     * This version aims to improve performance by
     * removing the string concatenated costs on release version.
     * Exception will be caught if input arguments have format error.
     * 
     * The log will only be output onto both DDMS and file for debug version.
     * It does nothing for release version.
     * 
     * @param obj
     * @param format    The format string such as "This is the %d sample : %s".
     * @param args      The args for format.
     *
     * Reference :
     * boolean : %b.
     * byte, short, int, long, Integer, Long : %d.
     *    NOTE %x for hex. 
     * String : %s.
     * Object : %s, for this occasion, toString of the object will be called,
     * and the object can be null - no exception for this occasion.
     *
     */
    public static void debug(Object obj, String format, Object... args) {
        if (isDebuggable()) {
            try {
                String msg = String.format(format, args);
                int line = getCallerLineNumber();
                String filename = getCallerFilename();
                String logText = msgForTextLog(obj, filename, line, msg);
                if(mLogger!=null) {
                	mLogger.debug(tag(), logText);
                }
                else{
                	Log.d(tag(), logText);
                }
                if (isSaveToFile() && FileUtils.externalStorageExist())
                    logToFile(logText);
            }catch (java.util.IllegalFormatException e) {
                Log.e(tag(), "IllegalFormatException happened: ", e);
            } catch (NullPointerException e){
                Log.e(tag(), "NullPointerException happened: ", e);
            }
        }
    }

    public static String debug(Object obj, String msg) {
        if (isDebuggable()) {
            int line = getCallerLineNumber();
            String filename = getCallerFilename();
            String logText = msgForTextLog(obj, filename, line, msg);
            if(mLogger!=null) {
            	mLogger.debug(tag(), logText);
            }
            else{
                Log.d(tag(), logText);
            }
        }
        return msg;
    }

    public static void debug(Object obj, Throwable t) {
        if (isDebuggable()) {
            int line = getCallerLineNumber();
            String filename = getCallerFilename();
            String methodname = getCallerMethodName();
            String logText = msgForException(obj, methodname, filename, line);
            if(mLogger!=null) {
            	mLogger.debug(tag(), logText);
            }
            else{
                Log.d(tag(), logText, t);
            }
            if (isSaveToFile() && FileUtils.externalStorageExist())
                logToFile(logText, t);
        }
    }

    /**
     * Output information log.
     * Exception will be caught if input arguments have format error.
     * @param obj
     * @param format    The format string such as "This is the %d sample : %s".
     * @param args      The args for format.
     * 
     * Reference : 
     * boolean, Boolean : %b or %B.
     * byte, short, int, long, Integer, Long : %d.
     * String : %s.
     * Object : %s, for this occasion, toString of the object will be called,
     * and the object can be null - no exception for this occasion.
     * 
     */
    public static void info(Object obj, String format, Object... args) {
        try {
            String msg = String.format(format, args);
//            int line = getCallerLineNumber();
//            String filename = getCallerFilename();
            int line = -1;
            String filename = "";
            String logText = msgForTextLog(obj, filename, line, msg);
            if(mLogger!=null) {
            	mLogger.info(tag(), logText);
            }
            else{
                Log.i(tag(), logText);
                logToFile(logText);
            }
        }catch (java.util.IllegalFormatException e) {
            Log.e(tag(), "IllegalFormatException happened: ", e);
        } catch (NullPointerException e){
            Log.e(tag(), "NullPointerException happened: ", e);
        }
    }

    public static void info(Object obj, String msg) {
//        int line = getCallerLineNumber();
//        String filename = getCallerFilename();
        int line = -1;
        String filename = "";
        String logText = msgForTextLog(obj, filename, line, msg);
        if(mLogger!=null) {
        	mLogger.info(tag(), logText);
        }
        else{
            Log.i(tag(), logText);
            logToFile(logText);
        }
    }

    public static void info(String clz, String msg) {
//        int line = getCallerLineNumber();
//        String filename = getCallerFilename();
        int line = -1;
        String filename = "";
        String logText = msgForTextLog(clz, filename, line, msg);
        if(mLogger!=null) {
            mLogger.info(tag(), logText);
        }
        else{
            Log.i(tag(), logText);
            logToFile(logText);
        }
    }
    
    public static void warn(Object obj, String format, Object... args) {
        try {
            String msg = String.format(format, args);
//            int line = getCallerLineNumber();
//            String filename = getCallerFilename();
            int line = -1;
            String filename = "";
            String logText = msgForTextLog(obj, filename, line, msg);
            if(mLogger!=null) {
            	mLogger.warn(tag(), logText);
            }
            else{
                Log.w(tag(), logText);
            }
        }catch (java.util.IllegalFormatException e) {
            Log.e(tag(), "IllegalFormatException happened: ", e);
        } catch (NullPointerException e){
            Log.e(tag(), "NullPointerException happened: ", e);
        }
    }
    
    public static void warn(Object obj, String msg) {
//        int line = getCallerLineNumber();
//        String filename = getCallerFilename();
        int line = -1;
        String filename = "";
        String logText = msgForTextLog(obj, filename, line, msg);
        if(mLogger!=null) {
        	mLogger.warn(tag(), logText);
        }
        else{
            Log.w(tag(), logText);
        }
    }

    public static void error(Object obj, String msg) {
        int line = getCallerLineNumber();
        String filename = getCallerFilename();
/*        int line = -1;
        String filename = "";*/
        String logText = msgForTextLog(obj, filename, line, msg);
        if(mLogger!=null) {
            mLogger.error(tag(), logText);
        }
        else{
            Log.e(tag(), logText);
            logToFile(logText);
        }

    }

    public static void error(Object obj, String format, Object... args) {
        try{
            String msg = String.format(format, args);
            int line = getCallerLineNumber();
            String filename = getCallerFilename();
/*            int line = -1;
            String filename = "";*/
            String logText = msgForTextLog(obj, filename, line, msg);
            if(mLogger != null){
            	mLogger.error(tag(), logText);
            }
            else{
               	Log.e(tag(), logText);
            	logToFile(logText);
            }
        } catch (java.util.IllegalFormatException e) {
            Log.e(tag(), "IllegalFormatException happened: ", e);
        } catch (NullPointerException e){
            Log.e(tag(), "NullPointerException happened: ", e);
        }
    }

    public static void error(Object obj, Throwable t) {
//        int line = getCallerLineNumber();
//        String filename = getCallerFilename();
//        String methodname = getCallerMethodName();
        int line = -1;
        String filename = "";
        String methodname = "";
        String logText = msgForException(obj, methodname, filename, line);
        if(mLogger!=null) {
        	mLogger.error(tag(), logText, t);
        }
        else{
            Log.e(tag(), logText, t);
            logToFile(logText);
        }       
    }


    /**
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int w(String tag, String  msg ){
       warn(tag, msg);
        return 0;
    }

    /**
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int i(String tag, String  msg){
        info(tag, msg);
        return 0;
    }


    /**
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int e(String tag, String  msg ){
       error(tag, msg);
        return 0;
    }

    /**
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int d(String tag, String msg) {
        debug(tag, msg);
        return 0;
    }



    private static String objClassName(Object obj) {
        if (obj instanceof String)
            return (String) obj;
        else
            return obj.getClass().getSimpleName();
    }

    private static long LEN_128K = ((1 << 10) * 128); // 
    private static long LEN_256K = ((1 << 10) * 256); // 

    private static void logToFile(final String logText) {
        writeToLog(logText);
    }
    
    private static ExecutorService mSingleThreadPool = Executors.newSingleThreadExecutor();

    private static void writeToLog(final String logText) {
    	if(mLogPath == null) {
    		return;
    	}

        mSingleThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                //if (BasicFileUtils.externalStorageExist()) { // it have performance issue
                    try {
                        LogToES.writeLogToFile(LogToES.getAbsolutionLogPath(), DEFAULT_LOG_NAME, logText);
                    }
                    catch (Throwable e) {
                        Log.e("YLogs", "writeLogToFile fail, " + e);
                    }
                //}
            }            
        }); 
    }

    private static void logToFile(String logText, Throwable t) {
        StringWriter sw = new StringWriter();
        sw.write(logText);
        sw.write("\n");
        t.printStackTrace(new PrintWriter(sw));
        writeToLog(sw.toString());
    }

    private static String msgForException(Object obj, String methodname, String filename, int line) {
        StringBuilder sb = new StringBuilder();
        if (obj instanceof String)
            sb.append((String) obj);
        else
            sb.append(obj.getClass().getSimpleName());
        sb.append(" Exception occurs at ");
        sb.append("(P:");
        sb.append(Process.myPid());
        sb.append(")");
        sb.append("(T:");
        sb.append(Thread.currentThread().getId());
        sb.append(") at ");
        sb.append(methodname);
        sb.append(" (");
        sb.append(filename);
        sb.append(":");
        sb.append(line);
        sb.append(")");
        String ret = sb.toString();
        return ret;
    }

    private static String msgForTextLog(Object obj, String filename, int line, String msg) {
        StringBuilder sb = new StringBuilder();
        //add tags
        sb.append(tag());
        sb.append(msg);
        sb.append("(P:");
        sb.append(Process.myPid());
        sb.append(")");
        sb.append("(T:");
        sb.append(Thread.currentThread().getId());
        sb.append(")");
        sb.append("(C:");
        sb.append(objClassName(obj));
        sb.append(")");
        sb.append("at (");
        sb.append(filename);
        sb.append(":");
        sb.append(line);
        sb.append(")");
        return sb.toString();
    }


    /** 默认增加sdk的总的tag*/
    private static String msgForTextLog(String tag, String filename, int line, String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ymrsdk]");
        sb.append(msg);
        sb.append("(P:");
        sb.append(Process.myPid());
        sb.append(")");
        sb.append("(T:");
        sb.append(Thread.currentThread().getId());
        sb.append(")");
        sb.append("(C:");
        sb.append(tag);
        sb.append(")");
        sb.append("at (");
        sb.append(filename);
        sb.append(":");
        sb.append(line);
        sb.append(")");
        String ret = sb.toString();
        return ret;
    }

    private static int getCallerLineNumber() {
        return Thread.currentThread().getStackTrace()[4].getLineNumber();
    }

    private static String getCallerFilename() {
        return Thread.currentThread().getStackTrace()[4].getFileName();
    }

    private static String getCallerMethodName() {
        return Thread.currentThread().getStackTrace()[4].getMethodName();
    }
    
    public static void printThreadStacks() {
        printThreadStacks(tag(), getThreadStacksKeyword(), false, false);
    }
    
    public static void printThreadStacks(String tag) {
        printThreadStacks(tag, getThreadStacksKeyword(), false, false);
    }
    
    public static void printThreadStacks(String tag, String keyword) {
        printThreadStacks(tag, keyword, false, false);
    }
    
    public static void printThreadStacks(String tag, String keyword, boolean fullLog, boolean release) {
        printStackTraces(Thread.currentThread().getStackTrace(), tag, keyword, fullLog, release);
    }
    
    private static AtomicReference<String> mThreadStacksKeyword = new AtomicReference<String>("com.duowan.mobile");
    
    public static void setThreadStacksKeyword(String keyword) {
        mThreadStacksKeyword.set(keyword);
    }
    
    public static String getThreadStacksKeyword() {
        return mThreadStacksKeyword.get();
    }
    
    public static void printStackTraces(StackTraceElement[] traces, String tag) {
        printStackTraces(traces, tag, getThreadStacksKeyword(), false, false);
    }

    public static void printStackTraces(StackTraceElement[] traces, String tag, String keyword,
                                        boolean fullLog, boolean release) {
        printLog(tag, "------------------------------------", release);
        for (StackTraceElement e : traces) {
            String info = e.toString();
            if (fullLog || (!StringUtils.isNullOrEmpty(keyword) && info.contains(keyword))) {
                printLog(tag, info, release);
            }
        }
        printLog(tag, "------------------------------------", release);
    }
    
    private static void printLog(String tag, String log, boolean release) {
        if (release) {
            info(tag, log);
        }
        else {
            debug(tag, log);
        }
    }

    public static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
    
    public static String threadStack() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            pw.println(e.toString());
        }
        return sw.toString();
    }
}
