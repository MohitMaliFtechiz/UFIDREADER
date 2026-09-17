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

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.uhf.UhfException;
import android.hardware.uhf.UhfManager;

/**
 * SDK-01: the entry point third-party apps build against, distributed as {@code
 * com.rockchip.uhf:uhf-sdk} (SDK-01's {@code com.<oem>.uhf:uhf-sdk} with the OEM this SRS was
 * written for). {@code UhfManager}/{@code UhfSession} are {@code @SystemApi}, unreachable from a
 * normal Gradle build without the platform source; this module's own build depends on the stub
 * jar generated from the platform's API signature files instead (SDK-02) — see this module's
 * README for how that stub is produced and its one known gap.
 */
public final class UhfSdk {

    private UhfSdk() {}

    /**
     * SDK-04: never throws, on any device — including one running a platform build that predates
     * this SRS entirely, as long as the SDK's own classes loaded (which requires nothing device-
     * specific: {@code FEATURE_UHF_READER} and {@code UhfManager} are ordinary compiled-in
     * framework classes on any device built from a tree with this patch series, present whether
     * or not a reader is attached — the value returned is what varies, never whether the check
     * itself is safe to make).
     */
    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UHF_READER);
    }

    /**
     * Opens a session against the real reader. Callers should check {@link #isSupported} first;
     * this throws {@link UhfException} with {@link UhfException#ERROR_DEVICE_LOST} rather than
     * returning null on a device with no reader, so a caller that skips the check still fails in
     * one obvious place instead of getting a {@code NullPointerException} three calls later.
     */
    public static UhfSdkSession open(Context context) throws UhfException {
        UhfManager manager = context.getSystemService(UhfManager.class);
        if (manager == null) {
            throw new UhfException(UhfException.ERROR_DEVICE_LOST,
                    "no UHF reader on this device — call isSupported() before open()");
        }
        return new RealUhfSdkSession(manager.openSession());
    }

    /**
     * SDK-09: an offline session backed by {@link FakeUhfSdkSession} — no reader, no {@code
     * @SystemApi} access, no device required. For building and testing app logic without
     * hardware; every write actually mutates the fake's in-memory tag store, so a
     * write-then-read-back flow exercises the same code path an app would run against a real tag.
     */
    public static UhfSdkSession openFake() {
        return new FakeUhfSdkSession();
    }
}
