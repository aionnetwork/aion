#!/bin/bash

if [ ! -z $coinbase_password ]; then
    yes "$coinbase_password" | JAVA_OPTS="" ./aion.sh -a create
    coinbase_address=$(JAVA_OPTS="" ./aion.sh -a list | head -n 1)
    miner_address=$coinbase_address python3 override-config.py
fi
