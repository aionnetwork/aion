#!/bin/bash

echo 'Removing any pre-existing testnet builds from a prior run'
rm avmtestnet-*.tar.bz2
echo 'Moving the necessary testnet scripts into the root directory'
mv AvmTestnet/* .
