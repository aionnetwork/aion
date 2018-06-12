FROM openjdk:9

#TODO: this image downloads a release of the kernel and uses ENV variables in order to override some parameters in config.xml
#TODO: 1. integrate image creation in the ant build of the kernel
#TODO: 2. allow program parameters to be passed when the kernel starts to avoid the config override and remove this step from the Dockerfile

MAINTAINER alexandru.laurus@centrys.io

RUN mkdir -p /opt/aion

RUN apt-get update && apt-get install -y wget

ARG kernel_archive
RUN echo $kernel_archive

RUN wget ${kernel_archive} -O aion.tar.gz && tar -xf aion.tar.gz && cd aion

ADD . /opt/aion

WORKDIR /opt/aion

CMD [ "python", "aion-docker.py" ]

