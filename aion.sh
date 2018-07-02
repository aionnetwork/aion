#!/bin/bash

cd "$(dirname $(realpath $0))"

KERVER=$(uname -r | grep -o "^4\.")

if [ "$KERVER" != "4." ]; then
  echo "Warning! The linux kernel version must great or equal than 4."
fi

HW=$(uname -m)

if [ "$HW" != "x86_64" ]; then
  echo "Warning! Aion blockchain platform must be running on the 64 bits architecture"
fi

DIST=$(lsb_release -i | grep -o "Ubuntu")

if [ "$DIST" != "Ubuntu" ]; then
  echo "Warning! Aion blockchain is fully compatible with the Ubuntu distribution. Your current system is not Ubuntu distribution. It may has some issues."
fi

MAJVER=$(lsb_release -r | grep -o "[0-9][0-9]" | sed -n 1p)
if [ "$MAJVER" -lt "16" ]; then
  echo "Warning! Aion blockchain is fully compatible with the Ubuntu version 16.04. Your current system is older than Ubuntu 16.04. It may has some issues."
fi

ARG=$@

#if [ "$ARG" == "--close" ]; then
#    PID=$(<./tmp/aion.pid)
#    kill -2 $PID
#    rm -r ./tmp
#    exit 0
#fi

# add execute permission to rt
chmod +x ./rt/bin/*

# this will append to the right of $JAVA_OPTS if it's already present on the server
# the rightmost value of the heapsize parameters will be picked up by the jvm so we should be safe

JAVA_OPTS=$JAVA_OPTS

if [ ! -z "$CORE_XMX" ]; then
    JAVA_OPTS="$JAVA_OPTS -Xmx$CORE_XMX"
fi

if [ ! -z "$CORE_XMS" ]; then
    JAVA_OPTS="$JAVA_OPTS -Xms$CORE_XMS"
else
    JAVA_OPTS="$JAVA_OPTS -Xms4g"
fi


env EVMJIT="-cache=1" ./rt/bin/java $JAVA_OPTS \
        -cp "./lib/*:./lib/libminiupnp/*:./mod/*" org.aion.Aion "$@"
