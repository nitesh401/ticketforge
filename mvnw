#!/bin/sh
# Minimal Maven launcher: uses `mvn` from PATH if present, otherwise downloads Apache Maven once into .mvn/.
# To use the official wrapper instead, run: mvn -N wrapper:wrapper
set -e
BASE_DIR=$(cd "$(dirname "$0")" && pwd)
if command -v mvn >/dev/null 2>&1; then exec mvn "$@"; fi
MAVEN_VERSION=3.9.9
MAVEN_HOME="$BASE_DIR/.mvn/maven-$MAVEN_VERSION"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$BASE_DIR/.mvn"
  URL="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"
  TMP=$(mktemp)
  if command -v curl >/dev/null 2>&1; then curl -fsSL "$URL" -o "$TMP"; else wget -q "$URL" -O "$TMP"; fi
  tar -xzf "$TMP" -C "$BASE_DIR/.mvn"
  mv "$BASE_DIR/.mvn/apache-maven-$MAVEN_VERSION" "$MAVEN_HOME"
  rm -f "$TMP"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"
