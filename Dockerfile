ARG UBUNTU_VER=16.04
FROM ubuntu:${UBUNTU_VER}

LABEL maintainers="mihai.cojan@centrys.io, alexandru.laurus@centrys.io"

# prepare for java instalation
RUN apt-get update && apt-get install -y bzip2 lsb-release wget curl jq locales net-tools libicu-dev libedit-dev
RUN apt-get clean

# change locales to UTF-8 in order to avoid bug when changing config.xml
RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && locale-gen
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

WORKDIR /opt

# TODO: use java 10.0.2
ARG DEV_BUILD=false
RUN if [ "${DEV_BUILD}" = "true" ]; then wget -O jdk.tar.gz \
https://download.java.net/java/GA/jdk10/10.0.1/fb4372174a714e6b8c52526dc134031e/10/openjdk-10.0.1_linux-x64_bin.tar.gz; fi
RUN if [ "${DEV_BUILD}" = "true" ]; then tar -xf jdk.tar.gz; fi
RUN if [ "${DEV_BUILD}" = "true" ]; then rm -f jdk.tar.gz; fi

ARG KERNEL_PATH=./pack/aion.tar.bz2
# COPY has a different behaviour in docker-compose vs docker so we'll have to use ADD
# ADD does some magic and automatically unpacks so we need to fix that
ADD ${KERNEL_PATH} /opt/aion.tar.bz2
# again different behaivour for ADD when unpacking in docker vs docker-compose
RUN if [ -d /opt/aion.tar.bz2/aion ]; then mkdir -p /opt/aion; fi
RUN if [ -d /opt/aion.tar.bz2/aion ]; then mv /opt/aion.tar.bz2/aion/* /opt/aion/; fi
RUN if [ -d /opt/aion.tar.bz2/aion ]; then rm -rf /opt/aion.tar.bz2; else mv /opt/aion.tar.bz2 /opt/aion; fi

RUN if [ "${DEV_BUILD}" = "true" ]; then cp jdk-10.0.1/lib/libjdwp.so ./aion/rt/lib/.; fi
RUN if [ "${DEV_BUILD}" = "true" ]; then cp jdk-10.0.1/lib/libdt_socket.so ./aion/rt/lib/.; fi
RUN if [ "${DEV_BUILD}" = "true" ]; then rm -rf jdk-10.0.1; fi

WORKDIR /opt/aion

COPY ./override-config.py .
COPY ./create-coinbase.sh .
COPY ./aion-docker.sh .

CMD [ "/bin/bash", "-c", "./aion-docker.sh" ]
