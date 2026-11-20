#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 top11r
#
# Builds pdc_hook.jar: CarPlay stays on screen beside the side parking-sensor
# popup. Compiled against the stock HMI classes, which are not part of this
# repo: convert your own unit's lsd.jxe (K3346, MU1438) to a jar and pass its path.
#
#   build.sh <stock lsd.jar>
#
# Output: ../../bin/pdc_hook.jar (install.sh copies it to /mnt/app/eso/hmi/lsd/jars/
# with PDC=1); classes are built in build/. Runs the host tests in test/ against
# the stock classes first; a failure leaves bin/ alone.
set -e
cd "$(dirname "$0")"
STOCK=${1:?usage: build.sh <stock lsd.jar>}
JAVAC=${JAVAC:-/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/bin/javac}
JAR=${JAR:-/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/bin/jar}
JAVA=${JAVA:-${JAVAC%c}}
rm -rf build
mkdir -p build/classes build/test
find java -name '*.java' > build/sources
"$JAVAC" -nowarn -Xlint:-options -source 1.4 -target 1.4 -encoding UTF-8 \
    -cp "$STOCK" -d build/classes @build/sources
# Host tests: our classes ahead of the stock ones, as on the unit.
if [ -f test/PdcHostTest.java ]; then
    # test/stubs stand in for the physical HMI widgets; they are not in the jar
    find test -name '*.java' > build/test-sources
    "$JAVAC" -nowarn -source 1.8 -target 1.8 -encoding UTF-8 -cp "build/classes:$STOCK" -d build/test @build/test-sources
    "$JAVA" -cp "build/test:build/classes:$STOCK" PdcHostTest
    # Negative control: the stock classes alone must show the bug the tests guard against.
    "$JAVA" -cp "build/test:$STOCK:build/classes" PdcHostTest --stock
fi
"$JAR" cf ../../bin/pdc_hook.jar -C build/classes .
echo "bin/pdc_hook.jar"
