#!/bin/bash

host="127.0.0.1"
port="8545"
avm="lib/avm.jar"

function print_help() {
	echo ' '
	echo 'Usage: ./auto_rpc.sh [OPTIONS]'
	echo ' Variant of rpc.sh for automated testing of a build '
	echo ' '
	echo 'OPTIONS:'
	echo ' '
	echo '--deploy <sender address> <path to dapp> <password>'
	echo '    Action: Deploys the specified dApp.'
	echo '    Returns: A transaction receipt hash that can be used later to query the transaction result.'
	echo ' '
	echo '--call <password> <sender address> <dapp address> <method name> [[<arg type> <arg value>] ...]'
	echo '    Action: Calls into the specified dApp, invoking the specified method with the zero or more'
	echo '            provided arguments.'
	echo '            All arguments must first declare the type of the argument followed by the argument'
	echo '            Supported argument types: -I (int), -J (long), -S (short), -C (char), -F (float),'
	echo '                                      -D (double), -Z (boolean), -A (address), -T (string)'
	echo '    Returns: A transaction receipt hash that can be used later to query the transaction result.'
	echo ' '
	echo '--get-receipt-address <receipt hash>'
	echo '  Returns the address from a deployment - fails with status 1 if not found and status 2 if failed'
	echo ' '
	echo '--check-receipt-status <receipt hash>'
	echo '  Checks the receipt status, exit 0 on pass - fails with status 1 if not found and status 2 if failed'
	echo ' '
}

function unlock_data() {
	header='{"jsonrpc":"2.0","method":"personal_unlockAccount","params":["'
	account="$1"
	space='", "'
	password="$2"
	footer='", "600"]}'
	echo $header$account$space$password$footer
}

function receipt_data() {
	header='{"jsonrpc":"2.0","method":"eth_getTransactionReceipt","params":['
	quote="'"
	receipt="$1"
	footer="']}"
	echo $header$quote$receipt$footer
}

function deploy_data() {
	header='{"jsonrpc":"2.0","method":"eth_sendTransaction","params":[{"from": "'
	sender="$1"
	gas='", "gas": "5000000", "gasPrice": "100000000000", "type": "0x2", "data": "'
	data="$2"
	footer='"}]}'
	echo $header$sender$gas$data$footer
}

function call_data() {
	header='{"jsonrpc":"2.0","method":"eth_sendTransaction","params":[{"from": "'
	sender="$1"
	to='", "to": "'
	recipient="$2"
	gas='", "gas": "2000000", "gasPrice": "100000000000", "data": "'
	data="$3"
	footer='"}]}'
	echo $header$sender$to$recipient$gas$data$footer
}

function result_is_true() {
	if [[ "$1" =~ (\"result\":true) ]];
	then
		return 0
	else
		return 1
	fi
}

function extract_status() {
	if [[ "$1" =~ (\"status\".+\"id) ]];
	then
		result=${BASH_REMATCH[0]:10}
		echo ${result:0:3}
	fi
}

function extract_receipt_hash() {
	if [[ "$1" =~ (\"result\":\"0x[0-9a-f]{64}) ]];
	then
		echo ${BASH_REMATCH[0]:10:66}
	fi
}

function extract_receipt() {
	if [[ "$1" =~ (\"result\".+\"id) ]];
	then
		result=${BASH_REMATCH[0]:9}
		echo ${result:0:-4}
	fi
}

function extract_address_from_receipt() {
	if [[ "$1" =~ (\"contractAddress\".+\"id) ]];
	then
		result=${BASH_REMATCH[0]:19}
		echo ${result:0:66}
	fi
}

function bad_connection_msg() {
	echo ' '
	echo "Unable to establish a connection using host $host and port $port. "
	echo "Ensure that the kernel is running and that it is running on the specified host and port, and that the kernel rpc connection is enabled. "
	echo 'The kernel rpc connection details can be modified in the configuration file at: config/config.xml'
}

if [ $# -eq 0 ]
then
	print_help
	exit 1
fi

function unlock_and_send() {
	response=$(curl -s -X POST --data "$1" http://$host:$port)
	if [ $? -eq 7 ]
	then
		bad_connection_msg
		exit 1
	fi

	result_is_true "$response"
	if [ $? -ne 0 ]
	then
		echo ' '
		echo 'Error: unable to unlock the sender account!'
		echo 'Please check the account address and password you supplied.'
		exit 1
	fi

	# send the transaction.
	response=$(curl -s -X POST --data "$2" http://$host:$port)
	if [ $? -eq 7 ]
	then
		bad_connection_msg
		exit 1
	fi

	receipt_hash=$(extract_receipt_hash "$response")
	echo $receipt_hash
}

if [ "$1" = '--get-receipt-address' ]
then
	# get receipt must have 2 arguments.
	if [ $# -ne 2 ]
	then
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

	# query the transaction receipt.
	response=$(curl -s -X POST --data "$(receipt_data "$2")" http://$host:$port)
	if [ $? -eq 7 ]
	then
		bad_connection_msg
		exit 1
	fi

	status=$(extract_status "$response")

	if [ "0x0" == "$status" ]
	then
		exit 2
	fi
	address=$(extract_address_from_receipt "$response")
	echo "$address"

elif [ "$1" = '--check-receipt-status' ]
then
	# get receipt must have 2 arguments.
	if [ $# -ne 2 ]
	then
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

	# query the transaction receipt.
	response=$(curl -s -X POST --data "$(receipt_data "$2")" http://$host:$port)
	if [ $? -eq 7 ]
	then
		bad_connection_msg
		exit 1
	fi

	status=$(extract_status "$response")

	if [ "0x0" == "$status" ]
	then
		exit 2
	elif [ "0x1" == "$status" ]
	then
		exit 0
	else
		exit 1
	fi

elif [ "$1" = '--deploy' ]
then
	if [ $# -eq 4 ]
	then
		# grab the bytes of the deployment jar.
		jar="$(java -jar $avm bytes "$3")"
		if [ $? -ne 0 ]
		then
			exit 1
		fi

		# unlock the sender account so we can make the transfer.
		PASSWORD="$4"

		unlock_payload="$(unlock_data "$2" "$PASSWORD")"

		deploy_payload="$(deploy_data "$2" "$jar")"
		unlock_and_send "$unlock_payload" "$deploy_payload"

	else
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

elif [ "$1" = '--call' ]
then
	# call must have at least 5 arguments
	if [ $# -lt 5 ]
	then
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

	# grab the method call encoding.
	if [ $# -eq 5 ]
	then
		call_encoding="$(java -jar $avm encode-call "$3" --method "$5")"
	else
		call_encoding="$(java -jar $avm encode-call "$3" --method "$5" --args "${@:6:$#}")"
	fi
	
	# check if there was an encoding error.
	if [ $? -ne 0 ]
	then
		echo 'ERROR: encoding error'
		exit 1
	fi

	# unlock the sender account so we can make the call.
	PASSWORD="$2"

	unlock_payload="$(unlock_data "$3" "$PASSWORD")"

	call_payload="$(call_data "$3" "$4" "$call_encoding")"
	unlock_and_send "$unlock_payload" "$call_payload"

else
	print_help
	exit 1
fi



