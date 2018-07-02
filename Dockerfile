FROM openjdk:9

MAINTAINER alexandru.laurus@centrys.io

RUN mkdir -p /opt/aion

ARG kernel_path

COPY ${kernel_path} /opt/aion/

WORKDIR /opt/aion

CMD [ "python", "aion-docker.py" ]
