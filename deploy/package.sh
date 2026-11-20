#!/usr/bin/env bash
set -euo pipefail

deploy_dir=$(cd -- "$(dirname -- "$0")" && pwd)
web_dir=$(dirname -- "$deploy_dir")
api_dir="$(dirname -- "$web_dir")/api.curricula.live"
: "${API_REVISION:?Pin the API commit}"
: "${WEB_REVISION:?Pin the web commit}"

scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

package() {
  local service=$1 checkout=$2 revision=$3
  if [[ ! $revision =~ ^[0-9a-f]{40}$ ]]; then
    echo "Expected a full Git commit for $service" >&2
    return 1
  fi
  git -C "$checkout" cat-file -e "$revision^{commit}"
  mkdir -p "$scratch/$service"
  git -C "$checkout" archive "$revision" | tar -x -C "$scratch/$service"
  (
    cd "$scratch/$service"
    "${CURRICULA_MAVEN:-./mvnw}" -B -ntp verify
  )

  local -a jars
  shopt -s nullglob
  jars=("$scratch/$service/target/curricula-$service-"*.jar)
  if (( ${#jars[@]} != 1 )); then
    echo "Expected one packaged Spring Boot JAR for $service" >&2
    return 1
  fi

  local context="$deploy_dir/.build/$service/$revision" output
  mkdir -p "$context"
  output=$(mktemp "$context/.jar.XXXXXX")
  install -m 0644 "${jars[0]}" "$output"
  install -m 0644 "$deploy_dir/healthcheck.sh" "$context/healthcheck.sh"
  touch "$context/.volume-root"
  mv -f "$output" "$context/app.jar"
}

package api "$api_dir" "$API_REVISION"
package web "$web_dir" "$WEB_REVISION"
