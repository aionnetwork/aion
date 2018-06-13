# Dockerization

The kernel can be deployed as a container using the Dockerfile present in the root of the repo.
Currently there is no automated way to build it but it provides an argument to pick the release you want to use to build the image.

## Building

Building is done by specifying the `kernel_archive` argument which is a full URL to the github kernel release.
Make sure you tag the image correctly by specifying the `version_tag`.

Note: You can use your own registry or build locally, but you need to specify the repository of the image
if you intend to push it to the `centrys` docker registry.

```bash
docker build -t \
centrys/aion-docker:<version_tag> . \
--build-arg kernel_archive=https://github.com/aionnetwork/aion/releases/download/<version>/<archive>
```

Eg:

```bash
docker build -t \
centrys/aion-docker:v0.2.7 . \
--build-arg kernel_archive=https://github.com/aionnetwork/aion/releases/download/v0.2.7/aion-v0.2.7.1bbeec1-2018-05-24.tar.bz2
```

## Running

You can start your kernel container using the `docker run` command and you can override a few parameters by passing
environment variables to the command.

Eg:

```bash
docker run -it \
-e java_api_listen_address=0.0.0.0 \
-e rpc_listen_address=0.0.0.0 \
aion-docker:v0.2.7
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
```

If you need to override more properties update `aion-docker.py` to support those properties.
