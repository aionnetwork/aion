#!/bin/bash

#TODO: remove miner setup after changing consensus
echo "Setting up miner..."
/bin/bash -c ./create-coinbase.sh

echo "Overriding config parameters..."
python3 ./override-config.py

if [ ! -z "${difficulty}" ]; then
    echo "Overriding difficulty with ${difficulty}"
    sed 's/"difficulty": "0x4000"/"difficulty": "'${difficulty}'"/g' -i ./config/genesis.json
fi

echo "Starting kernel..."
/bin/bash -c ./aion.sh
