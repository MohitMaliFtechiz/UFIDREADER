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
| **1 — Vertical slice** | Session lifecycle, reader identity, region, RF power, antennas, polled/buffered inventory, streaming (SRS §4.1-4.9) | Done, including buffered inventory (§4.9), completed after the arbiter-level plumbing for it sat unreachable through one prior patch. Wire-protocol codec and session-arbitration policy are unit-tested (100 host-side JUnit tests, no hardware required); the AIDL/Binder/JNI layers are written but unbuilt (no framework build attempted, no reader hardware available in the environment this was built in). |
| **2 — HAL split** | Vendor AIDL HAL (`rockchip.uhf.aidl`), native default implementation, sepolicy, VINTF | Done. The Phase 1 JNI shortcut is removed per the SRS's own exit criterion. Unbuilt/unverified for the same reasons as Phase 1. |
| **3 — Tag operations** | Tag memory read/write, EPC rewrite, block write/erase, kill, lock, passwords (SRS §4.10-4.11) | Done for memory access and security. Read protection, EAS, and vendor extensions (§4.12-4.14) are **not implemented** — their wire payloads are unspecified beyond a command byte in the vendor manual, and guessing them wholesale seemed worse than leaving them open. |
| **4 — SDK** | AAR, stub JAR, Kotlin adapters, sample app | Not started. |
| **5 — Peripherals & hardening** | GPIO/relay/buzzer/Wiegand, buffered auto-drain, soak testing | Not started. |

See `docs/UHF-SRS-001.html` for the full requirements specification, including which command codes
are confirmed against hardware versus inferred and pending confirmation.

## Layout

```
patches/
  frameworks_base/            5 patches — framework API, system service, session arbiter, protocol codec
  hardware_rockchip_uhf/      1 patch   — the vendor AIDL HAL (new project)
  device_rockchip_rk3576/     2 patches — board wiring, ueventd, sepolicy, HAL packaging
  device_rockchip_common/     1 patch   — the one shared-file change, a single precedented import line
docs/
  UHF-SRS-001.html            The software requirements spec this patch series implements
```

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

## What's verified vs. what isn't

Every patch's commit message states plainly what was and wasn't tested, following the same
Verified / Documented / Confirm convention the SRS itself uses for wire protocol command codes:

- **Verified**: the wire-protocol codec (CRC16, frame build/parse/resync, multi-frame assembly)
  and the session-arbitration policy (exclusivity, the antenna-check interlock, the RF power
  ceiling, region persistence, the streaming/buffered/tag-op mutual-exclusion rules) — both are
  plain Java with no Android framework dependency, compiled and tested standalone against JUnit 4
  on a host JDK. 100 tests, all passing as of the last patch in this series.
- **Written, not verified**: everything that needs a full AOSP framework build or native
  toolchain to compile (the AIDL surface, `UhfService`, the HAL's C++ implementation, sepolicy) —
  no such build was attempted against this tree, and no reader hardware was available to exercise
  any of it end-to-end regardless.
- **Explicitly inferred, flagged inline**: a handful of wire payload layouts (tag memory/security
  command encodings, the optional inventory parameter triple) where the vendor protocol manual
  was not available. Each is isolated to one clearly-commented method so that a real hardware
  capture only requires changing that one place.
