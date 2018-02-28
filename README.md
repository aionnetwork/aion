# Aion

Mainstream adoption of blockchains has been limited because of scalability, privacy, and interoperability challenges. Aion is the first multi-tier blockchain network designed to address these challenges. 

Core to our hypothesis is the idea that many blockchains will be created to solve unique business challenges within unique industries. As such, the Aion network is designed to support custom blockchain architectures while providing a trustless mechanism for cross-chain interoperability. At the root of the Aion network is the first dedicated, public, enterprise blockchain: Aion-1.

Aion-1 is a state-of-the-art, third-generation blockchain that introduces a new paradigm of security and fair, representative, cryptoeconomic
incentives.

This repository contains the main kernel implementation and releases for the Aion network.

## System Requirements

* **Ubuntu 16.04** or a later version

## Build the Aion network

Please see the details in this wiki page [Build your Aion network](https://github.com/aionnetwork/aion/wiki/Build-your-Aion-network).

## Aion Installation

1. Download the latest Aion kernel release from the [releases page](https://github.com/aionnetwork/aion/releases). 

   **For the Test-Net Beta group users, the binaries will be provided though a link after [sign-up](https://blog.aion.network/testnetsignup-e39c9d6c593).**

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

**Optional:** Your kernel will have access to the seed nodes by default. Do not remove these nodes from the configuration. To include additional peers (e.g. friends that are also connected to the network), update the `config.xml` by adding nodes using the **permanent peer id** (generated as shown above), IP and port of the computers you wish to connect to:
    
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

## Launch Kernel 

In a terminal, from the aion directory, run: 

```
./aion.sh
```

**Optional:** To check which peers you are connected to, open another terminal and run the command below:

```
netstat -antp | grep java
```

Please check the [owner's manual wiki](https://github.com/aionnetwork/aion/wiki/Aion-Owner's-Manual) for further instructions on working with the kernel. 

## Documentation

Please check the [wiki pages](https://github.com/aionnetwork/aion/wiki) for further documentation on mining, using the Web3 API, command line options, etc.

[Aion White Papers](https://aion.network/whitepapers.html)

[Aion Owner's Manual](https://github.com/aionnetwork/aion/wiki/Aion-Owner's-Manual)

[Releases](https://github.com/aionnetwork/aion/releases)

[Initial Codebase Contributors](https://github.com/aionnetwork/aion/wiki/Contributors)

## Contact

[Aion Forum](https://forum.aion.network/)

[Aion Gitter](https://gitter.im/aionnetwork)

[Aion Reddit](https://www.reddit.com/r/AionNetwork/)


<!--For additional Aion **command line options** run:```./aion.sh -h```-->
