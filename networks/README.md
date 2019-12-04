# Supported Networks

This directory contains the networks supported by this release.

Currently, the supported networks are:
* **mainnet** - the official Open Application Network platform ([https://mainnet.theoan.com/](https://mainnet.theoan.com/))
* **amity** - a test network with seed nodes setup by the foundation ([https://amity.theoan.com/](https://amity.theoan.com/))
* **custom** - a customizable test network without seed nodes

Each directory contains three files:
* `config.xml`
* `fork.properties`
* `genesis.json`

The `genesis.json` and `fork.properties` files for **mainnet** and **amity** networks provided with each release **should never be modified**.
Changing these files will cause your node to be unable to sync to the above mentioned networks.
The `config.xml` can be updated, but we strongly advise against doing so.
To change the configuration settings we recommend modifying the configuration inside the execution directory (typically located at `<data_dir>/<network_name>` directory or `<network_name>` directory) that also contains the database, logs and keystore.

When using a **custom** network one typically needs to change both the `genesis.json` and `fork.properties`.
These files should be changed in this directory since they are not copied to the execution directory.
Similar to the other networks, we recommend updating the `config.xml` file only in the execution directory.
