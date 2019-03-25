#!/bin/bash

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
	expected="$2"
	payload={"jsonrpc":"2.0","method":"eth_call","params":[{"to":"$address"}],"id":1}
	response=`curl -s -X POST --data "$payload" 127.0.0.1:8545`
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
		`./auto_rpc.sh --check-receipt-status "$receipt"`
		result=$?
		if [ "2" == "$result" ]
		then
			echo "Error"
			exit 1
		fi
	done
}


if [ $# -ne 1 ]
then
        echo "MUST SPECIFY PASSWORD"
        exit 1
fi
password="$1"

echo "Deploying the test.jar..."
receipt=`./auto_rpc.sh --deploy 0xa0d6dec327f522f9c8d342921148a6c42f40a3ce45c1f56baa7bfa752200d9e5 ./test.jar "$password"`
require_success $?

echo "Deployment returned receipt: \"$receipt\".  Waiting for deployment to complete..."
address=""
while [ "" == "$address" ]
do
	echo " waiting..."
	sleep 1
	address=`./auto_rpc.sh --get-receipt-address "$receipt"`
	require_success $?
done
echo "Deployed to address: \"$address\""

echo "Verifying initial program state..."
verify_state "$address" '{"result":"0x0102","id":1,"jsonrpc":"2.0"}'
echo "Initital state good"

echo "Sending first transaction..."
receipt=`./auto_rpc.sh --call "$password" 0xa0d6dec327f522f9c8d342921148a6c42f40a3ce45c1f56baa7bfa752200d9e5 "$address" a`
echo "$receipt"
require_success $?

echo "Transaction returned receipt: \"$receipt\".  Waiting for transaction to complete..."
wait_for_receipt "$receipt"
echo "Transaction completed"

echo "Verify state after first transaction..."
verify_state "$address" '{"result":"0x21000161","id":1,"jsonrpc":"2.0"}'
echo "State good"

echo "Sending second transaction..."
receipt=`./auto_rpc.sh --call "$password" 0xa0d6dec327f522f9c8d342921148a6c42f40a3ce45c1f56baa7bfa752200d9e5 "$address" ASDF`
echo "$receipt"
require_success $?

echo "Transaction returned receipt: \"$receipt\".  Waiting for transaction to complete..."
wait_for_receipt "$receipt"
echo "Transaction completed"

echo "Verify state after second transaction..."
verify_state "$address" '{"result":"0x21000441534446","id":1,"jsonrpc":"2.0"}'
echo "Final state good.  Test complete!"

