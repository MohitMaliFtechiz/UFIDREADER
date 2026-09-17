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
import android.hardware.uhf.UhfTag;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * SDK-01/03/09: the method surface {@code android.hardware.uhf.UhfSession} exposes, reproduced as
 * an interface so {@link UhfSdk} can hand out either a real, platform-API-backed session
 * ({@link RealUhfSdkSession}) or an offline one with no reader and no {@code @SystemApi} access
 * ({@link FakeUhfSdkSession}) behind the same type. This interface is the <em>only</em> reason an
 * indirection exists at all — {@link RealUhfSdkSession} adds zero logic of its own beyond
 * unwrapping exceptions that don't apply here (SDK-03: "a protocol fix in the platform shall not
 * require an SDK release" — true here because this interface's shape mirrors {@code UhfSession}
 * exactly, not because it adds any behavior).
 *
 * <p>Every method here matches its {@code UhfSession} counterpart's name, parameters and thrown
 * exception, verified by direct comparison against the compiled platform class during this SDK's
 * build (see this module's README) rather than left to drift.
 */
public interface UhfSdkSession extends AutoCloseable {
    boolean isValid();

    @Override
    void close();

    UhfReaderInfo getReaderInfo() throws UhfException;

    void setRegion(int bandCode) throws UhfException;

    void setRfPower(int power) throws UhfException;

    void setRfPower(int power, boolean allowAboveSafeCeiling) throws UhfException;

    int getRfPower() throws UhfException;

    int getMaxRfPower() throws UhfException;

    int getSafeRfPowerCeiling() throws UhfException;

    void setEnabledAntennas(int mask) throws UhfException;

    int getEnabledAntennas() throws UhfException;

    void setAntennaCheckEnabled(boolean enabled) throws UhfException;

    boolean isAntennaCheckEnabled() throws UhfException;

    List<UhfTag> inventory() throws UhfException;

    List<UhfTag> inventory(InventoryParams params) throws UhfException;

    void setStreamParams(StreamParams params) throws UhfException;

    void startStreaming(Executor executor, TagCallback callback) throws UhfException;

    void stopStreaming();

    boolean isStreaming() throws UhfException;

    StreamStats getStreamStats() throws UhfException;

    void resetStreamStats() throws UhfException;

    void startBufferedInventory(InventoryParams params) throws UhfException;

    void stopBufferedInventory();

    int getBufferedTagCount() throws UhfException;

    List<UhfTag> readBuffer() throws UhfException;

    void clearBuffer() throws UhfException;

    byte[] readTagMemory(TagFilter filter, int bank, int wordAddress, int wordCount)
            throws UhfException;

    void writeTagMemory(TagFilter filter, int bank, int wordAddress, byte[] data)
            throws UhfException;

    void writeEpc(TagFilter filter, byte[] newEpc) throws UhfException;

    void blockWrite(TagFilter filter, int bank, int wordAddress, byte[] data) throws UhfException;

    void blockErase(TagFilter filter, int bank, int wordAddress, int wordCount)
            throws UhfException;

    void setVerifyWrites(boolean verify) throws UhfException;

    void killTag(TagFilter filter, byte[] killPassword) throws UhfException;

    void lockTag(TagFilter filter, int region, int lockState, byte[] accessPassword,
            boolean confirmIrreversible) throws UhfException;

    void setAccessPassword(TagFilter filter, byte[] oldPassword, byte[] newPassword)
            throws UhfException;

    void setKillPassword(TagFilter filter, byte[] accessPassword, byte[] newKillPassword)
            throws UhfException;
}
