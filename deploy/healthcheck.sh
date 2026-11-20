#!/usr/bin/env bash
set -euo pipefail

port=$1
path=$2
expected=$3
exec 3<>"/dev/tcp/127.0.0.1/$port"
printf 'GET %s HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n' "$path" >&3
IFS= read -r status <&3
[[ $status == "HTTP/1.1 200 "* || $status == "HTTP/1.0 200 "* ]]
while IFS= read -r line || [[ -n $line ]]; do
  if [[ $line == *"$expected"* ]]; then
    printf '%s\n' "$line"
    exit 0
  fi
done <&3
exit 1
