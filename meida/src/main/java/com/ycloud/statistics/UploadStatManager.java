package com.ycloud.statistics;

/**
 * Created by DZHJ on 2017/4/11.
 */

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.ycloud.utils.YYLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 20s 统计一次并上报海度
 * 1、采集fps
 * 2、前处理时间 平均值、最大值
 * 3、编码fps
 * 4、编码时间 平均值、最大值
 * 5、编码器返回错误/异常 次数
 * TODO:加入解码器解码时间，平均值，最大值
 */

public class UploadStatManager {

    public static final int CAP_FPS = 0;
    public static final int ENC_FPS = 1;


    public final static String CONTENT_ACT_MOBILE_LIVE_UPLINK_STAT = "mobileliveuplinkstat"; //上报 手机开播数据周期性统计

    private static final int MSG_START_STATS = 1;
    private static final int MSG_STOP_STATS = 2;
    private static final int MSG_FPS = 3;
    private static final int MSG_BEGIN_PREPROCESS = 4;
    private static final int MSG_END_PREPROCESS = 5;
    private static final int MSG_BEGIN_ENCODE = 6;
    private static final int MSG_END_ENCODE = 7;
    private static final int MSG_REPORT_ENCERROR = 8;
    private static final int MSG_REPORT_ENCEXCEPTION = 9;

    private static final int STAT_INTERVAL = 20; //20s
    public static final String TAG = "UploadStatManager ";

    private static volatile UploadStatManager mInstance;

//    private volatile StatHandler mWorkerHandler;
//    private Timer mTimer;

    private HiidoStatsContent liveStatContent = null;
    //fps
    private SparseArray<Long> counts = new SparseArray<>();

    //preprocess
    private long preprocessBeginTs = 0;
    private ArrayList<Long> preprocessTimeArr = new ArrayList<>();
    private long preprocessTimeAvg = 0;
    private long preprocessTimeMax = 0;

    //encode
    private ArrayList<Long> encodeTimeArr = new ArrayList<>();
    private SparseArray<Long> encodePts2Begin = new SparseArray<>();
    private long encodeTimeAvg = 0;
    private long encodeTimeMax = 0;

    //encode error
    private SparseIntArray encErrArr = new SparseIntArray();
    private HashMap<String, Integer> encExcArr = new HashMap<>();

    //common
    private int encType;    //VideoEncoderType

    private final static byte[] SYNC_FLAG = new byte[1];

    public static synchronized UploadStatManager getInstance() {
        if (mInstance == null) {
            synchronized (SYNC_FLAG) {
                if (mInstance == null) {
                    mInstance = new UploadStatManager();
                }
            }
        }
        return mInstance;
    }

    public UploadStatManager() {
//        if (mWorkerHandler == null) {
//            HandlerThread handlerThread = new HandlerThread(TAG);
//            handlerThread.start();
//            Looper looper = handlerThread.getLooper();
//            if (looper != null) {
//                mWorkerHandler = new StatHandler(looper, this);
//            }
//        }
    }

    public void startStat(final int type) {
//        if (mWorkerHandler == null) {
//            YYLog.e(TAG, "WorkerHandler not init yet.");
//            return;
//        }
//        YYLog.i(TAG, "startStat type:" + type);
//        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(MSG_START_STATS, type, 0));
//        mTimer = new Timer();
//        mTimer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                mWorkerHandler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        //TODO. 不使用20秒上报哦一次数据
////                        buildStat();
//                    }
//                });
//            }
//        }, STAT_INTERVAL * 1000, STAT_INTERVAL * 1000);
    }

    private void doStartStat(int type) {
        clear();
        encType = type;
    }

    public void stopStat() {
        YYLog.i(TAG,"stopStat");
//        if (mTimer != null) {
//            mTimer.cancel();
//            mTimer = null;
//        }
//        if (mWorkerHandler == null) {
//            YYLog.e(TAG, "WorkerHandler not init yet.");
//            return;
//        }
//        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(MSG_STOP_STATS));
    }

    private void doStopStat() {
        clear();
    }

/*    *
     * 按不同类型计算fps
     *
     * @param type 不同的统计点,使用不同的type*/

    public void fps(final int type) {
//        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(MSG_FPS, type, 0));
    }

    private void doFps(final int type) {
        long frameCount = counts.get(type, 0L);
        counts.put(type, ++frameCount);
    }

    public void beginPreprocess() {
//        if (mWorkerHandler == null) {
//            YYLog.e(TAG, "WorkerHandler not init yet.");
//            return;
//        }
//        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(MSG_BEGIN_PREPROCESS));
    }

    private void doBeginPreprocess() {
        preprocessBeginTs = System.currentTimeMillis();
    }

    public void endPreprocess() {
//        if (mWorkerHandler == null) {
//            YYLog.e(TAG, "WorkerHandler not init yet.");
//            return;
//        }
//        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(MSG_END_PREPROCESS));
    }

    private void doEndPreprocess() {
        if (preprocessBeginTs == 0)
            return;
        long preprocessTimeTs = System.currentTimeMillis() - preprocessBeginTs;

        if (preprocessTimeTs > preprocessTimeMax) {
            preprocessTimeMax = preprocessTimeTs;
        }

        preprocessTimeArr.add(preprocessTimeTs);
    }

    public void beginEncode(final int pts) {
//        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(MSG_BEGIN_ENCODE, pts, 0));
    }

    private void doBeginEncode(final int pts) {
        long beginTs = System.currentTimeMillis();
        encodePts2Begin.put(pts, beginTs);
    }

    public void endEncode(final int pts) {
//        if (mWorkerHandler == null) {
//            YYLog.e(TAG, "WorkerHandler not init yet.");
//            return;
//        }
//        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(MSG_END_ENCODE, pts, 0));
    }

    private void doEndEncode(final int pts) {
        if (pts <= 0)
            return;
        Long encodeBeginTs = encodePts2Begin.get(pts);

        if (encodeBeginTs == null)
            return;

        long encodeTimeTs = System.currentTimeMillis() - encodeBeginTs;
        if (encodeTimeTs > encodeTimeMax) {
            encodeTimeMax = encodeTimeTs;
        }

        encodeTimeArr.add(encodeTimeTs);
    }

    public void reportEncError(final int err) {
//        if (mWorkerHandler == null) {
//            YYLog.e(TAG, "WorkerHandler not init yet.");
//            return;
//        }
//        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(MSG_REPORT_ENCERROR, err, 0));
    }

    private void doReportEncError(final int err) {
        int errCnt = encErrArr.get(err, 0);
        encErrArr.put(err, ++errCnt);
    }

    public void reportEncException(final String exception) {
//        if (mWorkerHandler == null) {
//            YYLog.e(TAG, "WorkerHandler not init yet.");
//            return;
//        }
//        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(MSG_REPORT_ENCEXCEPTION, exception));
    }

    private void doReportEncException(final String exception) {
        int excCnt = 0;
        if (encExcArr.containsKey(exception)) {
            excCnt = encExcArr.get(exception);
        }
        encExcArr.put(exception, ++excCnt);
    }

    private void buildStat() {
        long capFrameCount = 0;
        long encFrameCount = 0;

        if (counts.size() != 0) {
            capFrameCount = counts.get(CAP_FPS, 0L);
            encFrameCount = counts.get(ENC_FPS, 0L);
        }

        if (preprocessTimeArr.size() != 0) {
            long preprocessTimeSum = 0;
            for (Long preprocessTime : preprocessTimeArr) {
                preprocessTimeSum += preprocessTime;
            }
            preprocessTimeAvg = preprocessTimeSum / preprocessTimeArr.size();
        }

        if (encodeTimeArr.size() != 0) {
            long encodeTimeSum = 0;
            for (Long encodeTime : encodeTimeArr) {
                encodeTimeSum += encodeTime;
            }
            encodeTimeAvg = encodeTimeSum / encodeTimeArr.size();
        }

        //TODO.
//        if (encType != 0){//not soft encoder
//            String hwEncName = VideoEncoderCore.getEncoderName();
//            liveStatContent.put("encname", hwEncName);
//        }

        liveStatContent.put("capfps", capFrameCount / STAT_INTERVAL);
        liveStatContent.put("encfps", encFrameCount / STAT_INTERVAL);
        liveStatContent.put("preprocesstimeavg", preprocessTimeAvg);
        liveStatContent.put("preprocesstimemax", preprocessTimeMax);
        liveStatContent.put("enctimeavg", encodeTimeAvg);
        liveStatContent.put("enctimemax", encodeTimeMax);

        StringBuilder errString = new StringBuilder();
        for (int i = 0; i < encErrArr.size(); ++i) {
            errString.append(encErrArr.keyAt(i)).append("-").append(encErrArr.valueAt(i)).append("xx");
        }
        if (encErrArr.size() > 0) {
            liveStatContent.put("encerr", errString.toString());
        }


        StringBuilder excString = new StringBuilder();
        Iterator it = encExcArr.entrySet().iterator();
        while (it.hasNext()) {
            HashMap.Entry pair = (HashMap.Entry) it.next();
            it.remove();
            excString.append(pair.getKey()).append("-").append(pair.getValue()).append("xx");
        }

        if (encExcArr.size() > 0) {
            liveStatContent.put("encexc", excString.toString());
        }

        clear();

        YYLog.i(TAG,CONTENT_ACT_MOBILE_LIVE_UPLINK_STAT + " " + liveStatContent.getTreeMapContent());
    }

    private void clear() {
        counts.clear();
        preprocessTimeAvg = 0;
        preprocessTimeMax = 0;
        encodeTimeMax = 0;
        encodeTimeAvg = 0;
        preprocessTimeArr.clear();
        encodeTimeArr.clear();
        encodePts2Begin.clear();

        encErrArr.clear();
        encExcArr.clear();
    }

    public static class StatHandler extends Handler {
        private WeakReference<UploadStatManager> mWeakManager;

        public StatHandler(Looper looper, UploadStatManager manager) {
            super(looper);
            mWeakManager = new WeakReference<UploadStatManager>(manager);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;

            UploadStatManager statMgr = mWeakManager.get();
            if (statMgr == null) {
                YYLog.w(TAG, "StatHandler.handleMessage: statMgr is null");
                return;
            }

            switch (what) {
                case MSG_START_STATS:
                    statMgr.doStartStat(inputMessage.arg1);
                    break;
                case MSG_STOP_STATS:
                    statMgr.doStopStat();
                    break;
                case MSG_FPS:
                    statMgr.doFps(inputMessage.arg1);
                    break;
                case MSG_BEGIN_PREPROCESS:
                    statMgr.doBeginPreprocess();
                    break;
                case MSG_END_PREPROCESS:
                    statMgr.doEndPreprocess();
                    break;
                case MSG_BEGIN_ENCODE:
                    statMgr.doBeginEncode(inputMessage.arg1);
                    break;
                case MSG_END_ENCODE:
                    statMgr.doEndEncode(inputMessage.arg1);
                    break;
                case MSG_REPORT_ENCERROR:
                    statMgr.doReportEncError(inputMessage.arg1);
                    break;
                case MSG_REPORT_ENCEXCEPTION:
                    statMgr.doReportEncException((String) inputMessage.obj);
                    break;
                default:
                    YYLog.e(TAG, "not support message: " + what);
                    break;
            }
        }
    }

}
