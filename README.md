# Aion

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

> If you are using **aion-v0.1.8** or if you want a **permanent id** (used by peers), to connect to the Aion test network you need to first modify your configuration file to have a new personalized id. 
>
> - Download the ID generation script ***generateId.sh*** [here](https://github.com/aionnetwork/aion/blob/master/generateId.sh).
> - Add executable permissions to the script.
> ``` 
> chmod +x generateId.sh
> ```
> - Run the script.
>
> ```
> ./generateId.sh
> ```
> - Copy the output.
>
> Navigate to the `config.xml` file in `[aion_folder]/config/config.xml`:
>
> ```
> cd config
> gedit config.xml
> ```
>
> Update the value between the ***id*** tags to the copied ID.
>
> ```
> <id>my-new-id-value-is-set-here-12345678</id>
> ```
> Versions **aion-v0.1.9** and later do not require generating an id. A temporary unique id will be assigned to your kernel at runtime.
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

[Aion Forum](https://forum.aion.network/)
[Aion Gitter](https://gitter.im/aionnetwork)
[Aion Reddit](https://www.reddit.com/r/AionNetwork/)

The Aion Foundation repository layout includes the following: 

[Aion Core](https://github.com/aionnetwork/aion)
[Aion FastVM](https://github.com/aionnetwork/aion_fastvm)
[Aion Miner](https://github.com/aionnetwork/aion_miner)
[Aion Interchain](https://github.com/aionnetwork/aion_interchain)
[Aion Compatible Web3 API](https://github.com/aionnetwork/aion_web3)
[Aion Java API](https://github.com/aionnetwork/aion_api)


<!--For additional Aion **command line options** run:```./aion.sh -h```-->
