#!/usr/bin/env bash

# set -eox

# see spec: https://www.w3.org/TR/trace-context
# version-format   = trace-id "-" parent-id "-" trace-flags
# trace-id         = 32HEXDIGLC  ; 16 bytes array identifier. All zeroes forbidden
# parent-id        = 16HEXDIGLC  ; 8 bytes array identifier. All zeroes forbidden
# trace-flags      = 2HEXDIGLC   ; 8 bit flags. Currently, only one bit is used. See below for detail

VERSION="00" # fixed in spec at 00
TRACE_ID="$(cat /dev/urandom | tr -dc 'a-f0-9' | fold -w 32 | head -n 1)"
PARENT_ID="00$(cat /dev/urandom | tr -dc 'a-f0-9' | fold -w 14 | head -n 1)"
TRACE_FLAG="01"   # sampled
TRACE_PARENT="$VERSION-$TRACE_ID-$PARENT_ID-$TRACE_FLAG"
TRACE_STATE="mystate"

DEFAULT_URL="http://localhost:8080/ping"
URL="${1:-$DEFAULT_URL}"

echo "sending to host $URL"
echo "sending a trace-parent of: $TRACE_PARENT"

curl --location "${URL}" \
-H "traceparent: $TRACE_PARENT"

