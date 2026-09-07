#!/usr/bin/env bash
# JAVA_HOME must point to Java 25. Run from the repository root.
set -euo pipefail
mkdir -p logs/wall-selector-samples/run/config
if [[ ! -f logs/wall-selector-samples/run/config/ringworld.properties ]]; then
    cat > logs/wall-selector-samples/run/config/ringworld.properties <<'CONFIG'
widthBlocks=128
circumferenceBlocks=2048
wallHeightBlocks=160
testMode=false
pregenerateTerrainAtlas=false
requestOceanMonument=false
CONFIG
fi
if [[ ! -f logs/wall-selector-samples/run/options.txt ]]; then
    printf '%s\n' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'tutorialStep:none' 'fullscreen:false' 'gamma:0.5' > logs/wall-selector-samples/run/options.txt
fi
./gradlew :runAppearanceComparisonClient -x :prepareAppearanceComparisonRun \
    -I scripts/wall_samples/capture.init.gradle \
    -Pminecraft_version=26.2 -Ploom_version=1.17.20 \
    -Pfabric_api_version=0.158.0+26.2 -Pneoforge_version=26.2.0.69 \
    -Pmoddevgradle_version=2.0.144 --no-parallel --max-workers=1 --console=plain
