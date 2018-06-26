# Dockerization

The kernel can be deployed as a container using the Dockerfile present in the root of the repo. 
The docker image can be built and pushed using the `pack_docker` and `push_docker` ant targets.

## Building

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
-e java_api_listen_address="0.0.0.0" \
-e rpc_listen_address="0.0.0.0" \
-e peer_list="p2p://peer-id-1@10.10.10.10:3333,p2p://peer-id-2@12.12.12.12:4444" \
-e override_peer_list="true"
aion-core:v0.2.7b1677af
```

List of environment variables than can override xml properties:

```bash
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
