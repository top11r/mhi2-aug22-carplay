# Changelog

Each release is on the [Releases](https://github.com/top11r/mhi2-aug22-carplay/releases)
page, with a ready-made SD card archive. The history of the project this build is based
on is in [chefranov's changelog](https://github.com/chefranov/mhi2-au37x-carplay/blob/main/CHANGELOG.md).

## k3346-v1.3.0 — 2026-11-20

The parking-sensor popup beside CarPlay; based on k3346-v1.2.0.

- **Parking sensors beside CarPlay** (opt-in: `PDC=1`, or an empty `PDC` file on the
  card; K3346 only). When the side sensors open their popup, CarPlay stays on screen
  beside it instead of the head unit taking over; the status line and the "Check
  surroundings!" drawer stay off CarPlay meanwhile. Reverse gear, the camera, an active
  parking assistant and HOME are unchanged. luka-dev's guard for MHI2Q, ported to the
  K3346 HMI; source and host tests in [`src/pdc_hook`](src/pdc_hook) (GPL-3.0-or-later).

## k3346-v1.2.0 — 2026-11-19

The cockpit phone tab for CarPlay; based on k3346-v1.1.0 (chefranov's v1.1.13, with his
fix for #79).

- **CarPlay calls on the virtual cockpit's phone tab.** An incoming call pops up in the
  cockpit; answer, decline, hang up, hold and resume, and mute from the cockpit, or answer
  and hang up with the steering-wheel Tel key. Missed calls show on the tab; the Tel key
  clears them. Signal and carrier follow the iPhone.
- **Recents and favourites from the iPhone** in the cockpit's call lists; select one to
  call it. Numbers are cleaned before dialling (chefranov's #29 fix).
- **After "Restart CarPlay"** calls still reach the tab.
- **CarPlay comes back after quick re-plugs**: chefranov's `librgd_hook.so` with his fix
  for chefranov/mhi2-au37x-carplay#79 (the hook sends from a thread of its own). The
  cockpit's call actions are instant instead of 2–6 s late.
- **Settings: *Phone tab in virtual cockpit*** (on by default): off gives the stock phone
  tab during CarPlay, from the next phone plug.
- New `libcarplay_tel.so`, preloaded into `mm-ipod` next to `librgd_hook.so`; its source
  and host tests are in [`src/carplay_tel`](src/carplay_tel) (GPL-3.0-or-later).

Tester checklist: an incoming call (popup, answer, decline), Tel key answer and hang-up,
hold and resume (the held call shows again), mute, a missed call (icon, Tel key clears
it), recents and favourites (call one of each), "Restart CarPlay" then a call, a quick
unplug and replug.

## k3346-v1.1.0 — 2026-11-17

The CarPlay settings menu; based on k3346-v1.0.4 (chefranov's v1.1.13).

- **CarPlay settings in the car.** MENU → Audi smartphone interface → *Switch to CarPlay
  settings*, with no phone connected. CarPlay in the virtual cockpit, directions, sport
  view, arrival time first, the filling maneuver arrow, ending the MMI's own route, the
  CarPlay map in the virtual cockpit and offering it to wireless adapters are switched
  there; the choice is kept across ignition and reinstalls. Settings that need an MMI
  restart say so.
- **Restart CarPlay** in the same menu: when CarPlay doesn't come back after a reconnect,
  it restarts the CarPlay services without restarting the MMI; then plug the iPhone in
  again. If CarPlay still doesn't start (chefranov/mhi2-au37x-carplay#79), restart the
  MMI.
- **The touchpad as arrow keys and the album art can be left out at install:** `DPAD=0` /
  `COVERART=0`, or an empty `NO_DPAD` / `NO_COVERART` file on the card.
- **Installer: no setting options any more.** `SPORT`, `ETA_DEFAULT`, `MAP_ONLY` and
  `WIRELESS_MAP` (and their files on the card) are gone. The first install of this
  version sets every setting to its default; set yours once in the menu.
- The menu's source is in [`src/mmi_settings`](src/mmi_settings) (GPL-3.0-or-later).

## k3346-v1.0.4 — 2026-11-16

Based on chefranov's v1.1.13 (was v1.1.11), with every K3346 change carried over.

- **Route guidance: the first maneuver shows straight away** when CarPlay connects to
  a route that is already running, after switching navigation apps and after a
  reroute. A route that resumes right after a short stop no longer ends by itself,
  and a passed turn's distance is no longer shown for the next turn.
- **Route guidance: roundabouts and ramps follow the car's traffic side** when the
  navigation app doesn't send one (right-hand-drive cars got right-hand traffic).
- **Maneuver arrow fills blue as the turn gets closer.** For the plain arrow:
  `touch /mnt/app/rgd_no_arrow_fill` and reboot. In the sport layout the progress
  bar starts below the cluster's line.
- **CarPlay no longer drops when the HMI closes the route guidance connection**, and
  the cluster keeps following CarPlay after an internal error instead of stopping
  until the next reboot. Waits around route start, route end and reconnecting no
  longer follow the head unit's clock, which jumps when the time is synchronised.
- **CarPlay map in the cluster: the stream ends cleanly with its session** and comes
  back on the next connection.
- **Smaller fixes:** stock navigation gets its own route state back after a CarPlay
  route in one more case; oversized cover art is refused; street names cut at the
  length limit no longer end in half a character; fewer drawing calls for lanes and
  the progress bar.
- **Installer:** nothing is written before every check has passed, and a stopped
  install says so and leaves its log on the card (`mod/carplay/last_run.log`); jars
  and the config are replaced in one step; the config backup follows firmware
  updates; a new stock `mm-ipod` after a firmware update is kept; the route guidance
  hook is loaded into `mm-ipod` only, not into the cluster renderers; `NO_SPORT`,
  `NO_ETA_DEFAULT` and `NO_MAP_ONLY` on the card switch those options back off.
- **CarPlay map in the cluster: the long press of the left roller works again** and
  its choice is kept. The cluster map's supervisor makes `/mnt/app` writable for the
  session again, as in chefranov's release; this build had left it read-only.

## k3346-v1.0.3 — 2026-11-15

- **Route guidance: the cockpit's navigation tab no longer keeps the car's own
  arrow.** After the car's navigation had run (a route, or one it predicted home),
  the arrow in the cockpit's navigation tab stayed fixed for the whole CarPlay
  route while distance and arrival time updated. The cockpit was told to draw that
  arrow from its compass data; it now takes it from the maneuvers CarPlay sends.
  `/mnt/app/rgd_rgtype` still overrides the mode.
- **Installer: `MHI2_US_AUG22_K3347` is accepted.** It runs the same HMI release with
  byte-identical CarPlay natives as K3346, and a user confirmed it on the car.

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
