# Changelog

Each release is on the [Releases](https://github.com/top11r/mhi2-aug22-carplay/releases)
page, with a ready-made SD card archive. The history of the project this build is based
on is in [chefranov's changelog](https://github.com/chefranov/mhi2-au37x-carplay/blob/main/CHANGELOG.md).

## Unreleased

- **Route guidance: lane guidance clears when the next maneuver has none.** After a
  maneuver with lanes, the maneuver tile kept showing its lanes until the next
  maneuver that had lanes of its own; the tile now drops them as soon as CarPlay
  stops sending lane guidance, together with the cluster's own lane panel.

## k3346-v1.0.0 — 2026-11-12

- **First AUG22 K3346 build**, based on chefranov's
  [v1.1.11](https://github.com/chefranov/mhi2-au37x-carplay/releases/tag/v1.1.11):
  CarPlay map in the Virtual Cockpit, route guidance, cover art and touchpad → D-pad,
  ported to `MHI2_ER_AUG22_K3346` (MU1438).
- **The installer accepts `MHI2_*_AUG22_K3346` only** and stops, changing nothing, on
  any other firmware.
- **HMI jars rebuilt** against the K3346 HMI; **`libcarplay_hook.so` patched** for the
  K3346 CarPlay library (cluster stream socket, session key offset, `TCP_NODELAY` for
  smooth zoom, roller zoom steps 262 ms apart).
- **No compiled shaders**: the K3346 GPU driver compiles the renderers' GLSL at run
  time; shader files an earlier install left are removed.
- **No phone tab and no parking popup**: not available on this HMI.
- **`altscreen_sup.sh`** no longer remounts `/mnt/app` writable.
