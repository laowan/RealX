package com.ycloud.gpuimagefilter.utils;

import com.ycloud.utils.YYLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * Created by jinyongqing on 2018/1/31.
 */

public class FilterConfigParse {
    private static String TAG = FilterConfigParse.class.getSimpleName();
    public static final String CONF_PATH = "/effectconfig.conf";

    private static final String KEY_VOICE_MODE = "voiceChangeMode";
    private static final String KEY_SUPPORT_SEEKING = "supportSeeking";
    private static final String KEY_EFFECT_LIST = "effectList";
    private static final String KEY_EFFECT_TYPE_LIST = "effectTypeList";

    private static final int TYPE_COLOR_TABLE = 0;
    private static final int TYPE_EMOJI = 1;

    public static FilterConfigInfo parseFilterConf(String dir) {
        FilterConfigInfo filterConfigInfo = new FilterConfigInfo();
        try {
            FileInputStream inputStream = new FileInputStream(dir + CONF_PATH);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String json;
            while ((json = bufferedReader.readLine()) != null) {
                try {
                    JSONObject jsonObj = new JSONObject(json);

                    filterConfigInfo.mVoiceChangeMode = jsonObj.getInt(KEY_VOICE_MODE);
                    YYLog.info(TAG, "set voice change mode:" + filterConfigInfo.mVoiceChangeMode);

                    filterConfigInfo.mSupportSeeking = jsonObj.getInt(KEY_SUPPORT_SEEKING);
                    YYLog.info(TAG, "set support seeking:" + filterConfigInfo.mSupportSeeking);

                    JSONArray jsonEffectList = jsonObj.getJSONArray(KEY_EFFECT_LIST);
                    JSONArray jsonEffectTypeList = jsonObj.getJSONArray(KEY_EFFECT_TYPE_LIST);

                    for (int i = 0; i < jsonEffectTypeList.length(); i++) {
                        switch (Integer.valueOf(jsonEffectTypeList.getString(i))) {
                            case TYPE_COLOR_TABLE:
                                filterConfigInfo.mColorTableEffectPath = dir + "/" + jsonEffectList.getString(i);
                                YYLog.info(TAG, "set color table path:" + filterConfigInfo.mColorTableEffectPath);
                                break;
                            case TYPE_EMOJI:
                                filterConfigInfo.mEmojiEffectPath = dir + "/" + jsonEffectList.getString(i);
                                YYLog.info(TAG, "set emoji path:" + filterConfigInfo.mEmojiEffectPath);
                                break;
                        }
                    }
                } catch (Exception e) {
                    YYLog.error(TAG, "parse filter config exception:" + e.getMessage());
                }
            }

            inputStream.close();
            inputStreamReader.close();
            bufferedReader.close();

        } catch (Exception e) {
            YYLog.error(TAG, "parse filter config exception:" + e.getMessage());
        }
        return filterConfigInfo;
    }

    public static RhythmInfo parseRhythmConf(String confPath) {
        //节奏信息由业务层传入，sdk不再解析节奏信息
        /*RhythmInfo rhythmInfo = new RhythmInfo();
        Gson gson = new GsonBuilder().create();
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(confPath), "UTF-8"));
            reader.beginObject();
            String jsonTag;
            while (reader.hasNext()) {
                jsonTag = reader.nextName();
                if ("beatList".equals(jsonTag)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        RhythmInfo.RhythmInfoBeat rhythmInfoBeat = gson.fromJson(reader, RhythmInfo.RhythmInfoBeat.class);
                        rhythmInfo.rhythmInfoBeatList.add(rhythmInfoBeat);
                    }
                    reader.endArray();
                } else if ("pcmList".equals(jsonTag)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        RhythmInfo.RhythmInfoPcm rhythmInfoPcm = gson.fromJson(reader, RhythmInfo.RhythmInfoPcm.class);
                        rhythmInfo.rhythmInfoPcmList.add(rhythmInfoPcm);
                    }
                    reader.endArray();
                }
            }
            reader.endObject();
            return rhythmInfo;
        } catch (Exception e) {
            YYLog.error(TAG, "parse rhythm conf exception:" + e.getMessage());
        }*/
        return null;
    }


    public static class FilterConfigInfo {
        public int mSupportSeeking;
        public int mVoiceChangeMode;
        public String mColorTableEffectPath;
        public String mEmojiEffectPath;
        public String mGameEffectPath;
    }

}


