#!/bin/bash

seed1='138.91.123.106'
seed2='23.100.52.181'
seed3='13.91.127.35'
faucet='40.85.210.33'

function usage() {
	echo ' '
	echo 'USAGE: ./reset_testnet.sh <directory>'
	echo '    where directory is a path to a directory that contains the following files:'
	echo '    1) avmtestnet build for seed #1'
	echo '    2) avmtestnet build for seed #2'
	echo '    3) avmtestnet build for seed #3'
	echo '    4) avmtestnet release build (used by the faucet)'
}

if [ $# -ne 1 ]
then
	usage
	exit 1
fi

# First verify that all 4 builds exist in the specified directory and store their paths.
if [ ! -d "$1" ]
then
	echo "ERROR: The provided input was not a path to a directory: $1"
	usage
	exit 1
fi

# Grab the builds (note we 'echo' here so that the * expands and we capture the expanded name
# and not the name containing *)
seed1_build="$(echo "$1"/avmtestnet-seed1-*.tar.bz2)"
seed2_build="$(echo "$1"/avmtestnet-seed2-*.tar.bz2)"
seed3_build="$(echo "$1"/avmtestnet-seed3-*.tar.bz2)"
faucet_build="$(echo "$1"/avmtestnet-2019-*.tar.bz2)"


# Check that we actually have the builds in our hands.
if [ ! -e "$seed1_build" ]
then
	echo 'ERROR: The provided directory did not contain the expected build for seed #1'
	usage
	exit 1
fi

if [ ! -f "$seed2_build" ]
then
	echo 'ERROR: The provided directory did not contain the expected build for seed #2'
	usage
	exit 1
fi
if [ ! -f "$seed3_build" ]
then
	echo 'ERROR: The provided directory did not contain the expected build for seed #3'
	usage
	exit 1
fi
if [ ! -f "$faucet_build" ]
then
	echo 'ERROR: The provided directory did not contain the expected release build (used by the faucet)'
	usage
	exit 1
fi

# Shutdown all of the nodes.
echo ' '
echo 'Shutting down the 3 seed nodes and the faucet...'
ssh aion@"$seed1" "./shutdown_node.sh"
ssh aion@"$seed2" "./shutdown_node.sh"
ssh aion@"$seed3" "./shutdown_node.sh"
ssh aion@"$faucet" "./shutdown_node.sh"
echo '>> All seed nodes and the faucet have been shut down.'

# Update the node with the new build.
echo ' '
echo 'Updating the seed nodes and the faucet with the new build...'
scp "$seed1_build" aion@"$seed1":/home/aion/
scp "$seed2_build" aion@"$seed2":/home/aion/
scp "$seed3_build" aion@"$seed3":/home/aion/
scp "$faucet_build" aion@"$faucet":/home/aion/
echo '>> All seed nodes and the faucet have been updated with the new build.'

# Start all of the nodes back up.
echo ' '
echo 'Starting up the 3 seed nodes and the faucet...'
ssh aion@"$seed1" "./start_node.sh"
ssh aion@"$seed2" "./start_node.sh"
ssh aion@"$seed3" "./start_node.sh"
ssh aion@"$faucet" "./start_node.sh"
echo '>> All seed nodes and the faucet have been started with the new build.'

echo ' '
echo '######################## FIN ########################'
echo ' '
