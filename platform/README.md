# platform/ — the AOSP ROM track

This directory is the **"real OS" path**: how AMOS becomes a flashable build
that replaces stock Android, rather than just an installable launcher.

It is intentionally a thin overlay. **We do not fork all of AOSP.** We consume
AOSP via its repo manifest and overlay only the AMOS system apps and services on
top — exactly how CalyxOS, /e/OS, and LineageOS structure their trees.

## How an AOSP-derived build works (the short version)

1. `repo init` an AOSP manifest (a specific release tag) + a **local manifest**
   that adds AMOS projects and overrides the default launcher.
2. `repo sync` pulls AOSP + AMOS.
3. `lunch` a target (device or emulator), then `m` to build a system image.
4. Flash (or boot the emulator). AMOS replaces the home experience and ships the
   Agent Runtime as a system service.

`manifest/amos.xml` is a **sample local manifest fragment** showing how AMOS
projects are layered onto AOSP and how the stock launcher is removed. It is a
starting template, annotated, not a turnkey build — building a ROM requires a
Linux build host (~400 GB disk, lots of RAM) and a target device, which is
Phase 4 in [../docs/ROADMAP.md](../docs/ROADMAP.md).

## Play-app compatibility

Two supported routes (chosen at build time):
- **microG** — open-source reimplementation of Google Play services; "degoogled"
  with broad app compatibility. Default for a privacy-first AMOS.
- **Certified GMS** — official Google apps/services; requires Google
  certification (CTS/uncertified-device limits apply).

Either way, **Android apps run natively** because AMOS *is* Android underneath.

## Target devices

Start where AOSP support is strongest:
- **Cuttlefish / emulator** — for development; no hardware needed.
- **Google Pixel** — best AOSP + AVB (verified boot) support; the standard
  first target for alternative OSes.

Arbitrary phones ship closed vendor blobs and are far harder to support; that's
a later, per-device effort.

## What's NOT here yet

The actual device trees, kernel configs, and build scripts. Those land in
Phase 4. This directory currently establishes the **approach and the manifest
shape** so the architecture is concrete rather than hand-wavy.
