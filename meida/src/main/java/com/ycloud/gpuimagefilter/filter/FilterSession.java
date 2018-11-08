package com.ycloud.gpuimagefilter.filter;

import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.gpuimagefilter.utils.FilterConfig;
import com.ycloud.gpuimagefilter.utils.FilterInfo;
import com.ycloud.gpuimagefilter.utils.IFilterInfoListener;
import com.ycloud.utils.YYLog;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Administrator on 2017/7/29.
 */

public class FilterSession {
    //not public.
    FilterSession(int sessionID) {
        mSessionID = sessionID;
    }

    public int mSessionID = -1;

    public int getSessionID() {
        return mSessionID;
    }

    public void setFilterInfoListener(IFilterInfoListener listener) {
        if (mSessionID != -1) {
            FilterCenter.getInstance().setFilterInfoListener(listener, mSessionID);
        }
    }

    public int addFilter(int type, String groupType, int zOrderID) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().addFilter(type, groupType, zOrderID, mSessionID);
        }
        return -1;
    }

    /**
     * 添加一个FilterType类型的filter, filter参数为parameter，返回filter实例对应的id号
     */
    public int addFilter(int type, String groupType, BaseFilterParameter parameter, int zOrderID) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().addFilter(type, groupType, parameter, zOrderID, mSessionID);
        }
        return -1;
    }

    public int addFilter(int type, String groupType) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().addFilter(type, groupType, mSessionID);
        }
        return -1;
    }

    /**
     * 添加一个FilterType类型的filter, filter参数为parameter，返回filter实例对应的id号
     */
    public int addFilter(int type, String groupType, BaseFilterParameter parameter) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().addFilter(type, groupType, parameter, mSessionID);
        }
        return -1;
    }

    /*加载一个json_cfg配置文件中的filter, 返回配置文件中对应的filter实例的ID列表*/
    public List<Integer> addFilter(String json_cfg, boolean isUpdateID) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().addFilter(json_cfg, mSessionID, isUpdateID);
        }
        return null;
    }

    /*加载一个json_cfg配置文件中的filter, 返回配置文件中对应的filter实例的ID列表，同时设置生效时长startTime～endTime*/
    public ArrayList<FilterInfo> addFilter(String json_cfg, long startTime, long endTime) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().addFilter(json_cfg, startTime, endTime, mSessionID);
        }
        return null;
    }

    /*修改filter的z-order，调整其在filter list中的显示顺序*/
    public void modifyFilterZOrder(int filterID, int zOrder) {
        if (mSessionID != -1) {
            FilterCenter.getInstance().modifyFilterZOrder(filterID, zOrder, mSessionID);
        }
    }

    /*删除对应filterID的filter， 成功返回true， 否则返回false*/
    public boolean removeFilterByFilterID(int filterID) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().removeFilterByFilterID(filterID, mSessionID);
        }
        return false;
    }

    /*删除FilterType为type的所有filter实例， 成功返回true， 否则返回false*/
    public boolean removeFilterByFilterType(int type) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().removeFilterByFilterType(type, mSessionID);
        }
        return false;
    }

    /*删除所有filter实例， 成功返回true， 否则返回false*/
    public void removeAllFilter() {
        if (mSessionID != -1) {
            FilterCenter.getInstance().removeAllFilter(mSessionID);
        }
    }

    /*增加一个filter对象的参数, 成功返回这个parameter的id号，失败返回-1*/
    public int addFilterParameter(int filterID, BaseFilterParameter parameter) {
        if (mSessionID != -1) {
            parameter.mParameterID = FilterCenter.getInstance().addFilterParameter(filterID, parameter, mSessionID);
            return parameter.mParameterID;
        }
        return -1;
    }

    public int resetFilterParameter(int filterID, BaseFilterParameter parameter) {
        if (mSessionID != -1) {
            parameter.mParameterID = FilterCenter.getInstance().resetFilterParameter(filterID, parameter, mSessionID);
            return parameter.mParameterID;
        }
        return -1;
    }

    public boolean modifyFilterParameter(int filterID, int paramID, BaseFilterParameter parameter) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().modifyFilterParameter(filterID, paramID, parameter, mSessionID, true);
        }
        return false;
    }

    public boolean modifyFilterParameterWithoutCopy(int filterID, int paramID, BaseFilterParameter parameter) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().modifyFilterParameter(filterID, paramID, parameter, mSessionID, false);
        }
        return false;
    }

    public BaseFilterParameter getFilterParameter(int filterID, int paramID) {
        if (mSessionID != -1) {
            FilterInfo filterInfo =  getFilterInfo(filterID);
            if(filterInfo!=null){
                return  filterInfo.getFilterParameter(paramID);
            }
        }
        return null;
    }

    public List<BaseFilterParameter> getFilterParameters(int filterID) {
        if(mSessionID != -1) {
            FilterInfo filterInfo =  getFilterInfo(filterID);
            if(filterInfo!=null){
                return  filterInfo.getFilterParameters();
            }
        }
        return null;
    }

    public void removeFilterParameter(int filterID, int paramID) {
        if (mSessionID != -1) {
            FilterCenter.getInstance().removeFilterParameter(filterID, paramID, mSessionID);
        }
    }

    public void removeFilterParameter(int filterID) {
        if (mSessionID != -1) {
            FilterCenter.getInstance().removeFilterParameter(filterID, mSessionID);
        }
    }

    /*获取对应filterID的filter实例的信息，成功返回对应的配置信息(deep-copy), 否则返回null*/
    public FilterInfo getFilterInfo(Integer filterID) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().getFilterInfo(filterID, mSessionID);
        }
        return null;
    }

    /*获取对应filterType为type的所有filter实例的信息列表，成功返回对应的配置信息列表(deep-copy), 否则返回null*/
    public CopyOnWriteArrayList<FilterInfo> getFilterInfoByType(int type) {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().getFilterInfoByType(type, mSessionID);
        }
        return null;
    }

    public ArrayList<FilterInfo> getAllFilterInfo() {
        if (mSessionID != -1) {
            return FilterCenter.getInstance().getFilerInfoBySessionID(mSessionID);
        }
        return null;
    }

    public String getFilterConfig() {
        if(mSessionID != -1) {
            //编译各个filter, 得到对应的json信息.
            FilterConfig config = new FilterConfig();
//            config.setBodiesDetectInfoList(FilterCenter.getInstance().getBodyDetectInfoBySessionID(mSessionID));
            ArrayList<FilterInfo> filterInfoList =  FilterCenter.getInstance().getFilerInfoBySessionID(mSessionID);
            if(filterInfoList != null) {
                ListIterator<FilterInfo> it = filterInfoList.listIterator();
                while (it.hasNext()) {
                    FilterInfo filterInfo = it.next();
                    if (filterInfo != null) {
                        try {
                            BaseFilterParameter param = getFilterParameters(filterInfo.mFilterID).get(0);
                            if (param != null && param.mVisible) {
                                config.addFilterInfo(filterInfo);
                            }
                        } catch (Exception e) {
                            YYLog.error(this, "getFilterConfig exception:" + e.getMessage());
                        }
                    }
                }
            }
            String ret = config.marshall();
//            YYLog.info(this, "FilterSession.getFilterConfig: " + (ret == null ? "null" : ret));
            YYLog.info(this, "getFilterConfig: filter num of this session=" + config.mFilterInfos.size());
            return ret;
        }
        return null;
    }

    public String getFilterConfigByFilterType(ArrayList<Integer> filterTypes) {
        if(mSessionID != -1) {
            //编译各个filter, 得到对应的json信息.
            FilterConfig config = new FilterConfig();
            ArrayList<FilterInfo> filterInfoList =  FilterCenter.getInstance().getFilerInfoBySessionID(mSessionID);
            if(filterInfoList != null) {
                ListIterator<FilterInfo> it = filterInfoList.listIterator();
                while (it.hasNext()) {
                    FilterInfo filterInfo = it.next();
                    if(filterInfo != null) {
                        boolean isAdd = false;
                        if (filterTypes != null && filterTypes.size() != 0) {
                            for (int i = 0; i < filterTypes.size(); i++) {
                                if (filterInfo.mFilterType == filterTypes.get(i)) {
                                    isAdd = true;
                                    break;
                                }
                            }
                        }

                        if (isAdd) {
                            config.addFilterInfo(filterInfo);
                        }
                    }
                }
            }
            String ret = config.marshall();
            YYLog.info(this, "getFilterConfigByFilterType: " + (ret == null ? "null" : ret));
            return ret;
        }
        return null;
    }

    /*根据filterId list，生成对应的配置文件*/
    public String getFilterConfigByFilterId(List<Integer> filterIds) {
        if (mSessionID != -1) {
            FilterConfig config = new FilterConfig();
            for (int filterId : filterIds) {
                FilterInfo filterInfo = FilterCenter.getInstance().getFilterInfo(filterId, mSessionID);
                if (filterInfo != null) {
                    //only marshall filter config when its visible attribute is true
                    try {
                        BaseFilterParameter param = getFilterParameters(filterInfo.mFilterID).get(0);
                        if (param != null && param.mVisible) {
                            config.addFilterInfo(filterInfo);
                        }
                    } catch (Exception e) {
                        YYLog.error(this, "getFilterConfigByFilterId exception:" + e.getMessage());
                    }
                }
            }
            String ret = config.marshall();
            YYLog.info(this, "getFilterConfigByFilterId: " + (ret == null ? "null" : ret));
            return ret;
        }
        return null;
    }

    /*清空sessionID对应的filterGroup下的of context缓存，以及时释放gpu memory*/
    public void clearCachedResource() {
        if (mSessionID != -1) {
            FilterCenter.getInstance().clearCachedResource(mSessionID);
        }
    }
}
