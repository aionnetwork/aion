# Aion

This repository contains the main kernel implementation and releases for the Aion network.

## System Requirements

* **Ubuntu 16.04** or a later version

## Aion Installation

1. Download the latest Aion kernel release from the [releases page](https://github.com/aionnetwork/aion/releases). 

2. Unarchive the downloaded file by right clicking on it and selecting `Extract Here` from the drop-down menu. 
The `aion` folder will be generated in the current folder. 
    
Alternatively, to extract the file contents, run in a terminal: 
    
```
tar xvjf aion-{@version}.tar.bz2
```

3. Navigate to the `aion` folder and continue by configuring the network:
    
```
cd aion
```

## Aion Network Configuration

To connect to the Aion test network you need to first generate a configuration file.
In a terminal, run the command below to generate a default configuration: 
    
```
./aion.sh -c
```

To receive tokens for mining blocks, you first need to create an account using:
    
```
./aion.sh -a create
```

The [mining wiki](https://github.com/aionnetwork/aion_miner/tree/master/aion_solo_pool#6-verify-aion-configuration) illustrates how to set this account to be able to receive tokens for mining.

Now you are ready to start the kernel.

**Optional:** Your kernel will have access to the seed nodes by default. To include additional peers (e.g. friends that are also connected to the network) navigate to the `config.xml` file in `[aion_folder]/config/config.xml`:
    
```
cd config
gedit config.xml
```
    
Update the `config.xml` by adding nodes using the IP and port of the computers you wish to connect to:
    
```
<net>
    <p2p>
        <ip>0.0.0.0</ip>
        <port>30303</port>
    </p2p>
    <nodes>
        <node>p2p://PEER_ID@IP:PORT</node>
    </nodes>
</net>
```
    
**Note:** To allow peers to connect to you, you must also change your configuration IP from **127.0.0.1** to a public IP on your machine. If you are unsure about having a public IP, set it to **0.0.0.0**.

## Launch kernel 

In a terminal, from the aion directory, run: 

```
./aion.sh
```

**Optional:** To check which peers you are connected to, open another terminal and run the command below:

```
netstat -antp | grep java
```

## Documentation

Please check the [wiki pages](https://github.com/aionnetwork/aion/wiki) for further documentation. 

For additional Aion **command line options** run:
```
./aion.sh -h
```
