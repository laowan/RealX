package com.ycloud.gpuimagefilter.utils;

import com.ycloud.utils.YYLog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Administrator on 2017/7/28.
 */
public class FilterDataStore<K, V extends Dupable<V>> {

    //same as union in c lang
    public static class OperResult<K, V> {
        public long mSnapshotVer = 0;
        public boolean mSuccess = true;
        public ArrayList<K> mFilterIDList = null;
        public ArrayList<V> mFilterList = null;
        public CopyOnWriteArrayList<V> mFilterCopyOnWriteList = null;
        public V mFilter = null;
        public K mFilterID = null;
    }

    private List<BodiesDetectInfo> mBodiesDetectInfos = new ArrayList<>();
    private List<FacesDetectInfo> mFacesDetectInfos = new ArrayList<>();
    private List<SegmentCacheDetectWrapper.SegmentCacheData> mSegmentCacheInfos = new ArrayList<>();

    private TreeMap<K, FilterInfoSession<K, V>> mDataStore = new TreeMap<K, FilterInfoSession<K, V>>();
    AtomicLong mSnapshotVer = new AtomicLong(0);

    public static class FilterInfoSession<K, V extends Dupable<V>> {
        private TreeMap<K, V> mFilterID2FilterInfo = new TreeMap<K, V>();
        private TreeMap<K, CopyOnWriteArrayList<V>> mFilterType2FilterInfo = new TreeMap<K, CopyOnWriteArrayList<V>>();
        private IFilterInfoListener mListener = null;

        public void setFilterInfoListener(IFilterInfoListener listener) {
            mListener = listener;
        }

        public IFilterInfoListener getFilterInfoListener() {
            return mListener;
        }

        public void addFilter(K key, K type, V value) {
            synchronized (this) {
                mFilterID2FilterInfo.put(key, value);
                CopyOnWriteArrayList<V> list = mFilterType2FilterInfo.get(type);
                if (list == null) {
                    list = new CopyOnWriteArrayList<V>();
                    mFilterType2FilterInfo.put(type, list);
                }
                list.add(value);
            }
        }

        public boolean removeFilter(K key, K type) {
            synchronized (this) {
                V info = mFilterID2FilterInfo.remove(key);
                if (info == null) {
                    return false;
                }
                CopyOnWriteArrayList<V> list = mFilterType2FilterInfo.get(type);
                if (list != null) {
                    ListIterator<V> it = list.listIterator();
                    while (it.hasNext()) {
                        V e = it.next();
                        if (e.equals(info)) {
                            list.remove(e);
                            break;
                        }
                    }
                    if (list.isEmpty()) {
                        mFilterType2FilterInfo.remove(type);
                    }
                }
                return true;
            }
        }

        public ArrayList<K> removeFilter(K type) {
            synchronized (this) {
                CopyOnWriteArrayList<V> list = mFilterType2FilterInfo.get(type);
                if (list == null)
                    return null;

                ArrayList<K> ret = new ArrayList<>(0);
                ListIterator<V> it = list.listIterator();

                while (it.hasNext()) {
                    V info = it.next();
                    //数据量很小，性能不是问题.

                    Iterator<Map.Entry<K, V>> sIt = mFilterID2FilterInfo.entrySet().iterator();
                    while (sIt.hasNext()) {
                        Map.Entry<K, V> entry = sIt.next();
                        if (entry.getValue().equals(info)) {
                            ret.add(entry.getKey());
                            sIt.remove();
                        }
                    }
                }

                mFilterType2FilterInfo.remove(type);
                return ret;
            }
        }

        public ArrayList<K> removeAllFilter() {
            ArrayList<K> ret = new ArrayList<K>();
            synchronized (this) {
                Iterator<Map.Entry<K, V>> sIt = mFilterID2FilterInfo.entrySet().iterator();
                while (sIt.hasNext()) {
                    Map.Entry<K, V> entry = sIt.next();
                    ret.add(entry.getKey());
                }
                mFilterID2FilterInfo.clear();
                mFilterType2FilterInfo.clear();
                return ret;
            }
        }

        public void modifyFilter(V info) {
            //mFilterType2FilterInfo 和  mFieldID2FilterInfo中保持的info是同一个对象，只要改变一个就可以了.
            synchronized (this) {

            }
        }

        public V getFilter(K key) {
            synchronized (this) {
                V obj = mFilterID2FilterInfo.get(key);
                if (obj != null && obj.isDupable()) {
                    return obj.duplicate();
                }
                return obj;
            }
        }

        public void setFilter(K key, V filterInfo) {
            synchronized (this) {
                mFilterID2FilterInfo.put(key, filterInfo);
            }
        }

        public V unsafe_getFilter(K key) {
            return mFilterID2FilterInfo.get(key);
        }

        public CopyOnWriteArrayList<V> getFilterInfoByType(K type) {
            synchronized (this) {
                return mFilterType2FilterInfo.get(type);
            }
        }

        //not deep copy the element
        public ArrayList<V> getAllFilter() {
            if (mFilterID2FilterInfo.isEmpty())
                return null;

            ArrayList<V> ret = new ArrayList<>();
            Iterator<Map.Entry<K, V>> it = mFilterID2FilterInfo.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<K, V> entry = it.next();
//                if (entry.getValue().isDupable()) {
//                    ret.add(entry.getValue().duplicate());
//                } else {
//                    ret.add(entry.getValue());
//                }
                ret.add(entry.getValue());
            }
            return ret;
        }
    }

    private FilterInfoSession<K, V> getFilterSession(K sessionID, boolean create) {
        FilterInfoSession<K, V> session = mDataStore.get(sessionID);
        if (session == null && create) {
            session = new FilterInfoSession<>();
            mDataStore.put(sessionID, session);
        }
        return session;
    }

    public void setFilterInfoListener(IFilterInfoListener listener, K sessionID) {
        FilterInfoSession<K, V> session = getFilterSession(sessionID, true);
        session.setFilterInfoListener(listener);
    }

    public IFilterInfoListener getFilterInfoListener(K sessionID) {
        FilterInfoSession<K, V> session = getFilterSession(sessionID, true);
        return session.getFilterInfoListener();
    }

    public long addFilter(K key, K type, V value, K sessionID) {
        synchronized (this) {
            FilterInfoSession<K, V> session = getFilterSession(sessionID, true);
            session.addFilter(key, type, value);
            long version = mSnapshotVer.incrementAndGet();
            YYLog.info(this, "datastore.addFilter, version=" + version);
            return version;
        }
    }

    public OperResult<K, V> removeFilter(K key, K type, K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();

            YYLog.info(this, "datastore.removeFilter, version=" + res.mSnapshotVer);
            FilterInfoSession<K, V> session = getFilterSession(sessionID, false);
            if (session == null) {
                res.mSuccess = false;
                return res;
            }

            session.removeFilter(key, type);
            return res;
        }
    }

    public OperResult<K, V> removeFilter(K type, K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();
            YYLog.info(this, "datastore.removeFilter, version=" + res.mSnapshotVer);

            FilterInfoSession<K, V> session = getFilterSession(sessionID, false);
            if (session == null) {
                res.mSuccess = false;
                return res;
            }
            res.mFilterIDList = session.removeFilter(type);
            return res;
        }
    }

    public OperResult<K, V> removeAllFilter(K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();
            YYLog.info(this, "datastore.removeAllFilter, version=" + res.mSnapshotVer);

            FilterInfoSession<K, V> session = getFilterSession(sessionID, false);
            if (session == null) {
                res.mSuccess = false;
                return res;
            }
            res.mFilterIDList = session.removeAllFilter();
            return res;
        }
    }

    public long modifyFilter(V info) {
        //mFilterType2FilterInfo 和  mFieldID2FilterInfo中保持的info是同一个对象，只要改变一个就可以了.
        synchronized (this) {

            long version = mSnapshotVer.incrementAndGet();
            YYLog.info(this, "datastore.modifyFilter, version=" + version);
            return version;
        }
    }

    public OperResult<K, V> getFilter(K key, K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.get();

            FilterInfoSession<K, V> session = getFilterSession(sessionID, false);
            if (session != null) {
                res.mFilter = session.getFilter(key);
            }
            return res;
        }
    }

    public void setFilter(K key, K sessionID, V filterInfo) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.get();

            FilterInfoSession<K, V> session = getFilterSession(sessionID, false);
            if (session != null) {
                session.setFilter(key, filterInfo);
            }
        }
    }

    //对于一个SessionID的server端的FilterGroup,不会在多个线程中使用.
    public OperResult<K, V> getFilterInfoByType(K type, K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.get();

            FilterInfoSession<K, V> session = getFilterSession(sessionID, false);
            if (session == null)
                return res;

            CopyOnWriteArrayList<V> list = session.getFilterInfoByType(type);
            if (list == null || list.isEmpty()) {
                return res;
            }

            if (!list.get(0).isDupable()) {
                res.mFilterCopyOnWriteList = list;
                return res;
            }

            CopyOnWriteArrayList<V> ret = new CopyOnWriteArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                V obj = list.get(i);
                ret.add(obj.duplicate());
            }

            res.mFilterCopyOnWriteList = ret;
            return res;
        }
    }

    public OperResult<K, V> getFilerInfoBySessionID(K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.get();

            FilterInfoSession<K, V> session = getFilterSession(sessionID, false);
            if (session != null) {
                res.mFilterList = session.getAllFilter();
            }

//            YYLog.info(this, "datastore.getFilerInfoBySessionID, version=" + res.mSnapshotVer);
            return res;
        }
    }

    //not thread-safe
    public CopyOnWriteArrayList<V> unSaft_GetFilterInfoByType(K type, K sessionID) {
        FilterInfoSession<K, V> session = getFilterSession(sessionID, false);
        if (session != null) {
            return session.getFilterInfoByType(type);
        }
        return null;
    }

    public V unSafe_getFilter(K key, K sessionID) {
        FilterInfoSession<K, V> session = getFilterSession(sessionID, false);
        if (session != null) {
            //bug bug. copy
            return session.unsafe_getFilter(key);
        }
        return null;
    }

    public List<BodiesDetectInfo> getBodyDetectInfo() {
        return mBodiesDetectInfos;
    }

    public List<FacesDetectInfo> getFaceDetectInfo() {
        return mFacesDetectInfos;
    }

    public List<SegmentCacheDetectWrapper.SegmentCacheData> getSegmentCacheInfo() {
        return mSegmentCacheInfos;
    }
}
