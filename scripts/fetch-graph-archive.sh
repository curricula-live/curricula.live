#!/usr/bin/env bash
set -euo pipefail
commit=6da9d1066eabe36194593be422a1253f30062885
digest=c36057ff0fefe52150eb09f44213baec3443e36f3a38f185e13db93300086d70
destination=${1:-target/graph-archive.json}
directory=$(dirname "$destination")
mkdir -p "$directory"
scratch=$(mktemp "$directory/.graph-archive.XXXXXX")
trap 'rm -f "$scratch"' EXIT
curl --fail --silent --show-error --location --retry 3 --proto '=https' --proto-redir '=https' \
  "https://raw.githubusercontent.com/curricula-live/graph/$commit/public/graph.json" -o "$scratch"
printf '%s  %s\n' "$digest" "$scratch" | sha256sum --check
chmod 0644 "$scratch"
mv "$scratch" "$destination"
