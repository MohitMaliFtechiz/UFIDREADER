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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.hardware.uhf.TagFilter;
import android.hardware.uhf.UhfException;
import android.hardware.uhf.UhfTag;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Host-side JUnit tests for {@link FakeUhfSdkSession} (SDK-09), compiled and run against the real
 * {@code android.hardware.uhf} classes (via the system-API stub jar) rather than any hand-rolled
 * substitute — see this module's README for the exact toolchain. These exercise the same
 * caller-visible contracts UhfSessionArbiterTest exercises against the platform arbiter
 * (FR-SEC-02, FR-SEC-04, FR-MEM-06/07), because a fake that let those slip would defeat SDK-09's
 * purpose: an app that only ever tested against a lenient fake would meet these checks for the
 * first time on a real device.
 */
public class FakeUhfSdkSessionTest {

    @Test
    public void defaultConstructor_populatesFiveSimulatedTags() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        assertEquals(5, session.inventory().size());
    }

    @Test
    public void addAndRemoveSimulatedTag_changeWhatInventoryReports() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        session.clearSimulatedTags();
        assertTrue(session.inventory().isEmpty());

        byte[] epc = {1, 2, 3, 4};
        session.addSimulatedTag(epc);
        List<UhfTag> tags = session.inventory();
        assertEquals(1, tags.size());
        assertArrayEquals(epc, tags.get(0).getEpc());

        session.removeSimulatedTag(epc);
        assertTrue(session.inventory().isEmpty());
    }

    @Test
    public void writeThenReadTagMemory_roundTrips() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        session.clearSimulatedTags();
        byte[] epc = {1, 1, 1, 1};
        session.addSimulatedTag(epc);
        TagFilter filter = TagFilter.byEpc(epc);

        session.writeTagMemory(filter, 3 /* BANK_USER */, 0, new byte[] {0x55, 0x66});
        byte[] readBack = session.readTagMemory(filter, 3, 0, 1);

        assertArrayEquals(new byte[] {0x55, 0x66}, readBack);
    }

    @Test
    public void readTagMemory_rejectsInvalidWordRange_sameAsThePlatform() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        session.clearSimulatedTags();
        byte[] epc = {2, 2, 2, 2};
        session.addSimulatedTag(epc);

        try {
            session.readTagMemory(TagFilter.byEpc(epc), 3, -1, 2);
            fail("expected rejection of a negative word address (FR-MEM-06)");
        } catch (UhfException expected) {
            assertEquals(UhfException.ERROR_INVALID_PARAMETER, expected.getErrorCode());
        }
    }

    @Test
    public void killTag_rejectsAllZeroPassword_sameAsThePlatform() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        session.clearSimulatedTags();
        byte[] epc = {3, 3, 3, 3};
        session.addSimulatedTag(epc);

        try {
            session.killTag(TagFilter.byEpc(epc), new byte[] {0, 0, 0, 0});
            fail("expected rejection of an all-zero kill password (FR-SEC-02)");
        } catch (UhfException expected) {
            assertEquals(UhfException.ERROR_INVALID_PARAMETER, expected.getErrorCode());
        }
        assertEquals("must not have been killed", 1, session.inventory().size());
    }

    @Test
    public void killTag_withNonZeroPasswordRemovesItFromInventory() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        session.clearSimulatedTags();
        byte[] epc = {4, 4, 4, 4};
        session.addSimulatedTag(epc);

        session.killTag(TagFilter.byEpc(epc), new byte[] {0, 0, 0, 1});

        assertTrue(session.inventory().isEmpty());
    }

    @Test
    public void lockTag_permanentStateWithoutConfirmIsRejected_sameAsThePlatform()
            throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        session.clearSimulatedTags();
        byte[] epc = {5, 5, 5, 5};
        session.addSimulatedTag(epc);

        try {
            session.lockTag(TagFilter.byEpc(epc), /* region= BANK_EPC */ 1, /* LOCK_PERM_LOCKED */ 3,
                    null, /* confirmIrreversible= */ false);
            fail("expected rejection of a permanent lock without confirmation (FR-SEC-04)");
        } catch (UhfException expected) {
            assertEquals(UhfException.ERROR_INVALID_PARAMETER, expected.getErrorCode());
        }
        // Not locked: writeEpc must still succeed.
        session.writeEpc(TagFilter.byEpc(epc), new byte[] {6, 6, 6, 6});
    }

    @Test
    public void lockTag_permanentLockThenWriteEpcIsRejected() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        session.clearSimulatedTags();
        byte[] epc = {7, 7, 7, 7};
        session.addSimulatedTag(epc);

        session.lockTag(TagFilter.byEpc(epc), /* BANK_EPC */ 1, /* LOCK_PERM_LOCKED */ 3, null,
                /* confirmIrreversible= */ true);

        try {
            session.writeEpc(TagFilter.byEpc(epc), new byte[] {8, 8, 8, 8});
            fail("expected the fake to actually honour its own lock state");
        } catch (UhfException expected) {
            assertEquals(UhfException.ERROR_COMMAND_REJECTED, expected.getErrorCode());
        }
    }

    @Test
    public void findTag_anyRequiresExactlyOneTagInField() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        session.clearSimulatedTags();
        session.addSimulatedTag(new byte[] {9, 9, 9, 9});
        session.addSimulatedTag(new byte[] {9, 9, 9, 10});

        try {
            session.readTagMemory(TagFilter.any(), 3, 0, 1);
            fail("expected rejection: two tags in field, TagFilter.any() is ambiguous");
        } catch (UhfException expected) {
            assertEquals(UhfException.ERROR_INVALID_PARAMETER, expected.getErrorCode());
        }
    }

    @Test
    public void setRfPower_enforcesTheSafeCeilingSameAsThePlatform() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        try {
            session.setRfPower(25, false);
            fail("expected rejection above the default safe ceiling of 20");
        } catch (UhfException expected) {
            assertEquals(UhfException.ERROR_INVALID_PARAMETER, expected.getErrorCode());
        }
        session.setRfPower(25, true); // override must succeed
        assertEquals(25, session.getRfPower());
    }

    @Test
    public void streaming_deliversBatchesUntilStopped() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        CountDownLatch gotBatch = new CountDownLatch(1);
        AtomicReference<List<UhfTag>> lastBatch = new AtomicReference<>();
        Executor immediate = Runnable::run;

        session.startStreaming(immediate, new android.hardware.uhf.TagCallback() {
            @Override public void onTagsRead(List<UhfTag> batch) {
                lastBatch.set(batch);
                gotBatch.countDown();
            }
            @Override public void onStreamError(int errorCode, String message) {}
            @Override public void onBatchesDropped(int count) {}
        });

        assertTrue("expected at least one batch within 2s", gotBatch.await(2, TimeUnit.SECONDS));
        assertTrue(session.isStreaming());
        assertEquals(5, lastBatch.get().size());

        session.stopStreaming();
        assertFalse(session.isStreaming());
    }

    @Test
    public void close_invalidatesTheSessionForFurtherCalls() throws Exception {
        FakeUhfSdkSession session = new FakeUhfSdkSession();
        session.close();
        assertFalse(session.isValid());
        try {
            session.inventory();
            fail("expected rejection of a call on a closed session");
        } catch (UhfException expected) {
            assertEquals(UhfException.ERROR_BUSY, expected.getErrorCode());
        }
    }
}
