#!/bin/bash -ev

. ./setenv.sh

pushd libsodium
./configure --disable-soname-versions --prefix=`pwd`/libsodium-host --libdir=`pwd`/libsodium-host/lib
make clean
NPROCESSORS=$(getconf NPROCESSORS_ONLN 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null)
PROCESSORS=${NPROCESSORS:-3}
make -j${PROCESSORS}
make install
popd
