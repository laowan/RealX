package com.ycloud.gpuimagefilter.utils;

import com.ycloud.gpuimagefilter.param.BaseFilterParameter;
import com.ycloud.utils.YYLog;

import java.util.List;

/**
 * Created by Administrator on 2017/8/1.
 */

public class FilterInfoDataStore<K, V extends FilterDataInterface<V>> extends FilterDataStore<K, V> {
    public OperResult addFilterParameter(K filterID, BaseFilterParameter parameter, K sessionID) {
        //TODO.
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();
            res.mSuccess = false;

            if ((res.mFilter = unSafe_getFilter(filterID, sessionID)) != null) {
                res.mFilter.addFilterParameter(parameter);
                if (res.mFilter.isDupable()) {
                    res.mFilter = res.mFilter.duplicate();
                }
                res.mSuccess = true;
            }

            YYLog.info(this, "datastore.addFilterParameter, version=" + res.mSnapshotVer/* + parameter.toString()*/);
            return res;
        }
    }

    public OperResult resetFilterParameter(K filterID, BaseFilterParameter parameter, K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();
            res.mSuccess = false;

            if ((res.mFilter = unSafe_getFilter(filterID, sessionID)) != null) {
                res.mFilter.resetFilterParameter(parameter);
                if (res.mFilter.isDupable()) {
                    res.mFilter = res.mFilter.duplicate();
                }
                res.mSuccess = true;
            }

            YYLog.info(this, "datastore.resetFilterParameter, version=" + res.mSnapshotVer/* + parameter.toString()*/);
            return res;
        }
    }

    public OperResult modifyFilterParameter(K filterID, int paramID, BaseFilterParameter parameter, K sessionID, boolean isDuplicate) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();
            res.mSuccess = false;
            if ((res.mFilter = unSafe_getFilter(filterID, sessionID)) != null) {
                res.mFilter.modifyFilterParameter(paramID, parameter);
                if (isDuplicate && res.mFilter.isDupable()) {
                    res.mFilter = res.mFilter.duplicate();
                    //too more logs, so set to debug level
                    YYLog.debug(this, "datastore.modifyFilterParameter, version=" + res.mSnapshotVer/* + parameter.toString()*/);
                } else {
                    YYLog.debug(this, "datastore.modifyFilterParameter, version=" + res.mSnapshotVer/*+ res.mFilter.getFilterParameter(paramID).toString()*/);
                }
                res.mSuccess = true;
            }
            return res;
        }
    }

    public OperResult updateFilterParameter(K filterID, int paramID, BaseFilterParameter parameter, K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();
            res.mSuccess = false;
            if ((res.mFilter = unSafe_getFilter(filterID, sessionID)) != null) {
                res.mFilter.updateFilterParameter(paramID, parameter);
                res.mSuccess = true;
            }
            return res;
        }
    }

    public OperResult modifyFilterZOrder(K filterID, int zOrder, K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();
            res.mSuccess = false;
            if ((res.mFilter = unSafe_getFilter(filterID, sessionID)) != null) {
                res.mFilter.modifyFilterZOrder(zOrder);
                res.mFilter = res.mFilter.duplicate();
                res.mSuccess = true;
            }
            return res;
        }
    }

    public OperResult removeFilterParameter(K filterID, int paramID, K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();
            res.mSuccess = false;

            if ((res.mFilter = unSafe_getFilter(filterID, sessionID)) != null) {
                res.mFilter.removeFilterParameter(paramID);
                if (res.mFilter.isDupable()) {
                    res.mFilter = res.mFilter.duplicate();
                }
                res.mSuccess = true;
            }

            YYLog.info(this, "datastore.modifyFilterParameter, version=" + res.mSnapshotVer + " parameterID=" + paramID);
            return res;
        }
    }

    public OperResult removeFilterParameter(K filterID, K sessionID) {
        synchronized (this) {
            OperResult<K, V> res = new OperResult<>();
            res.mSnapshotVer = mSnapshotVer.incrementAndGet();
            res.mSuccess = false;

            if ((res.mFilter = unSafe_getFilter(filterID, sessionID)) != null) {
                res.mFilter.removeFilterParameter();
                if (res.mFilter.isDupable()) {
                    res.mFilter = res.mFilter.duplicate();
                }
                res.mSuccess = true;
            }
            return res;
        }
    }

    public long getSnapshotVer() {
        return mSnapshotVer.incrementAndGet();
    }
}
