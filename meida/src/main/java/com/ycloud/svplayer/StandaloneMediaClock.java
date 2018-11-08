/*
 * Copyright 2014 Mario Guggenberger <mg@protyposis.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ycloud.svplayer;

/**
 * Created by Mario on 14.06.2014.
 *
 * A time base in microseconds for media playback.
 */
class StandaloneMediaClock implements  IMediaClock {

    private long mStartTime;
    private double mSpeed = 1.0;
    private long mMediaTime;

    public StandaloneMediaClock() {
        start();
    }

    public void start() {
        startAt(0);
    }

    @Override
    public void startAt(long mediaTime) {
        mMediaTime = mediaTime;
        mStartTime = microTime() - mediaTime;
    }

    public long getCurrentTime() {
        return microTime() - mStartTime;
    }

    @Override
    public long getOffsetFrom(long from) {
        return  from - getCurrentTime();
    }

    public double getSpeed() {
        return mSpeed;
    }

    /**
     * Sets the playback speed. Can be used for fast forward and slow motion.
     * speed 0.5 = half speed / slow motion
     * speed 2.0 = double speed / fast forward
     * speed影响system clock的计算,例如speed = 2.0,system clock的差值会被放大2倍;反之,例如speed = 0.5, system clock的差值被缩小2倍
     * @param speed
     */
    public void setSpeed(double speed) {
        mSpeed = speed;
    }

    private long microTime() {
        return (long)(System.nanoTime() / 1000 * mSpeed);
    }

    @Override
    public void startAtIncrase(long mediaTime) {
        if(mediaTime>mMediaTime) {
            startAt(mediaTime);
        }
    }
}
