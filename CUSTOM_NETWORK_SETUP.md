# How to launch an Aion kernel syncing with a custom blockchain network.

1. Before starting the custom network, you must execute the `customNetworkBootstrap.sh` in the tooling folder. For more
   details please read the `README.md` in the `tooling` folder.

2. After executing the script above, you can launch the custom node with the command: `./aion.sh -n custom`.

3. Next, open a new terminal, go to the tooling folder, and run the `./launchStaker.sh` in the `externalStaker` folder.

4. Now your node can produce both mining and staking blocks according to the Unity protocol on your own custom network.
