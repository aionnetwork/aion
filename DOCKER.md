# Dockerization

## Prerequisites

- docker-compose (docker-compose version 1.21.0, build 5920eb0)
- docker (>= Docker version 17.12.1-ce, build 7390fc6)

The kernel can be deployed as a container using the Dockerfile present in the root of the repo. 
The docker image can be built and pushed using the `pack_docker` and `push_docker` ant targets. Local development can
leverage `ant pack_dev_docker` to build a custom image based on current code base.

## Building

### Dev build

```bash
ant pack_dev_docker
```

**Note**: The image build uses the binary built in the `pack` directory by ant. 
If you want to use a specific kernel binary download the binary from the official repo, rename it to `aion.tar.bz2` and 
copy it to the `pack` directory


### Release build

```bash
ant pack_docker
```

This command will create 2 docker images
- centrys/aion-core:<kernelversionshortcommit>
- centrys/aion-core:latest

In order to have the image available for deployments you need to push it to the registry using the `push_docker` target

```bash
ant push_docker
```

## Running

You can start your kernel container using the `docker run` command and you can override a few parameters by passing
environment variables to the command.

Eg:

```bash
docker run -it \
-p 8545:8545 \
-p 8547:8547 \
-e difficulty="0x1" \
-e mining="true" \
-e coinbase_password=p@ss \
-e java_api_listen_address="0.0.0.0" \
-e rpc_listen_address="0.0.0.0" \
-e peer_list="p2p://peer-id-1@10.10.10.10:3333,p2p://peer-id-2@12.12.12.12:4444" \
-e override_peer_list="true" \
centrys/aion-core:0.2.8.f5317462
```

##### Expose API

```bash
# Support for access outside of the container
-e java_api_listen_address="0.0.0.0" \
-e rpc_listen_address="0.0.0.0" \

# Exposes the kernel API (web3 and java)
-p 8545:8545 \
-p 8547:8547 \
```

##### Override peer list

```bash
-e peer_list="p2p://peer-id-1@10.10.10.10:3333,p2p://peer-id-2@12.12.12.12:4444" \
-e override_peer_list="true" \
```

##### Bootstrap internal mining

```bash
# This will lower the difficulty for slow machines and generate an account at startup and set it as the coinbase
-e difficulty="0x1" \
-e mining="true" \
-e coinbase_password=p@ss \
```


**Note**: if you built a development image you can override parameters in the `supporting-services.yml` file and run it:

```bash
docker-compose -f supporting-services.yml up
```

List of environment variables than can override xml properties:

```bash
- difficulty
- coinbase_password
- rpc_listen_address
- rpc_listen_port
- cors_enabled
- apis_enabled
- java_api_listen_address
- java_api_listen_port
- p2p_listen_address
- p2p_listen_port
- discover
- mining
- miner_address
- cpu_mine_threads
- peer_list
- override_peer_list
- log_level_db
- log_level_vm
- log_level_gen
- log_level_api
- log_level_sync
- log_level_cons
- log_file
- log_path
```

If you need to override more properties update `aion-docker.py` to support those properties.
