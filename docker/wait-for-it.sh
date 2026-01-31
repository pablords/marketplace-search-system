#!/usr/bin/env bash
# wait-for-it.sh: aguarda um host:porta ficar disponível

host="$1"
shift
port="$1"
shift
timeout="${WAITFORIT_TIMEOUT:-60}"

for i in $(seq 1 $timeout); do
  nc -z "$host" "$port" && exit 0
  sleep 1
done

echo "Timeout esperando $host:$port"
exit 1