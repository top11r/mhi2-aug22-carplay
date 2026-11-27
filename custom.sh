#!/bin/sh
#
# custom.sh - M.I.B. entry point.
#
# Copy this file to /mod/custom.sh on the M.I.B. SD card and the rest of this
# repository to /mod/carplay/, then on the unit:
#
#     Advanced Settings -> Run Custom Script
#
# M.I.B. V3.6 and later name it "Run individual script" and run
# /mod/command.sh instead; that file only calls this one.
#
# It finds the payload on the card and runs install.sh. To uninstall instead,
# create an empty file named UNINSTALL next to install.sh on the card
# (/mod/carplay/UNINSTALL) and run the custom script again.
#
# Settings (sport layout, arrival time first, map for wireless adapters, ...)
# are changed in the car: MENU > Audi smartphone interface > CarPlay settings.
# To install everything except route guidance, put an empty file named NO_RGI
# on the card beside install.sh; to leave out only the CarPlay map in the
# cluster, one named NO_ALTSCREEN.  An empty NO_DPAD file leaves out the
# touchpad as arrow keys, an empty NO_COVERART file the album art in the
# virtual cockpit, an empty NO_PDC file the parking popup beside CarPlay.
# If the installer stops on jars it does not
# know and you are sure they are harmless, an empty IGNORE_CONFLICTS file lets
# it go on.  There is no way to pass an environment variable through the
# M.I.B. menu, and the files combine.

set -u

echo "=== MHI2 AU37x CarPlay patches - M.I.B. launcher ==="

# The card can be mounted under different names depending on the reader, so
# look for our payload rather than assuming a path.
# `dirname` is missing too, so $0's directory is derived with ${0%/*} - which
# leaves $0 alone when there is no slash in it, hence the explicit "." case.
case $0 in
    */*) SELF_DIR=${0%/*} ;;
    *)   SELF_DIR=. ;;
esac

# Globs, not `find`: this unit has no find at all (/bin holds 40 utilities and
# that is not one of them), so a find-based search would fail silently and the
# script would report "payload not found" for the wrong reason. The shell
# expands these itself, and an unmatched pattern simply stays literal - the
# -f tests below reject it.
PAYLOAD=""
for d in \
    "$SELF_DIR/carplay" \
    /fs/*/mod/carplay \
    /mnt/*/mod/carplay \
    /net/*/fs/*/mod/carplay
do
    if [ -f "$d/install.sh" ] && [ -f "$d/bin/libcarplay_hook.so" ]; then
        PAYLOAD=$d
        break
    fi
done

if [ -z "$PAYLOAD" ]; then
    echo "!! payload not found."
    echo "!! Expected /mod/carplay/install.sh and /mod/carplay/bin/ on the card,"
    echo "!! with custom.sh itself at /mod/custom.sh."
    echo "!! Looked under: $SELF_DIR/carplay, /fs/*/mod, /mnt/*/mod, /net/*/fs/*/mod"
    exit 1
fi

echo "payload: $PAYLOAD"

if [ -f "$PAYLOAD/UNINSTALL" ]; then
    echo "UNINSTALL marker present - removing the patches"
    SRC_DIR="$PAYLOAD" sh "$PAYLOAD/uninstall.sh"
    RC=$?
    RUN_LOG=/tmp/carplay_uninstall.log
else
    R=1
    A=1
    if [ -f "$PAYLOAD/NO_RGI" ]; then
        echo "NO_RGI marker present - installing without route guidance"
        R=0
    fi
    if [ -f "$PAYLOAD/NO_ALTSCREEN" ]; then
        echo "NO_ALTSCREEN marker present - installing without the CarPlay map in the cluster"
        A=0
    fi
    D=1
    if [ -f "$PAYLOAD/NO_DPAD" ]; then
        echo "NO_DPAD marker present - installing without the touchpad as arrow keys"
        D=0
    fi
    C=1
    if [ -f "$PAYLOAD/NO_COVERART" ]; then
        echo "NO_COVERART marker present - installing without the album art in the virtual cockpit"
        C=0
    fi
    P=1
    if [ -f "$PAYLOAD/NO_PDC" ]; then
        echo "NO_PDC marker present - installing without the parking popup beside CarPlay"
        P=0
    fi
    I=0
    if [ -f "$PAYLOAD/IGNORE_CONFLICTS" ]; then
        echo "IGNORE_CONFLICTS marker present - unknown jars will not stop the install"
        I=1
    fi
    SRC_DIR="$PAYLOAD" RGI=$R ALTSCREEN=$A DPAD=$D COVERART=$C PDC=$P IGNORE_CONFLICTS=$I \
        sh "$PAYLOAD/install.sh"
    RC=$?
    RUN_LOG=/tmp/carplay_install.log
fi

# The log is in RAM and gone with the ignition, and this screen cannot be
# copied out of - so leave a copy on the card, which is what an issue report
# needs.  The card is mounted read-only like everything else here; a card
# pulled from a computer without ejecting stays read-only whatever is asked.
CARD=${PAYLOAD%/mod/*}
LOG_COPY=""
if [ -f "$RUN_LOG" ]; then
    [ "$CARD" = "$PAYLOAD" ] || mount -uw "$CARD" 2>/dev/null
    if cp "$RUN_LOG" "$PAYLOAD/last_run.log" 2>/dev/null; then
        sync
        LOG_COPY="$PAYLOAD/last_run.log"
    fi
fi

echo ""
if [ "$RC" = "0" ]; then
    echo "Read the log above, then reboot the unit."
else
    echo "!! NOT DONE - the script stopped (exit $RC).  Read the log above."
    echo "!! There is nothing to reboot for: what it says was not changed, was not."
fi
if [ -n "$LOG_COPY" ]; then
    echo "A copy of the log is on the card: mod/carplay/last_run.log"
else
    echo "The log could not be copied to the card (read-only?) - photograph this screen."
fi
