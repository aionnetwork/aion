#!/bin/bash
echo
echo "Bootstrap the custom network..."
echo

# Step1. clear the database of the custom network, if user assigned the database to the other PATH, please modify the CUSTOM_DB_PATH
echo
echo "Clean custom database..."
echo
CUSTOM_DB_PATH="../../custom/database"
rm -rf "$CUSTOM_DB_PATH"

# Step2. launch the aion kernel
../../aion.sh -n custom &

# sleep 8 sec for waiting the kernel services start
sleep 8

PID=$(ps aux | grep -i 'org.aion.Aion -n custom' -m1 | awk -F ' ' '{print $2}')
echo
echo "The kernel PID is: $PID"
echo
# Step3. Execute the boostrap script.
./bootstrap.sh
echo
echo "Shutting down the kernel ..."
echo

# Final Step. Shutdown kernel
trap 'trap - SIGTERM && kill $PID' SIGINT SIGTERM EXIT
