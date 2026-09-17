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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SDK-09: an in-memory reader with no hardware behind it, for building and testing app logic
 * offline. Enforces the same caller-visible contracts the real platform does — FR-SEC-02's
 * all-zero kill password refusal, FR-SEC-04's permanent-lock confirmation, FR-MEM-06's word-range
 * validation — so code that passes against the fake has already cleared the same checks it will
 * hit against a real reader; a fake that skipped these would let a bug through in development that
 * then breaks on hardware, which defeats the point of testing offline at all.
 *
 * <p>What it does <em>not</em> attempt to simulate: RF physics, antenna coverage, or any timing
 * characteristic of the real link (see UHF-SRS-001 §2.4's measured throughput notes — those
 * numbers are hardware-specific and this class does not pretend otherwise). It exists to exercise
 * application logic, not to predict real-world read rates.
 */
public final class FakeUhfSdkSession implements UhfSdkSession {

    /** One simulated tag's full state — the fake analog of what a real tag's own memory holds. */
    private static final class FakeTag {
        byte[] epc;
        final Map<Integer, byte[]> banks = new LinkedHashMap<>(); // bank -> word-addressed bytes
        byte[] accessPassword = new byte[4];
        byte[] killPassword = new byte[4];
        boolean killed;
        int epcLock = LOCK_OPEN; // android.hardware.uhf.UhfSession.LOCK_OPEN's value
    }

    // Mirrors android.hardware.uhf.UhfSession's LOCK_* constants (values only — this class does
    // not statically depend on UhfSession to keep the fake's own logic self-contained).
    private static final int LOCK_OPEN = 0;
    private static final int LOCK_PERM_OPEN = 2;
    private static final int LOCK_PERM_LOCKED = 3;

    private static final int BANK_RESERVED = 0;
    private static final int BANK_EPC = 1;

    private final Map<String, FakeTag> mTags = new LinkedHashMap<>(); // keyed by EPC hex
    private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "FakeUhfSdkSession"); t.setDaemon(true); return t; });

    private volatile boolean mValid = true;
    private int mRegion = 0b0100; // EU, matching this SRS's own development-unit default
    private int mRfPower = 20;
    private int mSafePowerCeiling = 20;
    private int mAntennaMask = 0b0001;
    private boolean mAntennaCheckEnabled;
    private boolean mVerifyWrites = true;
    private ScheduledFuture<?> mStreamFuture;
    private final StreamStatsAccumulator mStats = new StreamStatsAccumulator();

    public FakeUhfSdkSession() {
        for (int i = 0; i < 5; i++) {
            addSimulatedTag(new byte[] {(byte) 0xE2, 0x00, 0x00, 0x00, (byte) i});
        }
    }

    /** Test control, not part of {@link UhfSdkSession}: adds one simulated tag with the given EPC
     * (and empty Reserved/User banks, a zeroed TID). Safe to call at any time, including mid-test,
     * to shape what the next {@link #inventory()} or stream batch reports. */
    public void addSimulatedTag(byte[] epc) {
        FakeTag tag = new FakeTag();
        tag.epc = epc.clone();
        mTags.put(toHex(epc), tag);
    }

    /** Test control: removes a simulated tag by EPC, if present. */
    public void removeSimulatedTag(byte[] epc) {
        mTags.remove(toHex(epc));
    }

    /** Test control: removes every simulated tag — useful for asserting an app's "no tags in
     * field" empty-state handling. */
    public void clearSimulatedTags() {
        mTags.clear();
    }

    @Override public boolean isValid() { return mValid; }

    @Override public void close() {
        stopStreaming();
        mValid = false;
        mScheduler.shutdownNow();
    }

    private void requireValid() throws UhfException {
        if (!mValid) {
            throw new UhfException(UhfException.ERROR_BUSY, "session closed");
        }
    }

    @Override public UhfReaderInfo getReaderInfo() throws UhfException {
        requireValid();
        return new UhfReaderInfo("1.24", 0, true, false, mRegion, "EU", 865.1, 867.9, 30,
                1000, mAntennaMask, mAntennaCheckEnabled);
    }

    @Override public void setRegion(int bandCode) throws UhfException {
        requireValid();
        mRegion = bandCode;
    }

    @Override public void setRfPower(int power) throws UhfException {
        setRfPower(power, false);
    }

    @Override public void setRfPower(int power, boolean allowAboveSafeCeiling)
            throws UhfException {
        requireValid();
        if (power < 0 || power > 30) {
            throw new UhfException(UhfException.ERROR_INVALID_PARAMETER,
                    "power " + power + " outside [0,30]");
        }
        if (power > mSafePowerCeiling && !allowAboveSafeCeiling) {
            throw new UhfException(UhfException.ERROR_INVALID_PARAMETER,
                    "power " + power + " exceeds safe ceiling " + mSafePowerCeiling);
        }
        mRfPower = power;
    }

    @Override public int getRfPower() throws UhfException { requireValid(); return mRfPower; }

    @Override public int getMaxRfPower() throws UhfException { requireValid(); return 30; }

    @Override public int getSafeRfPowerCeiling() throws UhfException {
        requireValid();
        return mSafePowerCeiling;
    }

    @Override public void setEnabledAntennas(int mask) throws UhfException {
        requireValid();
        mAntennaMask = mask;
    }

    @Override public int getEnabledAntennas() throws UhfException {
        requireValid();
        return mAntennaMask;
    }

    @Override public void setAntennaCheckEnabled(boolean enabled) throws UhfException {
        requireValid();
        mAntennaCheckEnabled = enabled;
    }

    @Override public boolean isAntennaCheckEnabled() throws UhfException {
        requireValid();
        return mAntennaCheckEnabled;
    }

    @Override public List<UhfTag> inventory() throws UhfException {
        return inventory(null);
    }

    @Override public List<UhfTag> inventory(InventoryParams params) throws UhfException {
        requireValid();
        List<UhfTag> out = new ArrayList<>();
        long now = System.nanoTime();
        int i = 0;
        for (FakeTag tag : mTags.values()) {
            if (tag.killed) continue;
            out.add(new UhfTag(tag.epc, null, /* rssi= */ -40 - i, /* antenna= */ 1, now, i));
            i++;
        }
        return out;
    }

    @Override public void setStreamParams(StreamParams params) throws UhfException {
        requireValid();
    }

    @Override public void startStreaming(Executor executor, TagCallback callback)
            throws UhfException {
        requireValid();
        stopStreaming();
        mStats.onStreamStart();
        mStreamFuture = mScheduler.scheduleWithFixedDelay(() -> {
            List<UhfTag> batch;
            try {
                batch = inventory();
            } catch (UhfException e) {
                executor.execute(() -> callback.onStreamError(e.getErrorCode(), e.getMessage()));
                return;
            }
            mStats.onBatch(batch);
            executor.execute(() -> callback.onTagsRead(batch));
        }, 0, 200, TimeUnit.MILLISECONDS); // arbitrary, not a throughput claim — see class doc
    }

    @Override public void stopStreaming() {
        if (mStreamFuture != null) {
            mStreamFuture.cancel(false);
            mStreamFuture = null;
        }
    }

    @Override public boolean isStreaming() throws UhfException {
        requireValid();
        return mStreamFuture != null && !mStreamFuture.isDone();
    }

    @Override public StreamStats getStreamStats() throws UhfException {
        requireValid();
        return mStats.snapshot();
    }

    @Override public void resetStreamStats() throws UhfException {
        requireValid();
        mStats.reset();
    }

    @Override public void startBufferedInventory(InventoryParams params) throws UhfException {
        requireValid();
    }

    @Override public void stopBufferedInventory() {}

    @Override public int getBufferedTagCount() throws UhfException {
        requireValid();
        int count = 0;
        for (FakeTag tag : mTags.values()) if (!tag.killed) count++;
        return count;
    }

    @Override public List<UhfTag> readBuffer() throws UhfException { return inventory(); }

    @Override public void clearBuffer() throws UhfException { requireValid(); }

    private FakeTag findTag(TagFilter filter) throws UhfException {
        if (filter.isAny()) {
            List<FakeTag> live = new ArrayList<>();
            for (FakeTag t : mTags.values()) if (!t.killed) live.add(t);
            if (live.size() != 1) {
                throw new UhfException(UhfException.ERROR_INVALID_PARAMETER,
                        "TagFilter.any() requires exactly one tag in field, found " + live.size());
            }
            return live.get(0);
        }
        byte[] key = filter.isByEpc() ? filter.getEpc() : filter.getTid();
        FakeTag tag = mTags.get(toHex(key));
        if (tag == null || tag.killed) {
            throw new UhfException(UhfException.ERROR_INVALID_PARAMETER, "no such tag in field");
        }
        return tag;
    }

    @Override public byte[] readTagMemory(TagFilter filter, int bank, int wordAddress,
            int wordCount) throws UhfException {
        requireValid();
        requireValidWordRange(wordAddress, wordCount);
        FakeTag tag = findTag(filter);
        byte[] full = tag.banks.getOrDefault(bank, new byte[0]);
        byte[] out = new byte[wordCount * 2];
        int available = Math.max(0, Math.min(out.length, full.length - wordAddress * 2));
        if (available > 0) {
            System.arraycopy(full, wordAddress * 2, out, 0, available);
        }
        return out;
    }

    @Override public void writeTagMemory(TagFilter filter, int bank, int wordAddress, byte[] data)
            throws UhfException {
        requireValid();
        if (data.length % 2 != 0) {
            throw new UhfException(UhfException.ERROR_INVALID_PARAMETER,
                    "data length " + data.length + " is not a whole number of words");
        }
        requireValidWordRange(wordAddress, data.length / 2);
        FakeTag tag = findTag(filter);
        int neededLen = wordAddress * 2 + data.length;
        byte[] full = tag.banks.getOrDefault(bank, new byte[0]);
        if (full.length < neededLen) {
            full = Arrays.copyOf(full, neededLen);
        }
        System.arraycopy(data, 0, full, wordAddress * 2, data.length);
        tag.banks.put(bank, full);
        if (mVerifyWrites) {
            byte[] readBack = readTagMemory(filter, bank, wordAddress, data.length / 2);
            if (!Arrays.equals(readBack, data)) {
                throw new UhfException(UhfException.ERROR_PROTOCOL, "fake write verification "
                        + "failed — this should not happen against the in-memory store");
            }
        }
    }

    @Override public void writeEpc(TagFilter filter, byte[] newEpc) throws UhfException {
        requireValid();
        FakeTag tag = findTag(filter);
        if (tag.epcLock != LOCK_OPEN) {
            throw new UhfException(UhfException.ERROR_COMMAND_REJECTED,
                    "EPC bank is locked (state " + tag.epcLock + ") — lockTag() was called for "
                            + "this tag's EPC region");
        }
        mTags.remove(toHex(tag.epc));
        tag.epc = newEpc.clone();
        mTags.put(toHex(newEpc), tag);
    }

    @Override public void blockWrite(TagFilter filter, int bank, int wordAddress, byte[] data)
            throws UhfException {
        writeTagMemory(filter, bank, wordAddress, data);
    }

    @Override public void blockErase(TagFilter filter, int bank, int wordAddress, int wordCount)
            throws UhfException {
        requireValid();
        requireValidWordRange(wordAddress, wordCount);
        writeTagMemory(filter, bank, wordAddress, new byte[wordCount * 2]);
    }

    @Override public void setVerifyWrites(boolean verify) throws UhfException {
        requireValid();
        mVerifyWrites = verify;
    }

    @Override public void killTag(TagFilter filter, byte[] killPassword) throws UhfException {
        requireValid();
        if (killPassword == null || killPassword.length != 4 || isAllZero(killPassword)) {
            throw new UhfException(UhfException.ERROR_INVALID_PARAMETER,
                    "kill password must be exactly 4 bytes and non-zero (FR-SEC-02)");
        }
        findTag(filter).killed = true;
    }

    @Override public void lockTag(TagFilter filter, int region, int lockState,
            byte[] accessPassword, boolean confirmIrreversible) throws UhfException {
        requireValid();
        boolean isPermanent = (lockState == LOCK_PERM_OPEN || lockState == LOCK_PERM_LOCKED);
        if (isPermanent && !confirmIrreversible) {
            throw new UhfException(UhfException.ERROR_INVALID_PARAMETER,
                    "permanent lock state requires confirmIrreversible=true (FR-SEC-04)");
        }
        FakeTag tag = findTag(filter);
        if (region == BANK_EPC) {
            tag.epcLock = lockState;
        }
    }

    @Override public void setAccessPassword(TagFilter filter, byte[] oldPassword,
            byte[] newPassword) throws UhfException {
        requireValid();
        findTag(filter).accessPassword = newPassword.clone();
    }

    @Override public void setKillPassword(TagFilter filter, byte[] accessPassword,
            byte[] newKillPassword) throws UhfException {
        requireValid();
        findTag(filter).killPassword = newKillPassword.clone();
    }

    private static final int MAX_REASONABLE_WORD_COUNT = 128; // mirrors the platform's own bound

    private static void requireValidWordRange(int wordAddress, int wordCount)
            throws UhfException {
        if (wordAddress < 0 || wordCount <= 0
                || wordAddress + wordCount > MAX_REASONABLE_WORD_COUNT) {
            throw new UhfException(UhfException.ERROR_INVALID_PARAMETER,
                    "word range [" + wordAddress + ", " + (wordAddress + wordCount)
                            + ") is invalid or exceeds " + MAX_REASONABLE_WORD_COUNT + " words");
        }
    }

    private static boolean isAllZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    /** Mirrors {@code com.android.server.uhf.UhfService}'s accumulator — same FR-STR-10
     * "measure from the first delivered read" logic, reimplemented here since the SDK cannot
     * depend on platform-internal code. */
    private static final class StreamStatsAccumulator {
        private long totalReadCount;
        private long firstReadNanos = -1;

        synchronized void onStreamStart() { /* elapsed base is set on first onBatch, not here */ }

        synchronized void onBatch(List<UhfTag> batch) {
            if (firstReadNanos < 0 && !batch.isEmpty()) {
                firstReadNanos = System.nanoTime();
            }
            totalReadCount += batch.size();
        }

        synchronized StreamStats snapshot() {
            long elapsedMs = firstReadNanos < 0 ? 0 : (System.nanoTime() - firstReadNanos) / 1_000_000;
            double rate = elapsedMs > 0 ? (totalReadCount * 1000.0 / elapsedMs) : 0.0;
            return new StreamStats(0, totalReadCount, rate, 0, elapsedMs);
        }

        synchronized void reset() {
            totalReadCount = 0;
            firstReadNanos = -1;
        }
    }
}
