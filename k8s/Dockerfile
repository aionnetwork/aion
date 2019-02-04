# builder stage
FROM ubuntu:18.04

RUN apt-get update
RUN apt-get --no-install-recommends --yes install \
  wget \
  unzip  \
  lsb-release \
  libedit2 \
  libffi6 \
  libtinfo5 \
  bzip2 \
  jq \
  locales

WORKDIR /

# Copy latest build
COPY aion.tar.bz2 aion.tar.bz2

# Unpack
RUN tar -xvf aion.tar.bz2

# Delete source (Reduce image size)
RUN rm -rf aion.tar.bz2

WORKDIR /aion

# Copy config file for the test network
COPY custom custom

ENV LANG C.UTF-8
ENV LC_ALL C.UTF-8

# Start aion on image start
CMD ["/aion/aion.sh", "-n", "custom"]
