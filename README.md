# UFIDREADER — UHF RFID platform for AOSP 14 / RK3576

Patch series implementing **UHF-SRS-001**: a platform-level UHF RFID reader subsystem for the
`rockchip/rk3576/rk3576_u` AOSP 14 build — a vendor HAL, a system service, a public `UhfManager`
Java API, and (in progress) a developer SDK.

This repository holds **only the patches** — the diffs and new files this project adds on top of
the AOSP 14 / RK3576 vendor SDK tree. It does not, and will not, include that underlying tree: the
vendor-delivered Rockchip BSP source is licensed separately and is not redistributed here. Every
patch is a `git format-patch` series generated against an unmodified baseline of the affected AOSP
project, and applies cleanly with `git am` against that project at the commit noted in each
patch's own header.

## What's implemented

| Phase | Scope | Status |
|---|---|---|
| **1 — Vertical slice** | Session lifecycle, reader identity, region, RF power, antennas, polled/buffered inventory, streaming (SRS §4.1-4.9) | Done, including buffered inventory (§4.9). Wire-protocol codec and session-arbitration policy are unit-tested (100 host-side JUnit tests, no hardware required). The AIDL/Binder/JNI-turned-AIDL-HAL layers now **build cleanly end-to-end** — a full `lunch rk3576_u-userdebug && ./build.sh -K -A -p` against the real RK3576 AOSP 14 tree succeeds, producing a flashable `rockdev/Image-rk3576_u/`. No reader hardware was available to exercise it beyond that. |
| **2 — HAL split** | Vendor AIDL HAL (`rockchip.uhf.aidl`), native default implementation, sepolicy, VINTF | Done. The Phase 1 JNI shortcut is removed per the SRS's own exit criterion. Builds cleanly (see above) — this phase's HAL is what most of the later patches (0002-0010 in `hardware_rockchip_uhf/`) exist to fix, once a real build actually exercised it. |
| **3 — Tag operations** | Tag memory read/write, EPC rewrite, block write/erase, kill, lock, passwords (SRS §4.10-4.11) | Done for memory access and security. Read protection, EAS, and vendor extensions (§4.12-4.14) are **not implemented** — their wire payloads are unspecified beyond a command byte in the vendor manual, and guessing them wholesale seemed worse than leaving them open. |
| **4 — SDK** | `com.rockchip.uhf:uhf-sdk` facade, Kotlin coroutine/Flow adapters, in-memory fake reader, sample app (SRS §7) | Done for the source layer. Unlike the phases above, this one was actually compiled (and, for the fake reader, actually run) with real tooling found in the build tree: `aidl`, the real system `android.jar`, a real Kotlin 1.8.10 compiler, and JUnit 4 — 12/12 tests pass, including genuine background streaming. No Gradle/AGP build, AAR packaging, metalava stub jar (SDK-02), or device run — see `patches/sdk`'s commit message and `uhf-sdk/README.md` in the patch for the full breakdown. |
| **5 — Peripherals & hardening** | GPIO/relay/buzzer/Wiegand, buffered auto-drain, soak testing | Not started. |
| **Boot branding** | ID TECH "Identity and Security" boot splash (`patches/kernel_logo`) | Done — replaces both the U-Boot and kernel splash bitmaps, baked into `resource.img` by a real kernel build. Not board-tested (no device attached in the build environment). |

See `docs/UHF-SRS-001.html` for the full requirements specification, including which command codes
are confirmed against hardware versus inferred and pending confirmation.

## It actually builds now

Every patch in `frameworks_base/` past 0005 and every patch in `hardware_rockchip_uhf/` past 0001
exists because a real, full AOSP build (`lunch rk3576_u-userdebug && ./build.sh -K -A -p`) was
run against this tree and something in it was wrong — found by reading the actual failing
compiler/tool output, not by inspection. In order:

- 7 missing AIDL parcelable declaration files (`aidl` refuses to compile a Parcelable reference
  without one).
- Two Metalava API Lint violations (`Ms` suffix instead of `Millis`, and a `MissingGetterMatchingBuilder`
  case needing the same `@SuppressLint` pattern already used elsewhere in this tree).
- `aidl_interface`'s `versions_with_info` declared without ever running the real freeze step, then
  this tree's release-branch policy requiring an explicit `owner:` tag instead.
- A wrong Soong-generated Java module name (`rockchip.uhf.aidl-V1-java`, not `-java` — an unfrozen
  `stability: "vintf"` interface is still built as version "1").
- `byte[][]`, then `List<byte[]>` — both illegal in structured AIDL — fixed with a one-field
  `UhfRawTagRecord` parcelable wrapper, which itself then needed `@VintfStability`.
- A missing checked-in AIDL "current" API dump (the AIDL analogue of `frameworks/base/api/*.txt`).
- Missing `getInterfaceVersion()`/`getInterfaceHash()` overrides on the AIDL callback `Stub`.
- A C++ namespace collision silently shadowing the wire-protocol functions with an empty
  AIDL-generated namespace of the same name.
- A `-Werror` unused-variable error on a genuinely dead constant.
- A real sepolicy conflict: this project's own `/dev/ttyUSB[0-9]*` label collided with AOSP's
  own platform-wide `usb_serial_device` rule for the identical pattern.

The result: a full `rockdev/Image-rk3576_u/` flashable image set builds successfully against this
tree today, with the UHF platform compiled in and the new boot logo baked into `resource.img`.

## Layout

```
patches/
  frameworks_base/            13 patches — framework API, system service, session arbiter, protocol codec, real-build fixes
  hardware_rockchip_uhf/      10 patches — the vendor AIDL HAL (new project), plus every real-build fix on top of it
  device_rockchip_rk3576/     3 patches  — board wiring, ueventd, sepolicy, HAL packaging, sepolicy conflict fix
  device_rockchip_common/     1 patch    — the one shared-file change, a single precedented import line
  sdk/                        1 patch    — the uhf-sdk module (facade, fake reader, Kotlin adapters) + sample app
  kernel_logo/                1 patch    — ID TECH boot splash (U-Boot + kernel logo bitmaps)
sdk/                          Plain source (not a patch) — same uhf-sdk/sample as patches/sdk, for browsing/reuse
                              without an AOSP checkout
apps/UhfTestApp/              Plain source (not a patch) — the standalone Soong platform test app (see below)
dist/uhf-sdk-1.0.0.aar        A real, compiled AAR built from sdk/ (see "The AAR" below)
docs/
  UHF-SRS-001.html            The software requirements spec this patch series implements
  api/                        Generated Javadoc HTML for the SDK's public Java API
```

## sdk/, apps/, and the AAR — plain source, not patches

`patches/sdk/` and `patches/hardware_rockchip_uhf/` remain the way to apply this work onto a real
AOSP checkout. `sdk/`, `apps/UhfTestApp/`, and `dist/uhf-sdk-1.0.0.aar` at the repo root are a
different, complementary thing: the actual source and a built artifact, for anyone who wants to
browse or reuse them without syncing an AOSP tree at all.

- **`sdk/`** — an exact copy of `uhf-sdk/` and `sample/` from `patches/sdk`'s commit. Same code,
  just not a diff. `uhf-sdk/build.gradle.kts` is a real Android Studio/Gradle library module
  layout; open this directory in Android Studio the normal way once you have a real
  `compileOnlyApi` stub jar for the platform's `@SystemApi` surface (see the AAR note below for
  why this repo can't give you one built the usual way).
- **`apps/UhfTestApp/`** — the platform smoke-test app, as a plain Soong (`Android.bp`) module,
  not Gradle. It calls `android.hardware.uhf.UhfManager`/`UhfSession` directly rather than through
  the SDK facade. This one was actually built and installed on real hardware in this project (see
  its own commit message in `hardware_rockchip_uhf`'s git history) — it's here so you can read
  working, on-device-confirmed example code without needing to build it yourself.

### The AAR

`dist/uhf-sdk-1.0.0.aar` is a real compiled artifact, not a placeholder — but it was **not**
built by Gradle/AGP (still unavailable in every environment this project has been developed in).
Instead:

1. `sdk/uhf-sdk`'s four Java classes were compiled with the JDK's own `javac`, on the classpath of
   `frameworks/base`'s actual compiled `framework.jar` (`turbine-combined`, the same classpath
   form Soong itself uses to compile `services.core` against this exact API) — the real
   `android.hardware.uhf.*` bytecode from a build that succeeded end-to-end, not a stub.
2. `UhfSdkKt.kt` (the coroutine/Flow adapters) was compiled with a real Kotlin 1.8.10 compiler
   against that same `framework.jar` plus the real, tree-built `kotlinx-coroutines-core-jvm.jar`.
3. The resulting `.class` files were packed into `classes.jar` alongside a minimal generated
   `AndroidManifest.xml` (`package="com.rockchip.uhf.sdk"`, no components — a library manifest,
   matching what AGP's namespace-based manifest generation would have produced for a
   resource-less library) and `consumer-rules.pro`, then zipped into the standard AAR layout by
   hand.

What that means in practice: the bytecode inside this AAR is real and was compiled against the
real platform API, but the file wasn't put together by Gradle's `bundleReleaseAar` task, so it's
missing whatever AGP-specific metadata (proguard consumer rule merging behavior, lint vectors,
etc.) that task adds beyond a bare zip. For a library with no resources and no native code — which
this is — that gap is close to cosmetic, but it's worth knowing before treating this AAR as
identical to one `./gradlew :uhf-sdk:assembleRelease` would have produced.

### API docs

`docs/api/` is real generated Javadoc (`javadoc`, not hand-written HTML) covering the SDK's public
Java surface — `UhfSdk`, `UhfSdkSession`, `FakeUhfSdkSession`. `RealUhfSdkSession` doesn't appear:
it's package-private by design (SDK-03 — consumers only ever see it through the `UhfSdkSession`
interface returned by `UhfSdk.open()`), so `javadoc` correctly excludes it, which is itself a
small confirmation that the public API surface is what it's supposed to be. The Kotlin extension
functions (`inventorySuspend()`, `tagFlow()`) aren't in this Javadoc — Dokka would be the right
tool for those and isn't available here either — but they're documented with KDoc comments
in-source (`sdk/uhf-sdk/src/main/kotlin/.../UhfSdkKt.kt`) and in `uhf-sdk/README.md`'s integration
guide.

## Applying the patches

Each directory under `patches/` targets a different AOSP project (in the `repo`-manifest sense).
Against a synced AOSP 14 tree with the RK3576 SDK:

```bash
cd frameworks/base   && git am /path/to/UFIDREADER/patches/frameworks_base/*.patch
cd device/rockchip/rk3576  && git am /path/to/UFIDREADER/patches/device_rockchip_rk3576/*.patch
cd device/rockchip/common && git am /path/to/UFIDREADER/patches/device_rockchip_common/*.patch
```

`hardware/rockchip/uhf/` doesn't exist yet in a stock tree — apply its patch into a fresh empty
directory at that path (`mkdir -p hardware/rockchip/uhf && cd $_ && git init && git am
/path/to/UFIDREADER/patches/hardware_rockchip_uhf/*.patch`), then wire it into your device the way
`device/rockchip/rk3576/rk3576_u`'s patch already demonstrates
(`$(call inherit-product, hardware/rockchip/uhf/uhf_hal.mk)`).

Apply in the order listed above — `frameworks/base`'s 5 patches are sequential (each depends on
the previous), and the `device/rockchip/rk3576` and `hardware/rockchip/uhf` patches assume the
`frameworks/base` API surface already exists.

`sdk/` is also pre-existing AOSP content in a stock tree (SDK tooling, sample apps, etc.) — apply
its patch the same way as `frameworks/base`:

```bash
cd sdk && git am /path/to/UFIDREADER/patches/sdk/*.patch
```

It depends on `frameworks/base`'s `android.hardware.uhf` API surface existing, so apply it last.

`kernel_logo/` targets `kernel-6.1/` (also pre-existing content in a stock tree) and only touches
two files, `logo.bmp` and `logo_kernel.bmp`:

```bash
cd kernel-6.1 && git am /path/to/UFIDREADER/patches/kernel_logo/*.patch
```

Independent of everything else in this series — apply it whenever, in any order.

## What's verified vs. what isn't

Every patch's commit message states plainly what was and wasn't tested, following the same
Verified / Documented / Confirm convention the SRS itself uses for wire protocol command codes:

- **Verified**: the wire-protocol codec (CRC16, frame build/parse/resync, multi-frame assembly)
  and the session-arbitration policy (exclusivity, the antenna-check interlock, the RF power
  ceiling, region persistence, the streaming/buffered/tag-op mutual-exclusion rules) — both are
  plain Java with no Android framework dependency, compiled and tested standalone against JUnit 4
  on a host JDK. 100 tests, all passing as of the last patch in this series.
- **Actually built end-to-end**: a full `lunch rk3576_u-userdebug && ./build.sh -K -A -p` against
  the real RK3576 AOSP 14 tree this patch series was developed on succeeds and produces a
  flashable `rockdev/Image-rk3576_u/` — the AIDL surface, `UhfService`, the HAL's C++
  implementation, and sepolicy all compile and package correctly. See "It actually builds now"
  above for the real bugs that build run found and this series fixes. No reader hardware was
  available to exercise any of it beyond a successful build/boot-image-package.
- **Compiled and, in part, executed**: the SDK module (`patches/sdk`) — real `aidl`, the real
  system `android.jar`, a real Kotlin 1.8.10 compiler, and JUnit 4 were used to compile the whole
  module against the real compiled platform classes and to run its 12-test suite (all pass). This
  is a stronger bar than "written, not verified" above but still short of a full Gradle/AGP build
  or a device run — see `uhf-sdk/README.md` inside the patch.
- **Explicitly inferred, flagged inline**: a handful of wire payload layouts (tag memory/security
  command encodings, the optional inventory parameter triple) where the vendor protocol manual
  was not available. Each is isolated to one clearly-commented method so that a real hardware
  capture only requires changing that one place.
