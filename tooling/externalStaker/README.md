# External Staker

This is a very simple external staker designed to be used with Aion's Unity Hybrid Consensus Protocol. The staker can be used to interact with a Unity-enabled node over RPC.

The staker is largely built on top of the RPC functionality provided by the [Node Test Harness](https://github.com/aionnetwork/node_test_harness "Node Test Harness").

# Launching the staker

The staker can be used directly from the terminal by launching

`java -jar external_staker.jar <args>`

The staker takes the following arguments when launched:

1. **(required)** the private key of the signing address of the staker. 

2. *(optional)*) the IP address of a Unity-enabled node accepting RPC requests. If this argument isn't provided, `127.0.0.1` is used as default.

3. *(optional)*) the port on which this node is accepting RPC requests. If this argument isn't provided, `8545` is used as default.

# Behaviour

Once launched, the staker repeatedly queries the node over RPC, and submits signed staking blocks. Note that it aggressively sends a signed block as soon as it produces it; the nodes must reject future blocks if they want to.
