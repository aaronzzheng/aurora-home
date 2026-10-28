#!/usr/bin/env bash
# Build Aurora Home with Gradle (Kotlin + Java), then copy the APK to the top folder.
#   ./build.sh            AuroraHome.apk, for the TV
#   ./build.sh --preview  AuroraHome-preview.apk, debuggable, for the emulator only
#   ./build.sh --test     run the unit tests (tests/)
#
# Keep your keystore backed up and out of any shared repo: the TV only accepts
# updates signed with the same key, so a lost key means uninstalling Aurora Home
# (and losing its settings) before a newer build can be installed.
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f keystore.properties ]]; then
    echo "note: no keystore.properties, so this build uses Android's debug key. A TV that already has" >&2
    echo "      Aurora Home signed with your own key won't accept it as an update (see README)." >&2
fi

case "${1:-}" in
    --test)
        ./gradlew -q testDebugUnitTest --console=plain
        echo "tests passed"
        ;;
    --preview)
        ./gradlew -q assembleDebug --console=plain
        cp build/outputs/apk/debug/AuroraHome-debug.apk AuroraHome-preview.apk
        ls -lh AuroraHome-preview.apk | awk '{print "built AuroraHome-preview.apk (" $5 ")"}'
        ;;
    *)
        ./gradlew -q assembleRelease --console=plain
        cp build/outputs/apk/release/AuroraHome-release.apk AuroraHome.apk
        ls -lh AuroraHome.apk | awk '{print "built AuroraHome.apk (" $5 ")"}'
        ;;
esac
