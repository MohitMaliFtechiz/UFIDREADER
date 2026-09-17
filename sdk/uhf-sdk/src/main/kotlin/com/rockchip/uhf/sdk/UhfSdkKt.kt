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

package com.rockchip.uhf.sdk

import android.hardware.uhf.StreamParams
import android.hardware.uhf.TagCallback
import android.hardware.uhf.UhfException
import android.hardware.uhf.UhfTag
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * SDK-08: Kotlin coroutine and [Flow] adapters over [UhfSdkSession].
 *
 * The suspend function here is deliberately **not** named `inventory` — verified empirically
 * during this module's build (not assumed) that a `suspend fun UhfSdkSession.inventory()`
 * extension is silently shadowed by [UhfSdkSession.inventory]'s existing blocking member with the
 * same erased call shape: kotlinc compiles it but emits "extension is shadowed by a member" and
 * the extension becomes unreachable through ordinary call syntax. [inventorySuspend] avoids that
 * trap by not colliding with the member at all. [tagFlow] has no such collision (no member of that
 * name exists) and matches the SRS's own naming exactly.
 */

/** Runs the interface's blocking [UhfSdkSession.inventory] on [Dispatchers.IO]. */
suspend fun UhfSdkSession.inventorySuspend(): List<UhfTag> =
        withContext(Dispatchers.IO) { inventory() }

/**
 * A cold [Flow] of tag reads: starts streaming when collection begins, stops when the collector
 * cancels or the flow is otherwise torn down — mirroring [UhfSdkSession.startStreaming] /
 * [UhfSdkSession.stopStreaming]'s own lifecycle rather than adding a second one. Errors from
 * [TagCallback.onStreamError] close the flow with a [UhfException] carrying the same error code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun UhfSdkSession.tagFlow(params: StreamParams = StreamParams.Builder().build()): Flow<UhfTag> =
        callbackFlow {
    setStreamParams(params)
    val executor = Executor { command -> command.run() } // callback bodies below are already
                                                           // non-blocking; no extra thread needed
    startStreaming(executor, object : TagCallback {
        override fun onTagsRead(batch: MutableList<UhfTag>) {
            for (tag in batch) {
                trySend(tag)
            }
        }

        override fun onStreamError(errorCode: Int, message: String) {
            close(UhfException(errorCode, message))
        }

        override fun onBatchesDropped(count: Int) {
            // FR-STR-11's drop signal has no Flow-shaped equivalent to surface through without
            // changing the element type to something other than UhfTag; a collector that needs
            // this stays on the Java TagCallback surface directly instead.
        }
    })
    awaitClose { stopStreaming() }
}
