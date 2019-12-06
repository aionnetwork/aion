# Launching the custom network 

The Aion Unity protocol requires some environment settings to enable the Unity hardfork.
To setup a custom network, the user should execute the `./customNetworkBootstrap.sh` in the `customBootstrap` folder.
The script will:
 * wipe out the database in the `custom` folder,
 * deploy the staking contract,
 * setup the initial staking amount,
 * and then shut down the kernel.
Once the script has finished running, the custom network is unity ready to be used for testing purposes.

# Launching the staker

After the kernel setup is ready (after running the bootstrap script), the user can boot up the node and run the staker script.

The staker can be used directly from the terminal by launching `./launchStaker.sh` in the `externalStaker` folder.

The script will run the staker using the custom network default settings.

<!--For more details regarding the external staker, please read the `README.md` in the `externalStaker` folder.-->
 
