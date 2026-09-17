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

package com.rockchip.uhf.testapp;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.uhf.UhfDeviceInfo;
import android.hardware.uhf.UhfException;
import android.hardware.uhf.UhfManager;
import android.hardware.uhf.UhfReaderInfo;
import android.hardware.uhf.UhfSession;
import android.hardware.uhf.UhfTag;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * UHF-SRS-001 platform smoke test - exercises {@link UhfManager}/{@link UhfSession} directly
 * (unlike sdk/sample/, which goes through the uhf-sdk facade) so it can be built with plain
 * Soong ({@code m UhfTestApp}) and adb-installed without needing Gradle/AGP, which this build
 * environment doesn't have. No pass/fail assertions - it just reports exactly what it sees at
 * every step, since the point is to see how a real reader (or its absence) actually behaves.
 */
public class MainActivity extends Activity {
    private static final String TAG = "UhfTestApp";

    private TextView mLog;
    private UhfManager mManager;
    private UhfSession mSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        mLog = new TextView(this);
        mLog.setTextIsSelectable(true);
        mLog.setTypeface(android.graphics.Typeface.MONOSPACE);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(mLog);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button inventoryBtn = new Button(this);
        inventoryBtn.setText("Inventory");
        inventoryBtn.setOnClickListener(v -> runInventory());
        buttons.addView(inventoryBtn);

        Button clearBtn = new Button(this);
        clearBtn.setText("Clear log");
        clearBtn.setOnClickListener(v -> mLog.setText(""));
        buttons.addView(clearBtn);

        root.addView(buttons);
        root.addView(scroll);
        setContentView(root);

        checkFeatureAndOpen();
    }

    private void checkFeatureAndOpen() {
        boolean hasFeature = getPackageManager().hasSystemFeature("android.hardware.uhf");
        log("FEATURE_UHF_READER present: " + hasFeature);

        mManager = getSystemService(UhfManager.class);
        if (mManager == null) {
            log("getSystemService(UhfManager.class) returned null - "
                    + "service not registered on this build/device.");
            return;
        }
        log("UhfManager obtained.");

        boolean available = mManager.isReaderAvailable();
        log("isReaderAvailable(): " + available);

        List<UhfDeviceInfo> readers = mManager.getReaders();
        log("getReaders(): " + readers.size() + " reader(s)");
        for (UhfDeviceInfo info : readers) {
            log("  readerId=" + info.getReaderId() + " devicePath=" + info.getDevicePath());
        }

        if (!available) {
            log("No reader available - stopping here. This is expected if no UHF reader "
                    + "hardware is attached; everything above this line already confirms the "
                    + "platform (service registration, feature flag, HAL binder) is alive.");
            return;
        }

        try {
            mSession = mManager.openSession();
            log("openSession() ok.");
            UhfReaderInfo readerInfo = mSession.getReaderInfo();
            log("Reader: model info -> antennaMask=0x" + Integer.toHexString(
                    readerInfo.getAntennaMask()) + " maxRfPower=" + readerInfo.getMaxRfPower());
        } catch (UhfException e) {
            log("openSession()/getReaderInfo() failed: errorCode=" + e.getErrorCode()
                    + " message=" + e.getMessage());
        }
    }

    private void runInventory() {
        if (mSession == null || !mSession.isValid()) {
            log("No open session - nothing to inventory.");
            return;
        }
        try {
            List<UhfTag> tags = mSession.inventory();
            log("inventory(): " + tags.size() + " tag(s)");
            for (UhfTag tag : tags) {
                log("  epc=" + tag.getEpcHex() + " rssi=" + tag.getRssi()
                        + " antenna=" + tag.getAntenna() + " reads=" + tag.getReadCount());
            }
        } catch (UhfException e) {
            log("inventory() failed: errorCode=" + e.getErrorCode()
                    + " message=" + e.getMessage());
        }
    }

    private void log(String message) {
        String stamped = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(
                new java.util.Date()) + "  " + message;
        Log.i(TAG, message);
        runOnUiThread(() -> mLog.append(stamped + "\n"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSession != null) {
            mSession.close();
        }
    }
}
