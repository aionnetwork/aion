#!/bin/bash

PACK_PATH='pack'

cd $PACK_PATH
BIN_ARCHIVE=$(ls aion-*tar*)
tar xvjf $BIN_ARCHIVE
VERSION=$(./aion/aion.sh --version)

docker build -t centrys/aion-core:latest ../. --build-arg kernel_path="$PACK_PATH/aion/"
docker tag centrys/aion-core:latest centrys/aion-core:$VERSION

rm -rf aion/

cd ..


