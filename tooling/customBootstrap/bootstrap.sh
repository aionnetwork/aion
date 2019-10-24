#!/bin/bash

STAKER_ADDRESS="0xa02df9004be3c4a20aeb50c459212412b1d0a58da3e1ac70ba74dde6b4accf4b"
PRIVATE_KEY="0xcc76648ce8798bc18130bc9d637995e5c42a922ebeab78795fac58081b9cf9d4"
EXPECTED_DAPP_ADDRESS="0xa056337bb14e818f3f53e13ab0d93b6539aa570cba91ce65c716058241989be9"
JAR_PATH="stakerRegistry.jar"
TOOLS_JAR=Tools.jar
NODE_ADDRESS="127.0.0.1:8545"

function require_success()
{
	if [ $1 -ne 0 ]
	then
		echo "Failed"
		exit 1
	fi
}

function verify_state()
{
	address="$1"
	data="$2"
	expected="$3"

	payload={"jsonrpc":"2.0","method":"eth_call","params":[{"to":"$address","data":"$data"}],"id":1}
	response=`curl -s -X POST -H "Content-Type: application/json" --data "$payload" "$NODE_ADDRESS"`
	if [ "$expected" != "$response" ]
	then
		echo "Incorrect response from eth_call: \"$response\""
		exit 1
	fi
}

function wait_for_receipt()
{
	receipt="$1"
	result="1"
	while [ "1" == "$result" ]
	do
		echo " waiting..."
		sleep 1
		`./rpc.sh --check-receipt-status "$receipt" "$NODE_ADDRESS"`
		result=$?
		if [ "2" == "$result" ]
		then
			echo "Error"
			exit 1
		fi
	done
}

MIN_SELF_STAKE=1000000000000000000000
SIGNING_ADDRESS_COOLING_PERIOD="$((6 * 60 * 24 * 7))"
UNDELEGATE_LOCK_UP_PERIOD="$((6 * 60 * 24))"
TRANSFER_LOCK_UP_PERIOD="$((6 * 10))"
echo "Deploying the stakerRegistry.jar..."
callPayload="$(java -cp $TOOLS_JAR cli.ComposeCallPayload "deployStakerRegistry" "$MIN_SELF_STAKE" "$SIGNING_ADDRESS_COOLING_PERIOD" "$UNDELEGATE_LOCK_UP_PERIOD" "$TRANSFER_LOCK_UP_PERIOD")"
receipt=`./rpc.sh --deploy "$PRIVATE_KEY" "0" "$NODE_ADDRESS" "$JAR_PATH" "$callPayload"`
require_success $?

echo "Deployment returned receipt: \"$receipt\".  Waiting for deployment to complete..."
address=""
while [ "" == "$address" ]
do
	echo " waiting..."
	sleep 1
	address=`./rpc.sh --get-receipt-address "$receipt" "$NODE_ADDRESS"`
	require_success $?
done
echo "Deployed to address: \"$address\""
if [ "$EXPECTED_DAPP_ADDRESS" != "$address" ]
then
	echo "Address was incorrect:  Expected $EXPECTED_DAPP_ADDRESS"
	exit 1
fi

echo "Sending registration call..."
# registerStaker(Address identityAddress, Address signingAddress, Address coinbaseAddress)
callPayload="$(java -cp $TOOLS_JAR cli.ComposeCallPayload "registerStaker" "$STAKER_ADDRESS" "$STAKER_ADDRESS" "$STAKER_ADDRESS")"
receipt=`./rpc.sh --call "$PRIVATE_KEY" "1" "$address" "$callPayload" "100000000000000000000000" "$NODE_ADDRESS"`
echo "$receipt"
require_success $?

echo "Transaction returned receipt: \"$receipt\".  Waiting for transaction to complete..."
wait_for_receipt "$receipt"
echo "Transaction completed"

echo "Verifying that vote was registered and staker is active..."
# getTotalStake(Address staker)
callPayload="$(java -cp $TOOLS_JAR cli.ComposeCallPayload "getTotalStake" "$STAKER_ADDRESS")"
# This result in a BigInteger:  0x23 (byte), length (byte), value (big-endian length bytes)
verify_state "$address" "$callPayload" '{"result":"0x230a152d02c7e14af6800000","id":1,"jsonrpc":"2.0"}'

callPayload="$(java -cp $TOOLS_JAR cli.ComposeCallPayload "getEffectiveStake" "$STAKER_ADDRESS" "$STAKER_ADDRESS")"
# This result in the same BigInteger:  0x23 (byte), length (byte), value (big-endian length bytes)
verify_state "$address" "$callPayload" '{"result":"0x230a152d02c7e14af6800000","id":1,"jsonrpc":"2.0"}'

echo "BOOTSTRAP COMPLETE"
