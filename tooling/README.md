# Launching the custom network 

For the aion unity protocol, it requires some environment settings before the hardfork. Therefore, the user must have to 

execute the `./customNetworkBootstrap.sh` in the `customBootstrap` folder. The script will wipe out the database in the `custom` folder , 

deploy the staking contract, setup the initial staking amount and then shut down the kernel. Once finished running the script, the user 

has the unity ready network for testing purpose. 



# Launching the staker

After the kernel setup ready by running the bootstrap script, the user can boot up the node and running the staker script.

The staker can be used directly from the terminal by launching `./launchStaker.sh` in the `externalStaker` folder

The script will execute the staker by the custom network default settings.

If you want to know more details about the external staker. Please read the `README.md` in the `externalStaker` folder.
 
