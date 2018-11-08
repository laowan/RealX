package com.ycloud.statistics;

import com.ycloud.utils.YYLog;
import com.yy.hiidostatis.api.StatisContent;

import java.util.TreeMap;

/**
 * Created by yuanxiaoming on 17/2/14.
 */
public class HiidoStatsContent extends StatisContent {
    private static final String  TAG="HiidoStatsContent";
    // Use have to avoid repeated pair added.
    protected TreeMap<String, String> raw = new TreeMap<String, String>();

    /**
     * Put key value pair. Same effect as {@link #put(String, String)} with
     * arguments : (key, String.valueOf(value)).
     *
     * @param key
     *            Cannot be null.
     * @param value
     *            An integer. Will be converted as String :
     *            String.valueOf(value).
     * @return Old value if there is for the key, null otherwise.
     */
    public String put(String key, int value) {
        return put(key, String.valueOf(value));
    }

    /**
     * Put key value pair. Same effect as {@link #put(String, String)} with
     * arguments : (key, String.valueOf(value)).
     *
     * @param key
     *            Cannot be null.
     * @param value
     *            An integer. Will be converted as String :
     *            String.valueOf(value).
     * @return Old value if there is for the key, null otherwise.
     */
    public String put(String key, long value) {
        return put(key, String.valueOf(value));
    }

    public String put(String key, double value) {
        return put(key, String.valueOf(value));
    }

    public String get(String key) {
        return raw.get(key);
    }

    public boolean containsKey(String key) {
        return raw.containsKey(key);
    }

    /**
     *
     * @param key
     *            Cannot be null.
     * @param value
     *            null-OK, null will be converted to empty automatically.
     * @return Old value.
     */
    public String put(String key, String value) {
        if (empty(key)) {
            YYLog.e(TAG, "key is invalid for value "+value);
            return null;
        }

        value = asEmptyOnNull(value);

        return raw.put(key, value);

    }

    public String put(String key, String value,boolean isCover) {
        if (empty(key)) {
            YYLog.e(TAG, "key is invalid for value "+value);
            return null;
        }

        value = asEmptyOnNull(value);
        if(isCover){
            return raw.put(key, value);
        }else{
            if(raw.containsKey(key)){
                return raw.get(key);
            }else{
                return raw.put(key, value);
            }
        }

    }

    public boolean isEmpty(){
        return raw.isEmpty();
    }

    private   boolean empty(String s) {
        return s == null || s.length() == 0;
    }

    private String asEmptyOnNull(String s) {
        return s == null ? "" : s;
    }

    public TreeMap<String, String> getTreeMapContent(){
        return raw;
    }

}
