#!/bin/bash

host="127.0.0.1"
port="8545"
avm="lib/avm.jar"

deployer='0xa0a49f0298ca87ad01e9256cca8d620d74a253f201a5b5760ba7e626779fd258'
password='PLAT4life'
dapp="./dapp.jar"

function print_help() {
	echo ' '
	echo 'Usage: ./beta.sh [OPTIONS]'
	echo ' '
	echo 'OPTIONS:'
	echo ' '
	echo '--transfer <sender address> <recipient address> <value>'
	echo '    Action: Transfers the specified amount of Aion from the sender account to the recipient account.'
	echo '    Returns: A transaction receipt hash that can be used later to query the transaction result.'
	echo ' '
	echo '--deploy <sender address> <path to dapp>'
	echo '    Action: Deploys the specified dApp.'
	echo '    Returns: A transaction receipt hash that can be used later to query the transaction result.'
	echo ' '
	echo '--deploy'
	echo '    Action: Deploys a hard-coded dApp from a hard-coded deployer address.'
	echo '    Returns: A transaction receipt hash that can be used later to query the transaction result.'
	echo ' '
	echo '--call <sender address> <dapp address> <method name> [[<arg type> <arg value>] ...]'
	echo '    Action: Calls into the specified dApp, invoking the specified method with the zero or more'
	echo '            provided arguments.'
	echo '            All arguments must first declare the type of the argument followed by the argument'
	echo '            Supported argument types: -I (int), -J (long), -S (short), -C (char), -F (float),'
	echo '                                      -D (double), -Z (boolean), -A (address), -T (string)'
	echo '    Returns: A transaction receipt hash that can be used later to query the transaction result.'
	echo ' '
	echo '--call-hard-coded <dapp address> <method name> [[<arg type> <arg value>] ...]'
	echo '    Action: Calls into the specified dApp, invoking the specified method with the zero or more'
	echo '            provided arguments using the hard-coded deployer address.'
	echo '            All arguments must be declared as specified in the above call.'
	echo '    Returns: A transaction receipt hash that can be used later to query the transaction result.'
	echo ' '
	echo '--get-receipt <receipt hash>'
	echo '    Action: Fetchs the transaction receipt for a transaction.'
	echo '    Returns: The transaction receipt corresponding to the specified receipt hash.'
	echo ' '
	echo '--get-logs <from block> <to block> [FILTER]'
	echo '    Action: Fetches any logs that meet the requirements of the provided filter (see the FILTER'
	echo '            section below), so long as these logs were fired off in the specified block range:'
	echo '            [from block, to block].'
	echo '    Note: from block & to block must be provided as hexadecimal values. Exactly 1 filter is allowed.'
	echo '    Returns: All logs that match the provided filter inside the specified block range.'
	echo ' '
	echo '--get-all-logs [FILTER]'
	echo '    Action: Fetches any logs that meet the requirements of the provided filter (see the FILTER'
	echo '            section below), considering all blocks from block zero to the most recent block.'
	echo '    Note: Exactly 1 filter is allowed.'
	echo '    Returns: All logs that match the provided filter.'
	echo ' '
	echo '--create-account'
	echo '    Action: Creates a new account and adds it to the keystore directory.'
	echo '    Returns: The public account address.'
	echo ' '
	echo 'FILTER:'
	echo '    -a <contract address>'
	echo '    -t <topics>'
	echo '    -b <block hash>'
	echo 'where topics must be provided in its hexadecimal ASCII representation.'
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

function transfer_data() {
	header='{"jsonrpc":"2.0","method":"eth_sendTransaction","params":[{"from": "'
	sender="$1"
	to='", "to": "'
	recipient="$2"
	gas='", "gas": "2000000", "gasPrice": "100000000000", "value": "'
	amount="$3"
	footer='"}]}'
	echo $header$sender$to$recipient$gas$amount$footer
}

function deploy_data() {
	header='{"jsonrpc":"2.0","method":"eth_sendTransaction","params":[{"from": "'
	sender="$1"
	gas='", "gas": "5000000", "gasPrice": "100000000000", "type": "0xf", "data": "'
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

function create_data() {
	header='{"jsonrpc":"2.0","method":"personal_newAccount","params":["'
	pass="$1"
	footer='"]}'
	echo $header$pass$footer
}

function filter_text() {
	filter_type="$1"
	filter_middle='": ["'
	filter_value="$2"
	echo $filter_header$filter_type$filter_middle$filter_value$filter_footer
}

function logs_data() {
	header='{"jsonrpc":"2.0","method":"eth_getLogs","params":[{"fromBlock": "'
	from="$1"
	toBlock='", "toBlock": "'
	to="$2"
	filter_prep='", "'
	filter="$3"
	footer='"]}]}'
	echo $header$from$toBlock$to$filter_prep$filter$footer
}

function all_logs_data() {
	header='{"jsonrpc":"2.0","method":"eth_getLogs","params":[{"fromBlock": "0", "'
	filter="$1"
	footer='"]}]}'
	echo $header$filter$footer
}

function result_is_true() {
	if [[ "$1" =~ (\"result\":true) ]];
	then
		return 0
	else
		return 1
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

function extract_account() {
	if [[ "$1" =~ (\"result\".+\"id) ]];
	then
		result=${BASH_REMATCH[0]:10}
		echo ${result:0:-5}
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

	echo ' '
	echo "Your transaction receipt hash: $receipt_hash"
	echo ' '
}

if [ "$1" = '--transfer' ]
then
	# value transfer must have 4 arguments.
	if [ $# -ne 4 ]
	then
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

	# unlock the sender account so we can make the transfer.
	echo 'Enter your account password:'
	read -s PASSWORD

	unlock_payload="$(unlock_data "$2" "$PASSWORD")"

	# send the transaction off.
	transfer_payload="$(transfer_data "$2" "$3" "$4")"
	unlock_and_send "$unlock_payload" "$transfer_payload"

elif [ "$1" = '--get-receipt' ]
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

	echo 'Your transaction receipt is:'
	echo ' '
	echo $(extract_receipt "$response")
	echo ' '

elif [ "$1" = '--deploy' ]
then
	if [ $# -eq 3 ]
	then
		# grab the bytes of the deployment jar.
		jar="$(java -jar $avm bytes "$3")"
		if [ $? -ne 0 ]
		then
			exit 1
		fi

		# unlock the sender account so we can make the transfer.
		echo 'Enter your account password:'
		read -s PASSWORD

		unlock_payload="$(unlock_data "$2" "$PASSWORD")"

		deploy_payload="$(deploy_data "$2" "$jar")"
		unlock_and_send "$unlock_payload" "$deploy_payload"

	elif [ $# -eq 1 ]
	then
		# grab the bytes of the hard-coded deployment jar.
		jar="$(java -jar $avm bytes "$dapp")"
		if [ $? -ne 0 ]
		then
			exit 1
		fi

		# unlock the hard-coded sender account so we can make the transfer.
		unlock_payload="$(unlock_data "$deployer" "$password")"

		deploy_payload="$(deploy_data "$deployer" "$jar")"
		unlock_and_send "$unlock_payload" "$deploy_payload"

	else
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

elif [ "$1" = '--call' ]
then
	# call must have at least 4 arguments
	if [ $# -lt 4 ]
	then
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

	# grab the method call encoding.
	if [ $# -eq 4 ]
	then
		call_encoding="$(java -jar $avm encode-call "$2" --method "$4")"
	else
		call_encoding="$(java -jar $avm encode-call "$2" --method "$4" --args "${@:5:$#}")"
	fi
	
	# check if there was an encoding error.
	if [ $? -ne 0 ]
	then
		echo 'ERROR: encoding error'
		exit 1
	fi

	# unlock the sender account so we can make the call.
	echo 'Enter your account password:'
	read -s PASSWORD

	unlock_payload="$(unlock_data "$2" "$PASSWORD")"

	call_payload="$(call_data "$2" "$3" "$call_encoding")"
	unlock_and_send "$unlock_payload" "$call_payload"

elif [ "$1" = '--call-hard-coded' ]
then
	# call must have at least 3 arguments
	if [ $# -lt 3 ]
	then
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

	# grab the method call encoding.
	if [ $# -eq 3 ]
	then
		call_encoding="$(java -jar $avm encode-call $deployer --method "$3")"
	else
		call_encoding="$(java -jar $avm encode-call $deployer --method "$3" --args "${@:4:$#}")"
	fi

	# check if there was an encoding error.
	if [ $? -ne 0 ]
	then
		echo 'ERROR: encoding error'
		exit 1
	fi

	unlock_payload="$(unlock_data $deployer $password)"
	call_payload="$(call_data $deployer "$2" "$call_encoding")"
	unlock_and_send "$unlock_payload" "$call_payload"

elif [ "$1" = '--get-logs' ]
then
	# get logs must have 5 arguments
	if [ $# -ne 5 ]
	then
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

	# get the correct payload for the provided filter
	if [ "$4" = '-a' ]
	then
		filter_payload="$(filter_text address "$5")"
	elif [ "$4" = '-t' ]
	then
		filter_payload="$(filter_text topics "$5")"
	elif [ "$4" = '-b' ]
	then
		filter_payload="$(filter_text blockhash "$5")"
	else
		echo ' '
		echo "Error: unrecognized symbol: $4"
		exit 1
	fi

	logs="$(curl -s -X POST --data "$(logs_data "$2" "$3" "$filter_payload")" http://$host:$port)"
	if [ $? -eq 7 ]
	then
		bad_connection_msg
		exit 1
	fi

	echo ' '
	echo "The logs matching the specified filter are:"
	echo $logs
	echo ' '

elif [ "$1" = '--get-all-logs' ]
then
	# get all logs must have 3 arguments
	if [ $# -ne 3 ]
	then
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

	# get the correct payload for the provided filter
	if [ "$2" = '-a' ]
	then
		filter_payload="$(filter_text address "$3")"
	elif [ "$2" = '-t' ]
	then
		filter_payload="$(filter_text topics "$3")"
	elif [ "$2" = '-b' ]
	then
		filter_payload="$(filter_text blockhash "$3")"
	else
		echo ' '
		echo "Error: unrecognized symbol: $2"
		exit 1
	fi

	logs="$(curl -s -X POST --data "$(all_logs_data "$filter_payload")" http://$host:$port)"
	if [ $? -eq 7 ]
	then
		bad_connection_msg
		exit 1
	fi

	echo ' '
	echo "The logs matching the specified filter are:"
	echo $logs
	echo ' '

elif [ "$1" = '--create-account' ]
then
	# create account must have 1 argument
	if [ $# -ne 1 ]
	then
		echo 'Incorrect number of arguments given!'
		print_help
		exit 1
	fi

	echo 'Enter a password for the account:'
	read -s PASSWORD

	# create the account and relay the address
	account="$(curl -s -X POST --data "$(create_data "$PASSWORD")" http://$host:$port)"
	if [ $? -eq 7 ]
	then
		bad_connection_msg
		exit 1
	fi

	address="$(extract_account "$account")"
	echo ' '
	echo "Your newly created account address is: $address"
	echo ' '

else
	print_help
	exit 1
fi



