#!/usr/bin/env sh
# Rebuilds the bundled SDK copy in sdk-repo/ from a checkout of tether-xmpp.
#   scripts/update-sdk.sh [path-to-tether-xmpp]   (default: ../tether-xmpp)
# Only the JVM artifacts are built, so no Kotlin/Native toolchain is needed.
set -eu
DEMO="$(cd "$(dirname "$0")/.." && pwd)"
SDK="$(cd "${1:-$DEMO/../tether-xmpp}" && pwd)"
MODULES="tether-xml tether-debug tether-crypto tether-sasl tether-transport tether-core tether-im"
TASKS=""
for m in $MODULES; do
  TASKS="$TASKS :$m:publishJvmPublicationToMavenLocal :$m:publishKotlinMultiplatformPublicationToMavenLocal"
done
rm -rf "$DEMO/sdk-repo"
mkdir -p "$DEMO/sdk-repo"
(cd "$SDK" && ./gradlew -q -Ptether.android=false "-Dmaven.repo.local=$DEMO/sdk-repo" $TASKS)
# Keep only what a build reads; the local-repository metadata files are machine specific.
find "$DEMO/sdk-repo" -name 'maven-metadata-local.xml' -delete
COMMIT="$(git -C "$SDK" rev-parse HEAD 2>/dev/null || echo unknown)"
printf 'tether-xmpp %s\n' "$COMMIT" > "$DEMO/sdk-repo/SDK_VERSION"
echo "sdk-repo updated from $SDK at $COMMIT"
