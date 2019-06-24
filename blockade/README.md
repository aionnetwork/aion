# Set Up a Network Using Blockade

## Prerequisites

You will need to install the following software:

- docker and docker-compose
[How to install docker](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-18-04)
- iproute2 tools (`ip` and `tc` specifically, typically pre-installed)

and the blockade package:

```
pip install blockade
```

## Create a docker image (For aion rust kernel)

The next step is to create a docker image, which is required every time the kernel is updated.

Before you start, make sure the docker service is running and your user has access to the Docker API.
Please check [docker connect issues](https://stackoverflow.com/questions/21871479/docker-cant-connect-to-docker-daemon)
for instruction. Then, run in a terminal

```
(cd .. && cargo build && cp target/debug/aion blockade/)
sudo docker build -t unity:latest .
```

Test if the docker image is ready to use:
```
docker run unity "./aion"
```

Note: this article assume your work directory is this folder unless otherwise stated.

## Create a docker image (For aion java kernel)

Under the Aion Java kernel project root path:
```
./gradlew packDocker
```
or 
```
./gradlew packDockerWithVersion
```

Test if the docker image is ready to use:
```
docker run aionnetwork/aion:latest "./aion.sh"
```
or 
```
docker run aionnetwork/aion:<build version> "./aion.sh"
```

Note: this article assume your work directory is this folder unless otherwise stated.

## Launch the network

Before you launch the network, you need to modify the paths in the `blockade.yml` file. This is required because
the docker mount paths must be absolute. Then, you can start the network by the following command:

```
blockade up
```

To destroy the network, run
```
blockade destroy
```

For more blockade setting details or usage,
please check [blockade docs](https://blockade.readthedocs.io/en/latest/)

## Miner and staker addresses (Developing)

These following addresses are extracted based on the config files in `node1`, `node2`, ...

Miners:
```
node1: 0x1111111111111111111111111111111111111111111111111111111111111111
node2: 0x2222222222222222222222222222222222222222222222222222222222222222
node3: 0x3333333333333333333333333333333333333333333333333333333333333333
node4: 0x4444444444444444444444444444444444444444444444444444444444444444
```

Stakers:
```
node1: 0xa0bd75fcd7676504671ee75f95e1ed7ada6d168d1b852956568e1a32ce6a7886
node2: 0xa08c895fc144884e989a32a7cbfbf47346ad3926f57635a10f10c24b09135ca3
node3: 0xa06586f27e6c4e218183cde720931b35056d3857b52b8aa28afbf0db110cac03
node4: 0xa0d9342bc958587c8f14781eb6b124f68336d3921732a111343f11df0e3f13fb
```

## Set up PoW mining (Developing)

Use the CPU aion miner (change the port number to for a different node):
```
./aionminer -l 127.0.0.1:8001 -u 0xa0a95e710948d17a33c9bab892b33f7b25da064d3109f12ac89f249291b5dcd9 -d 0 -t 1
```

## Set up PoS forging (Developing)

1. Deploy the staking registry contract, using script  located at `../solidity/`:

    ```
    node deploy.js deploy
    ```

2. Register a staker (addresses are listed above):

    ```
    node deploy.js call register [address]
    ```

3. Vote for a staker

    ```
    node deploy.js call vote [address] [value]
    ```

4. Unvote for a staker

    ```
    node deploy.js call unvote [address] [value]
    ```

## Partition the network

Your can partition the network into groups by

```
blockade partition n1,n2
```

Or, to apply a random partition:
```
blockade random-partition
```

## Other useful commands
To check the kernel running log
```
docker logs <Container ID>or<name> -f
ex. docker logs blockade_n1 -f

```

To login in the docker container
```
docker exec -it <Container ID>or<name> bash
```

To attach to a node, run:

```
docker attach blockade_n1
```

To create a web3 console to a node, go to the `aion-web3` folder and run:

```
node console.js 127.0.0.1:9001
```
    
To delete the databases and reset the network, run:

For aion rust kernel
```
sudo rm -fr node*/databases
```
For aion java kernel
```
sudo rm -fr node*/database
```
