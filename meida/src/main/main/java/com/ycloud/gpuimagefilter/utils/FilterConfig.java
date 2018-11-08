package com.ycloud.gpuimagefilter.utils;

import com.ycloud.utils.YYLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by Administrator on 2017/7/28.
 */

public class FilterConfig {
    public String m_MP4Name = new String("null");
    public List<BodiesDetectInfo> mBodiesDetectInfoList;
    public ArrayList<FilterInfo> mFilterInfos = new ArrayList<>(0);

    public void setMP4Name(String name) {
        m_MP4Name = name;
    }

    public void addFilterInfo(FilterInfo filterInfo) {
        mFilterInfos.add(filterInfo);
    }

    public void unmarshall(String json) {
        try {
            JSONObject jobj = new JSONObject(json);
            m_MP4Name = jobj.getString(FilterJSonKey.KEY_MP4_NAME);
            mFilterInfos = new ArrayList<>(0);
            JSONArray jArray = jobj.getJSONArray(FilterJSonKey.KEY_FILTER_SETTING);
            for (int i = 0; i < jArray.length(); i++) {
                JSONObject jFilterObj = jArray.getJSONObject(i);
                FilterInfo fltInfo = new FilterInfo();
                fltInfo.unmarshall(jFilterObj);
                mFilterInfos.add(fltInfo);
            }
        } catch (JSONException e) {
            YYLog.error(this, "[exception] FilterConfig.unmarshal: " + e.toString());
            e.printStackTrace();
            return;
        }
        //TODO. remove this debug.
        YYLog.info(this, "FilterConfig.unmarshall success: "+json);
    }

    public void unmarshall(String json, long startTime, long endTime) {
        try {
            JSONObject jobj = new JSONObject(json);
            m_MP4Name = jobj.getString(FilterJSonKey.KEY_MP4_NAME);
//            unmarshallBodiesDetectInfoList(jobj);
            mFilterInfos = new ArrayList<>(0);
            JSONArray jArray = jobj.getJSONArray(FilterJSonKey.KEY_FILTER_SETTING);
            for (int i = 0; i < jArray.length(); i++) {
                JSONObject jFilterObj = jArray.getJSONObject(i);
                FilterInfo fltInfo = new FilterInfo();
                fltInfo.unmarshall(jFilterObj, startTime, endTime);
                mFilterInfos.add(fltInfo);
            }
        } catch (JSONException e) {
            YYLog.error(this, "[exception] FilterConfig.unmarshal: " + e.toString());
            e.printStackTrace();
            return;
        }
        //TODO. remove this debug.
        YYLog.info(this, "FilterConfig.unmarshall success: "+json);
    }

    public String marshall() {
        JSONObject jobj = new JSONObject();
        try {
            jobj.put(FilterJSonKey.KEY_MP4_NAME, m_MP4Name == null ? "null" : m_MP4Name);
//            marshallBodiesDetectInfoList(jobj);

            JSONArray jArray = new JSONArray();

            ListIterator<FilterInfo> it = mFilterInfos.listIterator();
            while (it.hasNext()) {
                FilterInfo fltInfo = it.next();
                JSONObject jFltInfo = new JSONObject();
                fltInfo.marshall(jFltInfo);
                jArray.put(jFltInfo);
            }

            jobj.put(FilterJSonKey.KEY_FILTER_SETTING, jArray);
            return jobj.toString();
        } catch (JSONException e) {
            YYLog.error(this, "[exception] FilterConfig.marshall: " + e.toString());
            e.printStackTrace();
        }
        return null;
    }

    public void setBodiesDetectInfoList(List<BodiesDetectInfo> bodiesDetectInfoList) {
        mBodiesDetectInfoList = bodiesDetectInfoList;
    }


    public void marshallBodiesDetectInfoList(JSONObject jsonObject) {
        try {
            int bodiesDetectInfoListLen = mBodiesDetectInfoList.size();
            jsonObject.put("bodiesDetectInfoListLen", bodiesDetectInfoListLen);
            for (int i = 0; i < bodiesDetectInfoListLen; i++) {
                jsonObject.put("timeStamp" + i, mBodiesDetectInfoList.get(i).mTimeStamp);


                int bodyDetectInfoLen = mBodiesDetectInfoList.get(i).mBodyDetectInfoList.size();
                jsonObject.put("bodyDetectInfoLen" + i, bodyDetectInfoLen);

                for (int j = 0; j < bodyDetectInfoLen; j++) {
                    int bodyPointLen = mBodiesDetectInfoList.get(i).mBodyDetectInfoList.get(j).mBodyPointList.size();
                    jsonObject.put("bodyPointLen" + i + ":" + j, bodyDetectInfoLen);

                    for (int k = 0; k < bodyPointLen; k++) {
                        jsonObject.put("bodyPoint" + i + ":" + j + ":" + k, mBodiesDetectInfoList.get(i).mBodyDetectInfoList.get(j).mBodyPointList.get(k));
                    }


                    int bodyPointScoreLen = mBodiesDetectInfoList.get(i).mBodyDetectInfoList.get(j).mBodyPointsScoreList.size();
                    jsonObject.put("bodyPointScoreLen" + i + ":" + j, bodyPointScoreLen);
                    for (int k = 0; k < bodyPointScoreLen; k++) {
                        jsonObject.put("bodyPointScore" + i + ":" + j + ":" + k, mBodiesDetectInfoList.get(i).mBodyDetectInfoList.get(j).mBodyPointsScoreList.get(k));
                    }
                }
            }
        } catch (JSONException e) {
            YYLog.error(this, "[exception] PressedEffectFilterParameter.marshallExtraDataList: " + e.toString());
            e.printStackTrace();
        }
    }

    public void unmarshallBodiesDetectInfoList(JSONObject jsonObject) {
        mBodiesDetectInfoList.clear();
        try {
            int bodiesDetectInfoListLen = jsonObject.getInt("bodiesDetectInfoListLen");
            for (int i = 0; i < bodiesDetectInfoListLen; i++) {
                long timeStamp = jsonObject.getLong("timeStamp" + i);

                BodiesDetectInfo bodiesDetectInfo = new BodiesDetectInfo(timeStamp);

                int bodyDetectInfoLen = jsonObject.getInt("bodyDetectInfoLen" + i);
                for (int j = 0; j < bodyDetectInfoLen; j++) {
                    BodiesDetectInfo.BodyDetectInfo bodyDetectInfo = new BodiesDetectInfo.BodyDetectInfo();


                    int bodyPointLen = jsonObject.getInt("bodyPointLen" + i + ":" + j);


                    for (int k = 0; k < bodyPointLen; k++) {
                        bodyDetectInfo.mBodyPointList.add((float) jsonObject.getDouble("bodyPoint" + i + ":" + j + ":" + k));
                    }

                    int bodyPointScoreLen = jsonObject.getInt("bodyPointScoreLen" + i + ":" + j);
                    for (int k = 0; k < bodyPointScoreLen; k++) {
                        bodyDetectInfo.mBodyPointsScoreList.add((float) jsonObject.getDouble("bodyPointScore" + i + ":" + j + ":" + k));
                    }

                    bodiesDetectInfo.mBodyDetectInfoList.add(bodyDetectInfo);
                }
                mBodiesDetectInfoList.add(bodiesDetectInfo);
            }
        } catch (Exception e) {

        }
    }
}
