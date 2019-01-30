#!/bin/bash

tar -xvjf aion*.tar.bz2
cd aion/config/avmtestnet
cp config-e66d"$1".xml config.xml
rm config-e66d*.xml
cd ../..
nohup ./aion.sh -n avmtestnet &>/dev/null &
disown
