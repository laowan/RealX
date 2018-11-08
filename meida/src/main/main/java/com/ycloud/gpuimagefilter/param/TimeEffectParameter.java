package com.ycloud.gpuimagefilter.param;


/**
 * Created by Administrator on 2018/4/21.
 *
 */

import com.ycloud.utils.YYLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeEffectParameter {
    private static final String TAG = "TimeEffectParameter";
    private static TimeEffectParameter mInstance = null;
    private static final byte[] SYNC_FLAG = new byte[1];
    private static final String JSONKEY_TIMERANGE = "timeRange";
    private static final String JSONKEY_ITEMS = "items";
    private static final String JSONKEY_MULTIP = "multiplier";
    private float timeRange[] = new float[2];
    private Map<Integer, TimeEffectItems> items = new HashMap<>();
    private List<TimeEffectRange> mTimeEffectList = new ArrayList<>();
    private AtomicBoolean mConfigSuccess = new AtomicBoolean(false);

    public static TimeEffectParameter instance() {
        if (mInstance == null) {
            synchronized (SYNC_FLAG) {
                if (mInstance == null) {
                    mInstance = new TimeEffectParameter();
                }
            }
        }
        return mInstance;
    }

    private void parserJsonConfig(String json) {
        synchronized (this) {
            //json config为null的情况表示变速不根据配置文件，将业务层通过接口设置的变速参数应用到整段时间特效区间
            if (json == null) {
                mConfigSuccess.set(true);
                YYLog.info(TAG, "json is null, change playback speed without config");
                return;
            }

            mConfigSuccess.set(false);
            try {
                JSONObject obj = new JSONObject(json);
                String[] timeRangeStr = obj.getString(JSONKEY_TIMERANGE).split(",");
                if (timeRangeStr.length == 2) {
                    timeRange[0] = Integer.parseInt(timeRangeStr[0].trim());
                    timeRange[1] = Integer.parseInt(timeRangeStr[1].trim());
                } else {
                    YYLog.warn(TAG, "json config parameter error! ");
                    return;
                }

                if (items.size() != 0) {   // 清除之前的配置信息
                    items.clear();
                }

                JSONArray array = obj.getJSONArray(JSONKEY_ITEMS);
                for (int i = 0; i < array.length(); i++) {
                    TimeEffectItems item = new TimeEffectItems();
                    JSONObject tmpObj = array.getJSONObject(i);
                    String[] timeRange = tmpObj.getString(JSONKEY_TIMERANGE).split(",");
                    if (timeRange.length == 2) {
                        item.mTimeRangeLeft = Float.parseFloat(timeRange[0].trim());
                        item.mTimeRangeRight = Float.parseFloat(timeRange[1].trim());
                    } else {
                        YYLog.warn(TAG, "json config parameter error! ");
                        return;
                    }
                    item.mMultiplier = Float.parseFloat(tmpObj.getString(JSONKEY_MULTIP).trim());
                    items.put(i, item);
                }
                mConfigSuccess.set(true);
            } catch (JSONException e) {
                YYLog.info(TAG, "Exception: " + e.getMessage());
            } catch (Exception e) {
                YYLog.error(TAG, "Exception: " + e.getMessage());
            }
        }
    }

    public void setConfig(String jsonStr) {
        YYLog.info(TAG, "setConfig " + jsonStr);
        parserJsonConfig(jsonStr);
    }

    public void addTimeEffect(int segmentId, float startTime, float duration, float playbackSpeed) {
        if (!mConfigSuccess.get()) {
            YYLog.error(TAG, "Should set config before addTimeEffect !");
            return;
        }

        try {
            mTimeEffectList.add(new TimeEffectRange(segmentId, startTime, duration, playbackSpeed));
            YYLog.info(TAG, "addTimeEffect segId " + segmentId + " startTime " + startTime +
                                                                                " duration " + duration);
        } catch (Exception e) {
            YYLog.error(TAG, "Exception : " + e.getMessage());
        }
    }

    public void removeTimeEffect(int segmentId) {
        int size = mTimeEffectList.size();
        for (int i = 0; i < size; i++) {
            TimeEffectRange range = mTimeEffectList.get(i);
            if (range != null && range.mSegmentId == segmentId) {
                mTimeEffectList.remove(i);
                YYLog.info(TAG, "removeTimeEffect segId " + range.mSegmentId + " startTime " +
                                                    range.mStartTime + " duration " + range.mDuration);
                break;
            }
        }
    }


    public boolean IsExistTimeEffect() {
        return mConfigSuccess.get() && mTimeEffectList.size() > 0;
    }

    private TimeEffectRange getTimeEffectRange(long pts) {
        int count = mTimeEffectList.size();
        for (int i = 0; i < count; i++) {
            TimeEffectRange range = mTimeEffectList.get(i);
            if (pts > range.mStartTime && pts < range.mStartTime + range.mDuration) {
                return range;
            }
        }
        return null;
    }

    private float getSpeed(float pts, TimeEffectRange range) {
        int itemCount = items.size();
        for (int i = 0; i < itemCount; i++) {
            TimeEffectItems item = items.get(i);
            if (pts > range.mStartTime + item.mTimeRangeLeft * range.mDuration &&
                    pts < range.mStartTime + item.mTimeRangeRight * range.mDuration) {
                return 1 / item.mMultiplier;
            }
        }
        return range.mPlaybackSpeed;
    }

    /**
     * 将业务层应用of特效时候传过来的pts转换成实际的视频pts
     * audio pts:音频pts，回调给业务层的pts
     * video pts:应用快慢速后视频的实际pts
     * 只有当业务层传过来的pts（audio pts）位于某个timeRange内,视频pts才会出现与音频pts不一致的情况。当前pts之外的变速区间（无论之前还是之后）均不会影响当前pts
     * @param audioPts 业务层传入的pts，根据音频pts获取
     * @return
     */
    public float audioPtsToVideoPts(long audioPts) {
        float videoPts = audioPts;
        for (TimeEffectRange timeEffect : mTimeEffectList) {
            if (audioPts > timeEffect.mStartTime && audioPts < timeEffect.mStartTime + timeEffect.mDuration) {
                videoPts = timeEffect.mStartTime;
                int itemCount = items.size();
                float lastItemEnd = timeEffect.mStartTime;
                for (int i = 0; i < itemCount; i++) {
                    TimeEffectItems item = items.get(i);

                    float itemStart = lastItemEnd;
                    float itemEnd = lastItemEnd + (item.mTimeRangeRight - item.mTimeRangeLeft) * timeEffect.mDuration * item.mMultiplier;
                    lastItemEnd = itemEnd;

                    if (audioPts > itemStart && audioPts < itemEnd) {
                        videoPts += (audioPts - itemStart) / item.mMultiplier;
                        break;
                    } else if (audioPts > itemEnd) {
                        videoPts += (itemEnd - itemStart) / item.mMultiplier;
                    }
                }
            }
        }
        return videoPts;
    }

    // pts : video pts
    public float getCurrentSpeed(long pts) {
        TimeEffectRange range = getTimeEffectRange(pts);
        if (range != null) {
            return getSpeed(pts, range);
        }
        return 1.0f;
    }

    public void clear() {
        items.clear();
        mTimeEffectList.clear();
        timeRange[0] = 0;
        timeRange[1] = 0;
        mConfigSuccess.set(false);
		YYLog.info(TAG, "clear success. ");
    }


    private class TimeEffectItems {
        private float mTimeRangeLeft;
        private float mTimeRangeRight;
        private float mMultiplier;
    }

    private class TimeEffectRange {
        private TimeEffectRange(int segmentId, float start, float duration, float playbackSpeed) {
            mStartTime = start;
            mDuration = duration;
            mSegmentId = segmentId;
            mPlaybackSpeed = playbackSpeed;
        }
        private float mStartTime;
        private float mDuration;
        private int mSegmentId;
        private float mPlaybackSpeed;
    }
}
