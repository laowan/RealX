package com.ycloud.gpuimagefilter.utils;

import com.ycloud.gpuimagefilter.filter.BaseFilter;
import com.ycloud.mediafilters.AbstractYYMediaFilter;
import com.ycloud.utils.YYLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Administrator on 2017/7/27.
 */
public class FilterLayout {
    //每个filterGroup最多支持多少个不同流流出.
    public final static int kMaxPath = 4;
    public final static int kPreviewPathFlag = (1 << 30);
    public final static int kEncodePathFlag = (1 << 29);
    public final static int kAllPathFlag = (kEncodePathFlag | kPreviewPathFlag);

    public static AtomicInteger mZOrderID = new AtomicInteger(1);

    public static boolean hasPathFlag(int zorder, int kPathFlag) {
        return (zorder & kPathFlag) != 0;
    }

    public static int resetPathFlag(int zOrder, int kPathFlag) {
        zOrder |= kPathFlag;
        return zOrder;
    }

    public static int generateZOrderID() {
        return generateZOrderID(kAllPathFlag);
    }

    public static void updateZOrderID(int zOrderID) {
        mZOrderID.set(zOrderID);
    }

    public static int generateZOrderID(int pathFlag) {
        int segment = kAllPathFlag;
        if ((pathFlag & kAllPathFlag) != 0) {
            segment &= ~kAllPathFlag;
        } else {
            if ((pathFlag & kEncodePathFlag) != 0) {
                segment &= ~kEncodePathFlag;
            }
            if ((pathFlag & kPreviewPathFlag) != 0) {
                segment &= ~kPreviewPathFlag;
            }
        }

        int id = mZOrderID.getAndIncrement();
        if(id < 0) {
            synchronized (FilterLayout.class) {
                id = mZOrderID.getAndIncrement();
                if(id < 0) {
                    mZOrderID.set(1);
                    id = mZOrderID.getAndIncrement();
                }
            }
        }

        id %= kAllPathFlag;
        return (segment + id);
    }

    //不考虑分流的情况，只考虑filter适用于所有path, 按照z-order从小到大排列.

    //暂时只是考虑每个路径，只有一个输入源，一个输出源.

    private TreeMap<Integer, AbstractYYMediaFilter> m_pathOutFilter = new TreeMap<>();
    private TreeMap<Integer, AbstractYYMediaFilter> m_pathInFilter = new TreeMap<>();

    public void addPathInFilter(int pathFlag, AbstractYYMediaFilter filter) {
        m_pathInFilter.put(pathFlag, filter);
    }

    public void addPathOutFilter(int pathFlag, AbstractYYMediaFilter filter) {
        m_pathOutFilter.put(pathFlag, filter);
    }

    public class Dumper {
        private AbstractYYMediaFilter mLastDowFitler = null;
        private String mDumpLog = "layout dump:";

        public void push(AbstractYYMediaFilter fitler, AbstractYYMediaFilter downFilter) {
            if (fitler == null || downFilter == null)
                return;

            if (fitler == mLastDowFitler) {
                mDumpLog += "->" + downFilter.getClass().getSimpleName();
            } else {
                mDumpLog += ";" + fitler.getClass().getCanonicalName() + "->" + downFilter.getClass().getSimpleName();
            }

            mLastDowFitler = downFilter;
        }

        public String getDumpLog() {
            return mDumpLog;
        }
    }

    public void defaultLayout() {
        Dumper dumper = new Dumper();
        Iterator<Map.Entry<Integer, AbstractYYMediaFilter>> inIt = m_pathInFilter.entrySet().iterator();
        while (inIt.hasNext()) {
            Map.Entry<Integer, AbstractYYMediaFilter> inEntry = inIt.next();
            inEntry.getValue().removeAllDownStream();

            Iterator<Map.Entry<Integer, AbstractYYMediaFilter>> outIt = m_pathOutFilter.entrySet().iterator();
            while (outIt.hasNext()) {
                Map.Entry<Integer, AbstractYYMediaFilter> outEntry = outIt.next();
                if ((outEntry.getKey() & inEntry.getKey()) != 0) {
                    inEntry.getValue().addDownStream(outEntry.getValue());

                    dumper.push(inEntry.getValue(), outEntry.getValue());
                }
            }
        }

        YYLog.info(this, dumper.getDumpLog());
    }

    public int performLayout(ArrayList<BaseFilter> baseFilters) {
        if (baseFilters == null || baseFilters.isEmpty()) {
            //input connect with out filter
            defaultLayout();
            return 0;
        }

        Dumper dumper = new Dumper();
        //ArrayList排序.
        Comparator<BaseFilter> comparator = new Comparator<BaseFilter>() {
            public int compare(BaseFilter f1, BaseFilter f2) {
                if (f1.getFilterInfo() != null && f2.getFilterInfo() != null) {
                    return -(f2.getFilterInfo().mZOrder - f1.getFilterInfo().mZOrder);
                } else if (f1.getFilterInfo() != null) {
                    return 1;
                } else {
                    return -1;
                }
            }
        };
        Collections.sort(baseFilters, comparator);

        Iterator<Map.Entry<Integer, AbstractYYMediaFilter>> inIt = m_pathInFilter.entrySet().iterator();
        while (inIt.hasNext()) {
            Map.Entry<Integer, AbstractYYMediaFilter> inEntry = inIt.next();
            inEntry.getValue().removeAllDownStream();
            inEntry.getValue().addDownStream(baseFilters.get(0));
            dumper.push(inEntry.getValue(), baseFilters.get(0));
        }

        for (int i = 0, j = 1; i < baseFilters.size() && j < baseFilters.size(); i++, j++) {
            baseFilters.get(i).removeAllDownStream();
            baseFilters.get(i).addDownStream(baseFilters.get(j));

            dumper.push(baseFilters.get(i), baseFilters.get(j));
        }

        baseFilters.get(baseFilters.size() - 1).removeAllDownStream();

        Iterator<Map.Entry<Integer, AbstractYYMediaFilter>> it = m_pathOutFilter.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, AbstractYYMediaFilter> entry = it.next();
            baseFilters.get(baseFilters.size() - 1).addDownStream(entry.getValue());
            dumper.push(baseFilters.get(baseFilters.size() - 1), entry.getValue());
        }

        YYLog.info(this, "performLayout: " + dumper.getDumpLog());
        return 1;
        //just add
    }

    //TODO. 时间有限， 先简单的做一个2路的layout, 且无环路.
    public int performSimpleTwoGraphLayout(ArrayList<BaseFilter> baseFilters) {
        if (baseFilters == null || baseFilters.isEmpty()) {
            //input connect with out filter
            defaultLayout();
            return 0;
        }

        if (m_pathOutFilter.size() > 2 && m_pathInFilter.size() != 1) {
            YYLog.error(this, "performLayout_SimpleTwoGraph, more than 2 path found");
            return -1;
        }

        Dumper dumper = new Dumper();

        //ArrayList排序.
        Comparator<BaseFilter> comparator = new Comparator<BaseFilter>() {
            public int compare(BaseFilter f1, BaseFilter f2) {
                if (f1.getFilterInfo() != null && f2.getFilterInfo() != null) {
                    return f1.getFilterInfo().mZOrder - f2.getFilterInfo().mZOrder;
                } else if (f1.getFilterInfo() != null) {
                    return 1;
                } else {
                    return -1;
                }
            }
        };
        Collections.sort(baseFilters, comparator);

        AbstractYYMediaFilter preFilter = null;
        Iterator<Map.Entry<Integer, AbstractYYMediaFilter>> inIt = m_pathInFilter.entrySet().iterator();
        while (inIt.hasNext()) {
            Map.Entry<Integer, AbstractYYMediaFilter> inEntry = inIt.next();
            if (inEntry != null) {
                preFilter = inEntry.getValue();
                break;
            }
        }

        if(preFilter == null) {
            return -1;
        }

        preFilter.removeAllDownStream();

        BaseFilter curFilter;
        boolean isAddEncode = false;
        for (int i = 0; i < baseFilters.size(); i++) {
            curFilter = baseFilters.get(i);
            curFilter.removeAllDownStream();

            if (false == isAddEncode) {
                // 找出最后一个非可编辑节点
                if (curFilter.getFilterInfo() != null && hasPathFlag(curFilter.getFilterInfo().mZOrder, FilterLayout.kEncodePathFlag)) {
                    preFilter.addDownStream(m_pathOutFilter.get(FilterLayout.kEncodePathFlag));
                    dumper.push(preFilter, m_pathOutFilter.get(FilterLayout.kEncodePathFlag));
                    isAddEncode = true;
                } else {
                    if (i == (baseFilters.size() - 1)) {
                        curFilter.addDownStream(m_pathOutFilter.get(FilterLayout.kEncodePathFlag));
                        dumper.push(curFilter, m_pathOutFilter.get(FilterLayout.kEncodePathFlag));
                        isAddEncode = true;
                    }
                }
            }

            // 最后节点
            if (i == (baseFilters.size() - 1)) {
                curFilter.addDownStream(m_pathOutFilter.get(FilterLayout.kPreviewPathFlag));
                dumper.push(curFilter, m_pathOutFilter.get(FilterLayout.kPreviewPathFlag));
            }

            preFilter.addDownStream(curFilter);
            dumper.push(preFilter, curFilter);
            preFilter = curFilter;
        }

        YYLog.info(this, "performSimpleTwoGraphLayout: " + dumper.getDumpLog());
/*
        int i = 0;
        AbstractYYMediaFilter lastAllPathNode = m_pathInFilter.entrySet().iterator().next().getValue();
        for(i = 0; i < baseFilters.size(); i++)
        {
            if(hasPathFlag(baseFilters.get(i).getFilterInfo().mZOrder, FilterLayout.kAllPathFlag))
            {
                lastAllPathNode.removeAllDownStream();
                lastAllPathNode.addDownStream(baseFilters.get(i));
                dumper.push(lastAllPathNode, baseFilters.get(i));
                lastAllPathNode = baseFilters.get(i);
            } else {
                break;
            }
        }

        Iterator<Map.Entry<Integer, AbstractYYMediaFilter>> it = m_pathOutFilter.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<Integer, AbstractYYMediaFilter> entry = it.next();
            AbstractYYMediaFilter lastFlagNode = lastAllPathNode;
           *//* if(i >= baseFilters.size()) {
                lastAllPathNode.addDownStream(entry.getValue());
                dumper.push(lastAllPathNode, entry.getValue());
                continue;
            }*//*

            for(; i < baseFilters.size(); i++)
            {
                if(hasPathFlag(baseFilters.get(i).getFilterInfo().mZOrder, entry.getKey())) {
                    if(lastFlagNode != lastAllPathNode) {
                        lastFlagNode.removeAllDownStream();
                    }
                    dumper.push(lastFlagNode, baseFilters.get(i));
                    lastFlagNode.addDownStream(baseFilters.get(i));
                    lastFlagNode = baseFilters.get(i);
                } else {
                    //lastFlagNode.addDownStream(entry.getValue());
                    //dumper.push(lastFlagNode, entry.getValue());
                    break;
                }
            }

            lastFlagNode.addDownStream(entry.getValue());
            dumper.push(lastFlagNode, entry.getValue());
        }

        YYLog.info(this, "performSimpleTwoGraphLayout: " + dumper.getDumpLog());*/
        return 1;
    }
}
