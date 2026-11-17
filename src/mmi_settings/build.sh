#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 top11r
#
# Builds mmi_settings.jar: the CarPlay settings screens in the Audi smartphone
# interface. Compiled against the stock HMI classes, which are not part of this
# repo: convert your own unit's lsd.jxe to a jar and pass its path.
#
#   build.sh <stock lsd.jar>
#
# Output: ../../bin/mmi_settings.jar (the installer copies it to
# /mnt/app/eso/hmi/lsd/jars/ on the unit); classes are built in build/.
# Runs test/HostCheck against the stock classes first; a failure leaves bin/ alone.
set -e
cd "$(dirname "$0")"
STOCK=${1:?usage: build.sh <stock lsd.jar>}
JAVAC=${JAVAC:-/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/bin/javac}
JAR=${JAR:-/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/bin/jar}
rm -rf build
mkdir -p build/classes build/gen
python3 tools/gen_texts.py i18n build/gen
find java build/gen -name '*.java' > build/sources
"$JAVAC" -nowarn -Xlint:-options -source 1.4 -target 1.4 -encoding UTF-8 \
    -cp "$STOCK" -d build/classes @build/sources
# Host check first: our state machine wiring, bank and settings table on the stock classes.
JAVA=${JAVA:-${JAVAC%c}}
mkdir -p build/test
"$JAVAC" -nowarn -XDignore.symbol.file -source 1.8 -target 1.8 -cp "build/classes:$STOCK" -d build/test test/HostCheck.java
"$JAVA" -cp "build/test:build/classes:$STOCK" HostCheck
"$JAR" cf ../../bin/mmi_settings.jar -C build/classes .
echo "bin/mmi_settings.jar"
