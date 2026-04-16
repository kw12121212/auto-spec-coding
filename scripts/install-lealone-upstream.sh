#!/usr/bin/env bash

set -euo pipefail

LEALONE_COMMIT="${LEALONE_UPSTREAM_COMMIT:-53a3f65f581373c5609d3d1837c300177657555b}"
LEALONE_DIR="${LEALONE_SRC_DIR:-/tmp/lealone-latest}"
LEALONE_SKIP_FETCH="${LEALONE_SKIP_FETCH:-false}"

if [ ! -e "$LEALONE_DIR/.git" ]; then
  git clone https://github.com/lealone/Lealone.git "$LEALONE_DIR"
fi

if [ "$LEALONE_SKIP_FETCH" != "true" ]; then
  git -C "$LEALONE_DIR" fetch --all --tags --prune
fi
git -C "$LEALONE_DIR" checkout "$LEALONE_COMMIT"

mvnd -DskipTests -pl lealone-common,lealone-net,lealone-orm,lealone-http,lealone-db -am install -f "$LEALONE_DIR/pom.xml"
