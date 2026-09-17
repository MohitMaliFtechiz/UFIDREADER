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
 *
 * SDK-05 reference app: demonstrates every API group this SDK exposes, in the order UHF-SRS-001
 * §5.1's own usage example presents them. This is a demonstration of API surface and permission
 * handling, not a production UI — see README.md's "What's verified" section: this file has not
 * been built with Gradle/AGP or run on a device or emulator in this environment.
 */
package com.rockchip.uhf.sdk.sample

import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.uhf.TagFilter
import android.hardware.uhf.UhfException
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.rockchip.uhf.sdk.UhfSdk
import com.rockchip.uhf.sdk.UhfSdkSession
import com.rockchip.uhf.sdk.inventorySuspend
import com.rockchip.uhf.sdk.tagFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val TAG = "UhfSample"
private const val REQUEST_UHF_PERMISSIONS = 1001

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private var session: UhfSdkSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this)
        setContentView(statusView)

        // §5.1: never assume the feature exists — this is the one check every consumer of this
        // SDK must do before anything else, and the SDK never throws just for asking.
        if (!UhfSdk.isSupported(this)) {
            statusView.text = "No UHF reader on this device."
            return
        }

        if (checkSelfPermission("android.permission.UHF_READ")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.UHF_READ"), REQUEST_UHF_PERMISSIONS)
            return
        }
        openAndDemo()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_UHF_PERMISSIONS
                && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            openAndDemo()
        } else {
            statusView.text = "UHF_READ permission denied."
        }
    }

    private fun openAndDemo() {
        val s = try {
            UhfSdk.open(this)
        } catch (e: UhfException) {
            // Falls back to the offline fake so this sample still demonstrates every API group
            // on a device/emulator with no reader attached — the same fallback an app's own
            // instrumented tests would use (SDK-09).
            Log.w(TAG, "no real reader (${e.message}); using UhfSdk.openFake() instead")
            UhfSdk.openFake()
        }
        session = s

        // §4.6: antenna/throughput tradeoff (see README) — fewer ports, faster reads.
        s.setEnabledAntennas(0b0001)
        // §4.5: never exceed the safe ceiling without deliberately opting in.
        s.setRfPower(20)

        CoroutineScope(Dispatchers.Main).launch {
            // SDK-08: suspend variant of the blocking inventory() call.
            val tags = s.inventorySuspend()
            statusView.text = "Polled inventory: ${tags.size} tag(s)"

            if (tags.isNotEmpty()) {
                demoTagMemory(s, tags.first().epc)
            }

            // SDK-08: Flow variant, matching UHF-SRS-001 §7's own sample almost verbatim.
            s.tagFlow()
                    .distinctUntilChangedBy { it.epcHex }
                    .catch { e -> Log.w(TAG, "stream ended", e) }
                    .onEach { tag -> Log.i(TAG, "${tag.epcHex}  rssi=${tag.rssi}") }
                    .launchIn(CoroutineScope(Dispatchers.Main))
        }
    }

    /** §4.10/§4.11: write, read back, and lock one tag's User bank — the pattern
     * UhfSessionArbiterTest and FakeUhfSdkSessionTest both verify at the unit level. */
    private fun demoTagMemory(s: UhfSdkSession, epc: ByteArray) {
        val filter = TagFilter.byEpc(epc)
        try {
            s.writeTagMemory(filter, /* BANK_USER */ 3, /* wordAddress= */ 0,
                    byteArrayOf(0x12, 0x34))
            val readBack = s.readTagMemory(filter, 3, 0, 1)
            Log.i(TAG, "wrote+verified ${readBack.size} bytes to tag ${filter.epc?.joinToString("")}")
        } catch (e: UhfException) {
            Log.w(TAG, "tag memory demo failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close()
    }
}
