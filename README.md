# MHI2 AUG22 CarPlay patches (K3346)

A private build for Audi MHI2 head units on **`MHI2_ER_AUG22_K3346`** (MU1438), based on
**[chefranov's CarPlay patches](https://github.com/chefranov/mhi2-au37x-carplay)** (v1.1.13).
His installer, jars and native parts do all the work; this build ports them to the
AUG22 K3346 firmware and keeps only what was tested there. The modifications are made
**with chefranov's permission**.

> **Private build.** Use at your own risk; everything is reversible.

What it adds on AUG22 K3346:

- **CarPlay map in the Virtual Cockpit**: the phone's navigation map (Apple Maps,
  Google Maps, Waze) in the cluster in place of the stock one, about 30 fps, decoded by
  the head unit's video hardware. Zoom with the left steering-wheel roller.
- **Route guidance**: CarPlay turn-by-turn maneuvers in the cluster's maneuver tile:
  the arrow, the distance, the street and lane guidance, drawn live.
- **Maneuver arrow** fills blue as the turn gets closer. For the plain arrow:
  `touch /mnt/app/rgd_no_arrow_fill` and reboot.
- **Cover art**: CarPlay album artwork on the cluster's now-playing widget.
- **Touchpad → D-pad**: the MMI touchpad navigates CarPlay menus.

## What differs from chefranov's release

- **Firmware**: the installer accepts `MHI2_*_AUG22_K3346` and `MHI2_*_AUG22_K3347` only
  (from `CachedTrain.txt` or the EEPROM) and stops, changing nothing, on anything else.
- **HMI jars** (`dpad_hook.jar`, `coverart_hook.jar`, `rgd_hook.jar`): rebuilt against the
  K3346 HMI.
- **`libcarplay_hook.so`**: patched for the K3346 CarPlay library: the cluster stream's
  socket family, the session key's offset, `TCP_NODELAY` on the link to the cluster-map
  renderer (smooth zoom), and roller zoom steps 262 ms apart (no skipped steps).
- **`altscreen_render`** (the cluster-map renderer): decodes a frame as soon as one is
  waiting instead of only when five are buffered (it can catch up after bursts), and
  passes the hardware decoder timestamp 0, so it no longer drops frames it considers late.
- **No compiled shaders**: the K3346 GPU driver (17.37) compiles the renderers' built-in
  GLSL with its own run-time compiler, so no shader files are shipped or installed, and
  ones an earlier install left are removed.
- **No phone tab**: the CarPlay call tap does not exist on this HMI (`rgd_no_tel` is set).
- **The car's own route is cancelled** when CarPlay route guidance starts (`rgd_cancel_native`
  is set): otherwise a route the car's navigation holds (resumed after a restart, or picked
  up when the phone was already navigating at connect) keeps the cluster.
- **`rgd_hook.jar`** also fixes the update of the cockpit's own maneuver tile, which failed
  in every K3346 build.
- **No parking popup**: not ported to this HMI.
- **Cluster-map intro**: shows "CarPlay Navigation" and "Loading…".

## Compatibility

Tested on `MHI2_ER_AUG22_K3346`, MU1438, with the Virtual Cockpit and the HARMAN iAP2
stack. Remove any other CarPlay or HMI mod first; the installer stops if it finds one.

- **`MHI2_US_AUG22_K3347`** (NAR) runs the same HMI release with byte-identical CarPlay
  natives; only 12 unrelated HMI classes differ. A user confirmed it on the car (cover
  art, route guidance, the CarPlay map in the cluster).

- **CarPlay map in the cluster** works by cable and through a wireless adapter that
  passes the cluster stream:
  - **Ottocast Mini Cube 3**: confirmed.
  - **Carlinkit Mini Ultra 3**: not proven; CarPlay runs, but the cluster stream was not
    opened. A firmware update of the adapter might be needed.

  Zooming with the roller works by cable only.
- **No coding or adaptations** are needed on the unit.
- **Route guidance** works with Apple Maps and Google Maps. Waze sends no turn-by-turn
  data over CarPlay; its map still shows in the cluster.

## Install

The card has a single folder, `mod`: `mod/custom.sh`, `mod/command.sh`,
`mod/carplay/install.sh`, `mod/carplay/uninstall.sh`, `mod/carplay/bin/` and
`mod/carplay/VERSION`.

**From an SD card (M.I.B.):**

1. Copy the `mod` folder to the root of a FAT32 card. Don't drag it over an existing
   `mod` folder on a Mac: Finder replaces the folder, it doesn't merge them.
2. Insert the card, then in M.I.B. run `Advanced Settings → Run Custom Script`
   (`Advanced Settings → Run individual script` on M.I.B. V3.6 and later).
3. Read the log on screen (it is also in `/tmp/carplay_install.log`), then **restart
   the MMI**. If the installer stopped, the unit is unchanged; the log says why and a
   copy is on the card as `mod/carplay/last_run.log`.

**Over a network cable:** from the folder that contains `mod`:

```sh
ssh root@172.16.250.248 'mount -uw /mnt/app; mkdir -p /mnt/app/root/carplay'
scp -O -r mod/carplay/* root@172.16.250.248:/mnt/app/root/carplay/
ssh root@172.16.250.248 'sh /mnt/app/root/carplay/install.sh'
```

Running the installer again upgrades in place.

| Option | Cable | SD card (empty file in `mod/carplay/`) |
|---|---|---|
| leave out route guidance and the cluster map | `RGI=0 sh install.sh` | `NO_RGI` |
| leave out only the cluster map | `ALTSCREEN=0 sh install.sh` | `NO_ALTSCREEN` |
| offer the cluster map to a wireless adapter that does not ask for it | `WIRELESS_MAP=1 sh install.sh` | `WIRELESS_MAP` |
| start the line under the maneuver on the arrival time | `ETA_DEFAULT=1 sh install.sh` | `ETA_DEFAULT` |
| the cluster map without route guidance (no maneuver tile) | `MAP_ONLY=1 sh install.sh` | `MAP_ONLY` |
| force the sport cluster layout | `SPORT=1 sh install.sh` | `SPORT` |
| let unknown jars through | `IGNORE_CONFLICTS=1 sh install.sh` | `IGNORE_CONFLICTS` |

`SPORT`, `ETA_DEFAULT` and `MAP_ONLY` stay set across later installs. To switch one back
off: `SPORT=0`, `ETA_DEFAULT=0` or `MAP_ONLY=0` by cable, or an empty `NO_SPORT`,
`NO_ETA_DEFAULT` or `NO_MAP_ONLY` file on the card.

## Using it

- **Cluster map**: comes up with the phone, after a short intro. A **long press of the
  left steering-wheel roller** swaps between the CarPlay map and the stock one; the
  choice is kept.
- **Route guidance**: start a route on the phone. While the car is off the road network
  (parked, before joining the route) the maneuver tile stays closed; it opens with the
  first turn. A **short press of the left roller** swaps the line under the maneuver
  between the next street and the arrival time. Off without uninstalling:
  `touch /mnt/app/rgd_disable` and restart the MMI; delete the file to turn it back on.
- **Sport layout** is detected by itself; `touch /mnt/app/rgd_sport` forces it.

Known behaviour of the stock USB stack: after unplugging, the head unit sometimes misses
the removal, and CarPlay stays on screen until its 30-second watchdog ends the session;
plugging in again only works once the old USB session is gone.

## Uninstall

```sh
ssh root@172.16.250.248 'sh /mnt/app/root/carplay/uninstall.sh'
```

or from the card: an empty `UNINSTALL` file in `mod/carplay/`, the same M.I.B. menu item,
then restart the MMI.

## Credits

- **[chefranov](https://github.com/chefranov)**: the project this build is based on, its
  installer, hooks, jars, renderers and route guidance. Support his work:
  [Ko-fi](https://ko-fi.com/chefranov) or [monobank](https://send.monobank.ua/jar/HdecLxjBN).
- His credits: [luka-dev/mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi)
  (the HMI patch pattern, cover art, route-guidance BAP work, the maneuver renderer and
  lane guidance), [Mich795](https://github.com/Mich795) (cover-art fixes) and
  [aberbic](https://github.com/aberbic/Audi-MHI2-Virtual-Cockpit-Altscreen-CarPlay-AndroidAuto)
  (the hardware-decoder layer setup for the cluster map).
