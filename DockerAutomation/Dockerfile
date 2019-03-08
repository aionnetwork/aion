# dist stage: image for end-user distribution
FROM ubuntu:18.04 AS dist
WORKDIR /
RUN apt-get update && apt-get --no-install-recommends --yes install \
  wget \
  unzip  \
  lsb-release \
  locales
ADD aion.tar.bz2 /
WORKDIR /aion
ENV LANG C.UTF-8
ENV LC_ALL C.UTF-8
CMD ["/aion/aion.sh"]

# k8s stage: produce image for Kubernetes CI test cluster
FROM dist AS k8s
COPY k8s/custom custom
CMD ["/aion/aion.sh", "-n", "custom"]
