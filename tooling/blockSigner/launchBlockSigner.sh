#!/bin/bash

echo
echo "Launching external staker..."
echo

SIGNING_ADDRESS_PRIVATE_KEY="0xcc76648ce8798bc18130bc9d637995e5c42a922ebeab78795fac58081b9cf9d4"
COINBASE_ADDRESS="0xa02df9004be3c4a20aeb50c459212412b1d0a58da3e1ac70ba74dde6b4accf4b"
NODE_IP="127.0.0.1"
NODE_PORT="8545"
NETWORK="amity"
VERBOSE="false"

java -jar block_signer.jar $SIGNING_ADDRESS_PRIVATE_KEY $COINBASE_ADDRESS $NETWORK $NODE_IP $NODE_PORT $VERBOSE

echo
echo "Shutting down the external staker..."
echo
