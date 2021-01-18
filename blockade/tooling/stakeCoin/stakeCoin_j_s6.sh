#!/bin/bash
set -x
#STAKER_ADDRESS="0xa02df9004be3c4a20aeb50c459212412b1d0a58da3e1ac70ba74dde6b4accf4b"
#PRIVATE_KEY="0xcc76648ce8798bc18130bc9d637995e5c42a922ebeab78795fac58081b9cf9d4"
STAKER_ADDRESS="0xa05e1d82687e1e795ee5694c2ae09b98a26720e0ca15bdaa29af5332ad001f96"
   PRIVATE_KEY="0xa4eb51be0500a03e5dba68923b76d0bc6a78181658f45571aadc06ef3b9fccb4"

EXPECTED_DAPP_ADDRESS="0xa0733306c2ee0c60224b0e59efeae8eee558c0ca1b39e7e5a14a575124549416"
TOOLS_JAR=Tools.jar
NODE_ADDRESS="127.0.0.1:9001"

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

echo "Sending registration call..."
# registerStaker(Address identityAddress, Address signingAddress, Address coinbaseAddress)
callPayload="$(java -cp $TOOLS_JAR cli.ComposeCallPayload "registerStaker" "$STAKER_ADDRESS" "$STAKER_ADDRESS" "$STAKER_ADDRESS")"
receipt=`./rpc.sh --call "$PRIVATE_KEY" "0" "$EXPECTED_DAPP_ADDRESS" "$callPayload" "4650000000000000000000000" "$NODE_ADDRESS"`
echo "$receipt"
require_success $?

echo "Transaction returned receipt: \"$receipt\".  Waiting for transaction to complete..."
wait_for_receipt "$receipt"
echo "Transaction completed"

echo "Verifying that vote was registered and staker is active..."
# getTotalStake(Address staker)
callPayload="$(java -cp $TOOLS_JAR cli.ComposeCallPayload "getTotalStake" "$STAKER_ADDRESS")"
# This result in a BigInteger:  0x23 (byte), length (byte), value (big-endian length bytes)
verify_state "$EXPECTED_DAPP_ADDRESS" "$callPayload" '{"result":"0x230a152d02c7e14af6800000","id":1,"jsonrpc":"2.0"}'

callPayload="$(java -cp $TOOLS_JAR cli.ComposeCallPayload "getEffectiveStake" "$STAKER_ADDRESS" "$STAKER_ADDRESS")"
# This result in the same BigInteger:  0x23 (byte), length (byte), value (big-endian length bytes)
verify_state "$EXPECTED_DAPP_ADDRESS" "$callPayload" '{"result":"0x230a152d02c7e14af6800000","id":1,"jsonrpc":"2.0"}'

echo "STAKECOIN COMPLETE"
