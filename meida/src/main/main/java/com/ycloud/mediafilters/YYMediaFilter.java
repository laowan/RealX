package com.ycloud.mediafilters;

import com.ycloud.ymrmodel.YYMediaSample;

/**
 * Created by kele on 2017/4/27.
 */

public class YYMediaFilter extends AbstractYYMediaFilter
{
    @Override
    public boolean processMediaSample(YYMediaSample sample, Object upstream) {
        deliverToDownStream(sample);
        return false;
    }
}
