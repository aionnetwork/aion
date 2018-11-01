#!/bin/bash

cd "$(dirname $(realpath $0))"

KERVER=$(uname -r | grep -o "^4\.")

if [ "$KERVER" != "4." ]; then
  echo "Warning! The linux kernel must be greater than or equal to version 4."
fi

HW=$(uname -m)

if [ "$HW" != "x86_64" ]; then
  echo "Warning! Aion blockchain platform must be running on 64 bits architecture"
fi

DIST=$(lsb_release -i | grep -o "Ubuntu")

if [ "$DIST" != "Ubuntu" ]; then
  echo "Warning! Aion blockchain is fully compatible with Ubuntu distribution. Your current system is not Ubuntu distribution. It may have some issues."
fi

MAJVER=$(lsb_release -r | grep -o "[0-9][0-9]" | sed -n 1p)
if [ "$MAJVER" -lt "16" ]; then
  echo "Warning! Aion blockchain is fully compatible with Ubuntu version 16.04. Your current system is older than Ubuntu 16.04. It may have some issues."
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

# prepare jvm params
# default to minimum 4gb heap if Xms not set.
JAVA_OPTS="$JAVA_OPTS"
if [[ ! ${JAVA_OPTS} = *"-Xms"* ]]; then
  JAVA_OPTS+=" -Xms4g"
fi

# to suppress illegal reflective access warning out of xnio and protobuf
# (we depend on xnio transitively via undertow-core)
JAVA_OPTS+=" --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED"

JAVA_CMD=java
if [ -d "./rt" ]; then
		JAVA_CMD="./rt/bin/java"
elif [ -d "./pack/rt" ]; then
		JAVA_CMD="./pack/rt/bin/java"
elif [ -d "$JAVA_HOME" ]; then
		JAVA_CMD="$JAVA_HOME/bin/java"
fi

env EVMJIT="-cache=1" $JAVA_CMD ${JAVA_OPTS} \
	-cp "./lib/*:./lib/libminiupnp/*:./jars/*" org.aion.ColdWallet
