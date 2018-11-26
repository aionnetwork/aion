# Dockerization

Provides a container that can run the AION kernel. Can also attach a remote debugger if necessary.

## Limitations

Currently this image does NOT support running CLI commands before the kernel startup.
One needs to build a custom image and bake in other CLI commands if necessary (override the container startup script).

## Prerequisites

- docker-compose (docker-compose version 1.21.0, build 5920eb0)
- docker (>= Docker version 17.12.1-ce, build 7390fc6)

The kernel can be deployed as a container using the Dockerfile present in the root of the repo. 
The docker image can be built using the `packDocker` Gradle target. Local development can
leverage `./gradlew packDevDocker` to build a custom image based on current code base.

## Building

### Dev build

```bash
./gradlew packDevDocker
```

##### Description

This command will:
* build the project with `-Dcompile.debug=true`
* build a custom Docker image using the current code adding java debug libs for remote debugging (this requires
downloading a version of java different from the one automatically packed by the kernel binary)

This build is conditioned by the `DEV_BUILD` argument in the `Dockerfile` so you can add more development related 
behaviour by using this flag.

##### Remote debug

If you want to use the remote debugging feature:
* make sure you expose the correct port where you want to attach which is set in the `supporting-services.yml` 
and defaults to `6006`:
```bash
- JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:6006 -Xms4g
```  

* run the image as described below in [Running](#Running) and expose the debug port:
```bash
...
-p 6006:6006
...
```

**Note**: The image build uses the binary built in the `pack` directory by Gradle.
If you want to use a specific kernel binary download the binary from the official repo, rename it to `aion.tar.bz2` and 
copy it to the `pack` directory


### Release build

#### Build docker image

```bash
./gradlew packDocker
```

This command will create 1 docker image with the tag value as `GITVER` variable from `script/prebuild.sh` which is the
short commit revision.
Eg:
```bash
aion-core:0.2.8f5317462
```

#### Push docker image

In order to have the image available for deployments/developers you need to tag and push it to you registry of choice manually.

* tag image according to repo:
```bash
docker tag aion-core:<short_commit_revision> <your_docker_registry>/aion-core:<short_commit_revision> 
```

* push image:
```bash
docker push <your_docker_registry>/aion-core:<short_commit_revision>
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
aion-core:0.2.8.f5317462
```

**Note**: 
We don't support setting options for the script as the container will have to be torn down after
running and recreated again. This is not feasible in a production environment. Instead of passing options to the startup 
script we should provide a rpc/java API that will allow the required functionality to be present after kernel startup.
Until then we can still use that functionality by manually starting a bash session in the container and running
the commands that we need. 

Eg:

* Start the container as described above
* In a separate terminal get the container id
```bash
docker ps
``` 

* Start a bash session in the container
```bash
docker run -it <contaier_id> bash
```

* Make sure you are in the `/opt/aion` directory and run your desired commands:
```bash
./aion.sh -a list
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

**Note**: The override of these variables are mostly for local development/testing purposes. 

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

If you need to override more properties, update `override-config.py` to support those properties.
