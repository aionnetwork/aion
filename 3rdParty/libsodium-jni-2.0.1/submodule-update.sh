#!/bin/bash -ev

set -ev

. ./setenv.sh

rm -rf libsodium

git clone https://github.com/jedisct1/libsodium
#git submodule sync 
#git submodule update --remote --merge
#git submodule update 

pushd libsodium

git checkout c398a51e2172098096fe11ad0dbea57bd6253290
#git reset --hard c398a51e2172098096fe11ad0dbea57bd6253290
#git pull
popd
