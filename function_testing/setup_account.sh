#!/bin/bash

if [ $# -ne 1 ]
then
	echo "MUST SPECIFY PRIVATE KEY"
	exit 1
fi

privateKey="$1"
echo "Importing key \"$privateKey\""
# (WOW - 256M is not enough to do this!)
JAVA_OPTS="-Xms512m -Xmx512m" ./aion.sh --network avmtestnet --account import "$privateKey"
status=$?

if [ $status -ne 0 ]
then
	echo "FAILED TO IMPORT ACCOUNT"
fi

echo "Now, start your kernel and run test.sh"

