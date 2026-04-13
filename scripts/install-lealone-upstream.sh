#!/usr/bin/env bash

set -euo pipefail

LEALONE_COMMIT="${LEALONE_UPSTREAM_COMMIT:-990a56f458a5a48c719450243f35fea17f45fb78}"
LEALONE_DIR="${LEALONE_SRC_DIR:-/tmp/lealone-latest}"

if [ ! -d "$LEALONE_DIR/.git" ]; then
  git clone https://github.com/lealone/Lealone.git "$LEALONE_DIR"
fi

git -C "$LEALONE_DIR" fetch --all --tags --prune
git -C "$LEALONE_DIR" checkout "$LEALONE_COMMIT"

mvn -DskipTests -pl lealone-common,lealone-net,lealone-orm,lealone-http,lealone-db -am install -f "$LEALONE_DIR/pom.xml"
