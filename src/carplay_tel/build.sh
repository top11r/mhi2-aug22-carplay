#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 top11r
#
# Builds libcarplay_tel.so (QNX 6.5, ARMv7) in luka-dev's qnx65-armv7-toolchain
# Docker image, after the host test. Output: ../../bin/libcarplay_tel.so, which
# install.sh copies to /mnt/app/eso/hmi/lib/ and the mm-ipod shim preloads.
#
#   build.sh            QNX_IMAGE=<image> overrides the default below
# The image is built once with qnx65-armv7-toolchain's "qnx-run.sh build 8.5".
# It is linux/amd64: run it with the platform named (its own qnx-run.sh check
# by name fails on Apple silicon with Docker 27).
set -e
cd "$(dirname "$0")"
qnx() { docker run --rm --platform=linux/amd64 -v "$PWD":/src "${QNX_IMAGE:-qnx65-armv7-toolchain:8.5}" "$@"; }
mkdir -p build

# Host tests: table patch, parsers and builders; the hook module and command
# glue with librgd_hook's exports stubbed (-rdynamic: Linux hosts).
cc -Wall -Wextra -DCARPLAY_TEL_HOST_TEST -I. carplay_tel.c test/tel_test.c -o build/tel_test
build/tel_test
cc -Wall -Wextra -DCARPLAY_TEL_NO_INIT -I. test/module_test.c -o build/module_test -lpthread $( [ "$(uname)" = Linux ] && echo -rdynamic )
build/module_test

# The unit: libc.so.3 only (dlopen, pthreads are in it); libgcc linked in.
qnx arm-unknown-nto-qnx6.5.0eabi-gcc -O2 -Wall -Wextra -fPIC -shared -static-libgcc \
    -o build/libcarplay_tel.so carplay_tel.c
needed=$(qnx arm-unknown-nto-qnx6.5.0eabi-readelf -d build/libcarplay_tel.so | grep NEEDED || true)
echo "$needed"
echo "$needed" | grep -v 'libc.so.3' | grep -q NEEDED && { echo "unexpected DT_NEEDED"; exit 1; }
cp build/libcarplay_tel.so ../../bin/libcarplay_tel.so
echo "bin/libcarplay_tel.so"
