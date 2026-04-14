#!/usr/bin/env bash

set -euo pipefail

LEALONE_COMMIT="${LEALONE_UPSTREAM_COMMIT:-16259183819d97b42210df9be6763f5a387fe79e}"
LEALONE_DIR="${LEALONE_SRC_DIR:-/tmp/lealone-latest}"
LEALONE_SKIP_FETCH="${LEALONE_SKIP_FETCH:-false}"

if [ ! -e "$LEALONE_DIR/.git" ]; then
  git clone https://github.com/lealone/Lealone.git "$LEALONE_DIR"
fi

if [ "$LEALONE_SKIP_FETCH" != "true" ]; then
  git -C "$LEALONE_DIR" fetch --all --tags --prune
fi
git -C "$LEALONE_DIR" checkout "$LEALONE_COMMIT"

mvn -DskipTests -pl lealone-common,lealone-net,lealone-orm,lealone-http,lealone-db -am install -f "$LEALONE_DIR/pom.xml"
