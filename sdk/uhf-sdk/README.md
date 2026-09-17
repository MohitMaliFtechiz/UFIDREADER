# uhf-sdk

SDK-01/03/04/08/09: a thin Kotlin/Java facade over the platform's `android.hardware.uhf.UhfManager`
(UHF-SRS-001), distributed as `com.rockchip.uhf:uhf-sdk` so third-party apps can build against it
without platform source or `@SystemApi` access.

## Integration guide (SDK-06)

### Permissions

Declare in your app's manifest:

```xml
<uses-permission android:name="android.permission.UHF_READ" />
<!-- Only if your app writes tags: -->
<uses-permission android:name="android.permission.UHF_WRITE" />
```

`UHF_READ` is `normal` (granted at install, no runtime prompt). `UHF_WRITE` is `dangerous` —
request it at runtime the same way you would `CAMERA` or `RECORD_AUDIO`. Region, RF power and
antenna configuration (`UHF_CONFIGURE`) are `signature|privileged` — not requestable by a normal
app at all; those calls fail with `UhfException(ERROR_BUSY, ...)` for anything but a
platform/OEM-signed caller. This SDK does not attempt to work around that: an app that needs
`UHF_CONFIGURE` is an OEM/provisioning tool, not a third-party consumer of this SDK.

Always call `UhfSdk.isSupported(context)` before `UhfSdk.open(context)` — it never throws, on any
device, including one from a platform build that predates this SRS. `open()` on an unsupported
device throws `UhfException(ERROR_DEVICE_LOST, ...)` rather than returning null, precisely so that
skipping the check fails loudly in one place instead of a `NullPointerException` three calls later.

### The antenna/throughput tradeoff

Measured on the reference hardware (UHF-SRS-001 §2.4): streaming reads/sec roughly **halves with
each additional antenna port enabled** (130.9 reads/sec at one port, 51.9 at four). If your
application only needs read range, not multi-port coverage, `setEnabledAntennas(ANTENNA_1)`
before streaming is the single highest-leverage performance decision available — no other setting
comes close. `getReaderInfo()`'s antenna-mask field tells you what's currently enabled if you
didn't set it explicitly.

### The RF power ceiling

`setRfPower()` refuses anything above the platform's safe ceiling (default 20/30) unless you pass
`allowAboveSafeCeiling = true` explicitly. This exists because the reference hardware **browns out
at 30/30** on its stock power supply — every command still answers normally, but inventory returns
nothing, which reads as "the reader is broken" rather than "the power supply is undersized." Do not
reach for the override flag to fix an inventory problem; if inventory suddenly returns nothing
after a power change, that is the first thing to suspect, not a software bug.

### Offline development (SDK-09)

`UhfSdk.openFake()` returns a `UhfSdkSession` backed by an in-memory simulated reader — no device,
no `@SystemApi` access, buildable and testable from a clean checkout. It enforces the same
caller-visible contracts the platform does (an all-zero kill password is refused, a permanent lock
needs explicit confirmation, an invalid word range is rejected) specifically so that code passing
against the fake has already cleared the checks it will meet on real hardware. See
`FakeUhfSdkSessionTest` for the full set of behaviors it guarantees.

## Compatibility matrix (SDK-07)

| SDK version | Requires platform API level | Notes |
|---|---|---|
| 1.0.x | UHF-SRS-001 Phase 1+ applied, SDK 34 (AOSP 14) | Session lifecycle, identity, region, power, antennas, polled/buffered inventory, streaming (§4.1-4.9). |
| 1.1.x (planned) | UHF-SRS-001 Phase 3 applied | Adds tag memory access and security (§4.10-4.11) — `readTagMemory`/`writeTagMemory`/`killTag`/`lockTag`/etc. |
| — | UHF-SRS-001 Phase 4/5 not yet implemented | Read protection, EAS, vendor extensions, and GPIO/peripherals have no SDK surface yet because the platform itself doesn't either. |

The SDK is versioned independently of the platform build (SDK-07): a platform patch that only
fixes a wire-protocol bug (see UHF-SRS-001's own Confirm-status command codes) does not require an
SDK release, because this module adds no protocol logic of its own (SDK-03) — everything here is a
1:1 delegation to `UhfManager`/`UhfSession`.

## What's verified vs. what isn't

Unlike most of this patch series (which could only be checked by reading, since no Android build
toolchain was available), **this module's Java and Kotlin source was actually compiled and, for
the fake reader, actually run**, using tools found in the surrounding AOSP tree rather than assumed
unavailable:

- `prebuilts/sdk/34/system/android.jar` — the real system-API stub jar — plus the platform's own
  `android.hardware.uhf.*` sources compiled directly against it (this is what caught a real,
  otherwise-silent bug: `IUhfService.aidl` needs a companion `<ClassName>.aidl` declaration file
  for every custom Parcelable it references, which the platform commit had been missing since
  Phase 1 — found by actually running `prebuilts/build-tools/linux-x86/bin/aidl`, not by reading
  the file).
- Every class in this module (`UhfSdk`, `UhfSdkSession`, `RealUhfSdkSession`,
  `FakeUhfSdkSession`) compiles cleanly against that real platform output.
- `UhfSdkKt.kt` (the coroutine/Flow adapters) was compiled with a real Kotlin 1.8.10 compiler
  (`external/kotlinc/`) against a real `kotlinx-coroutines-core` jar. The decision to name the
  suspend function `inventorySuspend()` rather than `inventory()` (colliding with the interface's
  existing blocking member) was verified empirically, not assumed: a same-named suspend extension
  compiles but is reported "shadowed by a member" and is unreachable through ordinary call syntax.
- `FakeUhfSdkSessionTest`'s 12 tests were **compiled and actually executed** against JUnit 4, and
  all pass — this includes exercising the fake's real background streaming (a genuine
  `ScheduledExecutorService`, not a mock).
- The sample app (`sdk/sample/`) compiles cleanly against the same real platform output.

What is still unverified: no Gradle/AGP build of either module was performed (none was available
in this environment), so packaging into an AAR, resource merging, and manifest merging are
untested; no device or emulator run of the sample app happened; and the platform-generated stub
jar SDK-02 asks for (built from `frameworks/base/core/api/current.txt`/`system-current.txt` via
metalava) does not exist yet — this module's own verification used the real compiled platform
classes directly instead, which proves the SDK's source is correct against the real API shape
without yet having that specific packaged artifact.
