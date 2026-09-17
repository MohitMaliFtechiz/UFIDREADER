/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rockchip.uhf.sdk;

import android.hardware.uhf.InventoryParams;
import android.hardware.uhf.StreamParams;
import android.hardware.uhf.StreamStats;
import android.hardware.uhf.TagCallback;
import android.hardware.uhf.TagFilter;
import android.hardware.uhf.UhfException;
import android.hardware.uhf.UhfReaderInfo;
import android.hardware.uhf.UhfSession;
import android.hardware.uhf.UhfTag;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * SDK-03: pure 1:1 delegation to a real {@link UhfSession}, with zero protocol logic of its own —
 * every method here is exactly "call the same method on the delegate, return what it returns."
 * If this class ever does anything more than that, it has stopped being what SDK-03 requires.
 */
final class RealUhfSdkSession implements UhfSdkSession {
    private final UhfSession mDelegate;

    RealUhfSdkSession(UhfSession delegate) {
        mDelegate = delegate;
    }

    @Override public boolean isValid() { return mDelegate.isValid(); }

    @Override public void close() { mDelegate.close(); }

    @Override public UhfReaderInfo getReaderInfo() throws UhfException {
        return mDelegate.getReaderInfo();
    }

    @Override public void setRegion(int bandCode) throws UhfException {
        mDelegate.setRegion(bandCode);
    }

    @Override public void setRfPower(int power) throws UhfException {
        mDelegate.setRfPower(power);
    }

    @Override public void setRfPower(int power, boolean allowAboveSafeCeiling)
            throws UhfException {
        mDelegate.setRfPower(power, allowAboveSafeCeiling);
    }

    @Override public int getRfPower() throws UhfException { return mDelegate.getRfPower(); }

    @Override public int getMaxRfPower() throws UhfException { return mDelegate.getMaxRfPower(); }

    @Override public int getSafeRfPowerCeiling() throws UhfException {
        return mDelegate.getSafeRfPowerCeiling();
    }

    @Override public void setEnabledAntennas(int mask) throws UhfException {
        mDelegate.setEnabledAntennas(mask);
    }

    @Override public int getEnabledAntennas() throws UhfException {
        return mDelegate.getEnabledAntennas();
    }

    @Override public void setAntennaCheckEnabled(boolean enabled) throws UhfException {
        mDelegate.setAntennaCheckEnabled(enabled);
    }

    @Override public boolean isAntennaCheckEnabled() throws UhfException {
        return mDelegate.isAntennaCheckEnabled();
    }

    @Override public List<UhfTag> inventory() throws UhfException { return mDelegate.inventory(); }

    @Override public List<UhfTag> inventory(InventoryParams params) throws UhfException {
        return mDelegate.inventory(params);
    }

    @Override public void setStreamParams(StreamParams params) throws UhfException {
        mDelegate.setStreamParams(params);
    }

    @Override public void startStreaming(Executor executor, TagCallback callback)
            throws UhfException {
        mDelegate.startStreaming(executor, callback);
    }

    @Override public void stopStreaming() { mDelegate.stopStreaming(); }

    @Override public boolean isStreaming() throws UhfException { return mDelegate.isStreaming(); }

    @Override public StreamStats getStreamStats() throws UhfException {
        return mDelegate.getStreamStats();
    }

    @Override public void resetStreamStats() throws UhfException {
        mDelegate.resetStreamStats();
    }

    @Override public void startBufferedInventory(InventoryParams params) throws UhfException {
        mDelegate.startBufferedInventory(params);
    }

    @Override public void stopBufferedInventory() { mDelegate.stopBufferedInventory(); }

    @Override public int getBufferedTagCount() throws UhfException {
        return mDelegate.getBufferedTagCount();
    }

    @Override public List<UhfTag> readBuffer() throws UhfException {
        return mDelegate.readBuffer();
    }

    @Override public void clearBuffer() throws UhfException { mDelegate.clearBuffer(); }

    @Override public byte[] readTagMemory(TagFilter filter, int bank, int wordAddress,
            int wordCount) throws UhfException {
        return mDelegate.readTagMemory(filter, bank, wordAddress, wordCount);
    }

    @Override public void writeTagMemory(TagFilter filter, int bank, int wordAddress, byte[] data)
            throws UhfException {
        mDelegate.writeTagMemory(filter, bank, wordAddress, data);
    }

    @Override public void writeEpc(TagFilter filter, byte[] newEpc) throws UhfException {
        mDelegate.writeEpc(filter, newEpc);
    }

    @Override public void blockWrite(TagFilter filter, int bank, int wordAddress, byte[] data)
            throws UhfException {
        mDelegate.blockWrite(filter, bank, wordAddress, data);
    }

    @Override public void blockErase(TagFilter filter, int bank, int wordAddress, int wordCount)
            throws UhfException {
        mDelegate.blockErase(filter, bank, wordAddress, wordCount);
    }

    @Override public void setVerifyWrites(boolean verify) throws UhfException {
        mDelegate.setVerifyWrites(verify);
    }

    @Override public void killTag(TagFilter filter, byte[] killPassword) throws UhfException {
        mDelegate.killTag(filter, killPassword);
    }

    @Override public void lockTag(TagFilter filter, int region, int lockState,
            byte[] accessPassword, boolean confirmIrreversible) throws UhfException {
        mDelegate.lockTag(filter, region, lockState, accessPassword, confirmIrreversible);
    }

    @Override public void setAccessPassword(TagFilter filter, byte[] oldPassword,
            byte[] newPassword) throws UhfException {
        mDelegate.setAccessPassword(filter, oldPassword, newPassword);
    }

    @Override public void setKillPassword(TagFilter filter, byte[] accessPassword,
            byte[] newKillPassword) throws UhfException {
        mDelegate.setKillPassword(filter, accessPassword, newKillPassword);
    }
}
