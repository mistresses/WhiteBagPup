#!/usr/bin/env bash
# Builds WhiteBagPup.jar: the Puppy library + its dependencies (compiled from source) + the notifier.
# Expects sibling checkouts: ../Puppy (or an older ../RealmShark folder), ../dl/{pcap,gson,fb,jna}
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
DL="$ROOT/dl"
OUT="$HERE/build"
SRC="$OUT/src"
CLS="$OUT/classes"
rm -rf "$OUT"; mkdir -p "$SRC" "$CLS"

# Puppy library (based on RealmShark)
LIB="$ROOT/Puppy"; [ -d "$LIB" ] || LIB="$ROOT/RealmShark"
cp -r "$LIB/src/main/java/." "$SRC/"
rm -rf "$SRC/realmshark"   # replaced by puppy/version in src/main/java

# Dependencies
cp -r "$DL/fb/java/src/main/java/." "$SRC/"
cp -r "$DL/gson/gson/src/main/java/." "$SRC/"; rm -f "$SRC/module-info.java"
sed 's/\${project.version}/2.9.1/' "$DL/gson/gson/src/main/java-templates/com/google/gson/internal/GsonBuildConfig.java" \
  > "$SRC/com/google/gson/internal/GsonBuildConfig.java"
for m in common spi jdk7; do cp -r "$DL/pcap/$m/src/main/java/." "$SRC/"; done
rm -f "$SRC/module-info.java"
# pcap's optional slf4j/log4j bridges aren't needed; use its no-op logger.
rm -f "$SRC"/pcap/common/logging/{Slf4jLogger,Slf4jLoggerFactory,Log4j2Logger,Log4j2LoggerFactory}.java
python3 - "$SRC/pcap/common/logging/LoggerFactory.java" <<'EOF'
import re, sys
p = sys.argv[1]; s = open(p).read()
s = re.sub(r"if \(Slf4jLoggerFactory\.hasSlf4j\(\)\) \{.*?\} else \{\s*(loggerFactory = NoLoggerFactory\.getInstance\(\);)\s*\}",
           r"\1", s, flags=re.S)
open(p, "w").write(s)
EOF

# Notifier
cp -r "$HERE/src/main/java/." "$SRC/"

find "$SRC" -name "*.java" > "$OUT/sources.txt"
javac --release 8 -nowarn -encoding UTF-8 -cp "$DL/jna/dist/jna.jar" -d "$CLS" @"$OUT/sources.txt" 2>&1 | grep -v "^Note:\|warning: \[options\]\|^1 warning\|^[0-9]* warnings" || true
test -f "$CLS/whitebag/WhiteBagNotifier.class"
# the notifier's own resources (RealmStock name lists used until the live ones load)
cp -r "$HERE/src/main/resources/." "$CLS/"

# Resources: pcap service registration, JNA (with its native dispatch libraries), licences
mkdir -p "$CLS/META-INF/services"
cp "$DL/pcap/jdk7/src/main/resources/META-INF/services/pcap.spi.Service" "$CLS/META-INF/services/"
(cd "$CLS" && unzip -qo "$DL/jna/dist/jna.jar" -x 'META-INF/MANIFEST.MF' 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA')
mkdir -p "$CLS/META-INF/licenses"
cp "$LIB/LICENSE.md" "$CLS/META-INF/licenses/Puppy-LICENSE.md"
cp "$DL/pcap/LICENSE" "$CLS/META-INF/licenses/pcap-LICENSE"
cp "$DL/jna/LICENSE" "$CLS/META-INF/licenses/jna-LICENSE"
cp "$DL/gson/LICENSE" "$CLS/META-INF/licenses/gson-LICENSE"
cp "$DL/fb/LICENSE" "$CLS/META-INF/licenses/flatbuffers-LICENSE" 2>/dev/null || true
cp "$HERE/licenses/Nunito-OFL.txt" "$CLS/META-INF/licenses/Nunito-OFL.txt"

printf "Main-Class: whitebag.WhiteBagNotifier\n" > "$OUT/manifest.txt"
jar cfm "$OUT/WhiteBagPup.jar" "$OUT/manifest.txt" -C "$CLS" .
ls -la "$OUT/WhiteBagPup.jar"
