# pdc_hook

CarPlay stays on screen when the side parking-sensor popup (OPS, popup `2100008`)
opens beside it on the K3346 HMI (MU1438). Stock hides CarPlay twice over: the
small stage drops the CarPlay screen (`smallStageType 1`) and `HMIDeactivated`
hands the screen to the head unit, and parking message 108 switches to the Main
Wizard and takes the screen. Reverse gear, the full-screen camera, an active
parking assistant, HOME and everything without CarPlay stay stock.

Ported from [luka-dev/mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi)
at `91e301df` (2026-11-11), where it was built for MHI2Q (MU1316); see its
`docs/cluster-and-rgi/PDC_SMALL_STAGE.md` for the investigation. GPL-3.0-or-later,
see [LICENSE](LICENSE). Installed only with `PDC=1` and only on K3346.

## Build

```sh
src/pdc_hook/build.sh <stock lsd.jar>
```

The stock HMI classes are not in this repository: convert your own unit's
`lsd.jxe` to a jar and pass its path. Needs a Java 8 JDK (`JAVAC`, `JAR`, `JAVA`
override the Zulu 8 default). The build compiles for the head unit's J9 (Java 1.4
class files), runs `test/PdcHostTest` on the stock classes, then runs it again with
the stock classes first, where each bug it guards against must show (`--stock`),
and writes `bin/pdc_hook.jar`. A failure leaves `bin/` alone.

Check the jar against the stock HMI before it goes on a unit (a class that doesn't
link stops the HMI from starting): `LinkAudit` and `LoadVerify` from the K3346
source tree must report 0 unresolved, 0 broken stock references, 0 failed.

## What changed against luka's version

| File | Base | Change for MU1438 |
|---|---|---|
| `com/mhi2/pdc/PdcSmallStageGuard`, `OpsAudioDrawerPolicy` | luka's | package; `CarPlaySession` instead of his `CarPlayApp` |
| `com/mhi2/pdc/CarPlaySession` | new | CarPlay = active TerminalMode device; releases the guard when it changes |
| `ParkingSystemControllerComponentEvo`, `AudioDrawerContextImpl` | luka's (stock class byte-identical on both) | imports |
| `ParkingPartialPopupHandler` | luka's | MU1438's own mutex instead of the shared parking mutex |
| `PartialPopupManagerEvoHigh` | luka's | MU1438 superclass `PartialPopupManager`; status line 62 only, not the entertainment drawer (his r4) |
| `HighPriorityResourceTracker` | MU1438 stock | luka's 108 rule on MU1438's `rvcLock`; registers `CarPlaySession` |
| `ExternalEventsListener` | MU1438 stock | luka's SMALL_STAGE / AP 1002 rule on MU1438's `Debouncer`: the parking state is taken when a 1002 arrives |

Not ported, as the K3346 car shows a clean bottom edge beside the popup without them
(2026-11-20): luka's `StatusBarStubController` (entertainment drawer) and the
background image `2199999.png` without the black band under the popup frame. The
guard still looks for that image and logs once that it is missing.

## Log

`/tmp/pdc_hook.log` on the unit (RAM, up to 256 KB). Warnings only, unless
`/mnt/app/.mhi2_trace` exists (the same switch as chefranov's hooks; read at HMI
start). With it, each parking event: `parking intent ... standaloneOPS=true`,
`OPS guard: screen 3200000 smallStageType 1 -> 6`, `SMALL_STAGE pure OPS: keep CarPlay
SCREEN owner`, `OPS 108: keep CarPlay SCREEN`, and on close `OPS guard: restored
screen 3200000 smallStageType 1`.
