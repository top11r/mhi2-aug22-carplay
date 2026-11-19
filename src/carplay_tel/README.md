# carplay_tel

`libcarplay_tel.so`: CarPlay call actions, recents and favourites for the Audi
MHI2 virtual cockpit (K3346 / MU1438). Preloaded into `mm-ipod` next to
`librgd_hook.so` by the shim `install.sh` writes. GPL-3.0-or-later, copyright
top11r, see [LICENSE](LICENSE).

What it does, at `mm-ipod` start:

1. Waits until `mm-ipod` has loaded the iAP2 driver (`dlsym(RTLD_DEFAULT,
   "iap2_ctrl_msg_table")` every 5 ms; its own `dlopen` only after 1 s, since
   QNX 6.5 ignores `RTLD_NOLOAD`) and replaces the driver's Telephony group
   (message group 0x41, 90 slots) by a 0x72-slot copy, only if the group is
   exactly the stock one:
   - send slots InitiateCall `0x415A`, AcceptCall `0x415B`, EndCall `0x415C`,
     HoldStatusUpdate `0x415F`, MuteStatusUpdate `0x4160` and StartListUpdates
     `0x4170`; the driver declares every send slot in the Identify;
   - receive slot ListUpdate `0x4171`; without it the driver rejects the
     iPhone's lists ("Invalid packet") and the link stalls.
2. Reads `/ramdisk/pps/carplay/tel` (`cmd::accept|end|hold|resume|mute|unmute|dial`,
   `num::<number>` for dial, `ts::<ms>`), which the phone tab in `rgd_hook.jar`
   writes, and sends the command with the hook's `harman_send_message()`:
   one u8 as parameter 0 (Action 0, or the hold / mute status), no CallUUID,
   so iOS takes the incoming or frontmost call; dial as InitiateCall (Type 0,
   the number, Service 1). A command older than 3 s is dropped.
3. Registers a module with `librgd_hook`'s hook framework: on the first
   incoming TelephonyUpdate `0x4158` of a link it asks for the lists once (from
   its own thread; `on_transport_send` for stock's `0x4154` is a second path,
   which the hook calls reliably only for messages sent through it), and it turns every ListUpdate into the sticky bus text
   (`EVT_TEL_LISTS`, `r.<i>.a|b|type|ts|occ`, `f.<i>.a|b`) that the jar shows
   on the cockpit as recents and favourites. A new identification starts with
   empty lists.

Log: `/dev/shmem/carplay_tel.log`. Message layouts from Luka's
[mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi)
(`IAP2_COMMUNICATIONS.md`, `tel_hook.c`, GPL-3.0).

## Build

```sh
src/carplay_tel/build.sh
```

Runs the host tests, then cross-compiles in
[luka-dev/qnx65-armv7-toolchain](https://github.com/luka-dev/qnx65-armv7-toolchain)
(build the image once with its `qnx-run.sh build 8.5`; `QNX_IMAGE` overrides
the image name) and writes `bin/libcarplay_tel.so`.

- `test/tel_test.c`: table patch, command parser, InitiateCall and
  StartListUpdates payloads, ListUpdate parsing with Luka's captured frames,
  bus text.
- `test/module_test.c`: the hook module's callbacks, the list request thread
  and the command glue, with `librgd_hook`'s exports stubbed.
- `test/pps_test.c` (on the unit or the emulator): the command path with a fake
  `harman_send_message` (`pps_test read`) and a writer that writes like the HMI
  jar (`pps_test write <cmd> [age ms] [number]`).
