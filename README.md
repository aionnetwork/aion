# Aion

Mainstream adoption of blockchains has been limited because of scalability, privacy, and interoperability challenges. Aion is a multi-tier blockchain network designed to address these challenges. 

Core to our hypothesis is the idea that many blockchains will be created to solve unique business challenges within unique industries. As such, the Aion network is designed to support custom blockchain architectures while providing a trustless mechanism for cross-chain interoperability. 

The [Aion White Papers](https://aion.network/whitepapers.html) provides more details regarding our design and project roadmap. 

This repository contains the main kernel implementation and releases for the Aion network.

## System Requirements

* **Ubuntu 16.04** or a later version

## Build the Aion network

Please see the details in this wiki page [Build your Aion network](https://github.com/aionnetwork/aion/wiki/Build-your-Aion-network).

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

<!--In a terminal, run the command below to generate a default configuration: `./aion.sh -c`-->

To receive tokens for mining blocks, you first need to create an account using:
    
```
./aion.sh -a create
```

The [mining wiki](https://github.com/aionnetwork/aion/wiki/Internal-Miner) illustrates how to set this account to be able to receive tokens for mining.

Now you are ready to start the kernel.

**Optional:** 

Your kernel will have access to the seed nodes by default. Do not remove these nodes from the configuration. To include additional peers (e.g. friends that are also connected to the network) or get added by peers, update the `config.xml` by adding nodes using the **permanent peer id** (generated as shown below), IP and port of the computers you wish to connect to:
    
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

To get a permanent peer id is by creating a new configuration: 

```
./aion.sh -c
```

This newly made configuration will not have access to seed nodes by default. In order to connect to seed nodes, you will need to edit the `config.xml` file by adding nodes as listed from [here](https://github.com/aionnetwork/aion/wiki/Aion-Seed-nodes):

```
<nodes>
  <node>p2p://2da62542-999f-4405-bdb3-50d8c61bed61@52.237.31.69:30303</node>
  <node>p2p://c1f42646-279a-441e-bba7-bfebfc1eec63@52.179.100.107:30303</node>
  <node>p2p://0466a78b-814b-4a5d-844e-7054e48f0d28@191.232.176.213:30303</node>
  <node>p2p://f9ea8c08-6f2d-4e64-91a2-d7186d76e096@52.231.206.150:30303</node>
  <node>p2p://d9242b38-cf4e-4654-9995-2727fee3dd9d@13.95.218.95:30303</node>
</nodes>
```

You are welcome to add other seed nodes (not solely restricted to what is shown above).

## Launch Kernel 

In a terminal, from the aion directory, run: 

```
./aion.sh
```

When the kernel starts up, you should see it trying to sync with the latest block. 

**Optional:** To check which peers you are connected to, open another terminal and run the command below:

```
netstat -antp | grep java
```

Please check the [owner's manual wiki](https://github.com/aionnetwork/aion/wiki/Aion-Owner's-Manual) for further instructions on working with the kernel. 

## Documentation

Please check the [wiki pages](https://github.com/aionnetwork/aion/wiki) for further documentation on mining, using the Web3 API, command line options, etc.

## Contact

[Aion Forum](https://forum.aion.network/)

[Aion Gitter](https://gitter.im/aionnetwork)

[Aion Reddit](https://www.reddit.com/r/AionNetwork/)

## License

Aion is released under the [LGPL-V3 license](https://github.com/aionnetwork/aion/blob/dev/LICENSE)


<!--For additional Aion **command line options** run:```./aion.sh -h```-->
