#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
javac -encoding UTF-8 -d build \
  src/helden/plugin/*.java \
  src/helden/plugin/datenxmlplugin/*.java \
  src/modernbogen/*.java
cp resources/heldenstyle.css build/
jar cfm ModernBogenPlugin.jar MANIFEST.MF -C build .
echo "Built: $(pwd)/ModernBogenPlugin.jar"
ls -la ModernBogenPlugin.jar
