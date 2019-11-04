# dist stage: image for end-user distribution
FROM ubuntu:18.04 AS dist
WORKDIR /
RUN apt-get update && apt-get --no-install-recommends --yes install \
  wget \
  unzip  \
  lsb-release \
  locales
ADD oan.tar.bz2 /
WORKDIR /oan
ENV LANG C.UTF-8
ENV LC_ALL C.UTF-8
CMD ["/oan/aion.sh"]

# k8s stage: produce image for Kubernetes CI test cluster
FROM dist AS k8s
COPY k8s/custom custom
CMD ["/oan/aion.sh", "-n", "custom"]
