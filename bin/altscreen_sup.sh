#!/bin/sh
# K3346 stage 3b: keeps one altscreen_render --listen alive while the cluster
# map is switched on (/mnt/app/altscreen_inject). Started in the background by
# the mm-ipod shim, i.e. with the CarPlay session. Private copy of chefranov's
# altscreen_sup.sh without its `mount -uw /mnt/app`: /mnt/app stays read-only,
# so the steering-wheel on/off choice is not saved across sessions.
BIN=/mnt/app/eso/hmi/lib/altscreen_render
LOG=/dev/null
LOCK=/dev/shmem/altscreen_sup.pid
FLAG=/dev/shmem/altscreen_live
MARKER=/mnt/app/altscreen_inject
# libdisplayinit (/eso/lib), the GL run-time compiler libcgdrv
# (/mnt/app/root/lib-target, used because no shader blobs are installed) and
# the hardware decoder's libraries.
LD_LIBRARY_PATH=/eso/lib:/mnt/app/eso/hmi/lib:/lib:/usr/lib:$LD_LIBRARY_PATH:/mnt/app/root/lib-target:/mnt/app/armle/lib:/mnt/app/armle/usr/lib
export LD_LIBRARY_PATH

[ -f "$MARKER" ] || exit 0
[ -x "$BIN" ] || exit 0

if [ -f "$LOCK" ]; then
    read old < "$LOCK"
    if [ -n "$old" ] && kill -0 "$old" 2>/dev/null; then exit 0; fi
fi
echo $$ > "$LOCK"

while [ -f "$MARKER" ]; do
    "$BIN" --listen >> "$LOG" 2>&1
    rc=$?
    # A dead renderer must not leave the cluster on a context whose map
    # window is gone.
    rm -f "$FLAG"
    # 0 is a planned restart (one hardware decoder per process); a crash waits.
    [ "$rc" -eq 0 ] || sleep 2
done
rm -f "$LOCK"
