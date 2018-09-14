FROM ubuntu:xenial
MAINTAINER joshjdevl < joshjdevl [at] gmail {dot} com>
ENV DEBIAN_FRONTEND noninteractive

RUN apt-get -qq update
RUN apt-get -y -qq install sudo rng-tools lsb-release
WORKDIR /installs/libsodium-jni
ADD . /installs/libsodium-jni
ADD settings.xml ~/.m2/settings.xml

RUN ./dependencies.sh 
RUN ./build-linux.sh
