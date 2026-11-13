# Changelog

Each release is on the [Releases](https://github.com/top11r/mhi2-aug22-carplay/releases)
page, with a ready-made SD card archive. The history of the project this build is based
on is in [chefranov's changelog](https://github.com/chefranov/mhi2-au37x-carplay/blob/main/CHANGELOG.md).

## k3346-v1.0.2 — 2026-11-14

- **CarPlay map in the cluster: smoother with Apple Maps.** The cluster renderer
  now catches up as soon as a frame is waiting instead of pacing every frame to a
  fixed 30 ms slot, so bursts during zoom, turns and route start no longer pile
  up into one-second jumps. It also no longer has the hardware decoder drop frames
  it considers late by their arrival time.
- **Route guidance: the car's own route no longer stays on the cluster** once CarPlay
  route guidance starts. The car's navigation can hold a route of its own (one it
  resumes after a restart, or picks up when the phone is already navigating at
  connect); the installer now turns on chefranov's switch that cancels it
  (`/mnt/app/rgd_cancel_native`). It is cancelled when the CarPlay route starts, not
  before.
- **Route guidance: the cockpit's own maneuver data is updated again.** Every update
  of the current maneuver to the cockpit's own tile failed with an error in all
  K3346 builds; it now goes through as in chefranov's original, without the
  cockpit's straight arrow showing while there is no maneuver to draw.

## k3346-v1.0.1 — 2026-11-13

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
