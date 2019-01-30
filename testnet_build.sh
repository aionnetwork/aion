#!/bin/sh

date=`date "+%Y-%m-%d"`
release_name="avmtestnet-$date"
avm_repo="../AVM/"
pack_dir="avm_pack"
aion_repo=""

echo "Building avm testnet release on $date..." \
&& aion_repo="$(pwd)" \
&& echo "Checking out latest version of kernel avmtestnet-build-jan30 branch..." \
&& git checkout avmtestnet-build-jan30 \
&& git pull origin avmtestnet-build-jan30 \
&& git submodule update --init --recursive \
&& cd $avm_repo \
&& echo "$(pwd)" \
&& echo "Checking out latest version of Avm master branch..." \
&& git checkout master \
&& git pull origin master \
&& echo "Building latest Avm jar..." \
&& ant \
&& cd $aion_repo \
&& echo "Building latest kernel release..." \
&& ./gradlew clean \
&& ./gradlew pack \
&& rm -rf $pack_dir \
&& mkdir $pack_dir \
&& cd $pack_dir \
&& cp ../pack/aion-v0.3.2*.tar.bz2 . \
&& echo "Unpacking the kernel release..." \
&& tar -xvjf aion-v0.3.2*.tar.bz2 \
&& cd aion/ \
&& echo "Copying in the default dApp..." \
&& cp ../../dapp.jar . \
&& echo "Grabbing the rpc script..." \
&& cp ../../rpc.sh . \
&& echo "Grabbing the jar compiler script..." \
&& cp ../../"$avm_repo"scripts/compile.sh . \
&& echo "Setting up the environment for the compile script..." \
&& mkdir lib \
&& cd lib \
&& cp ../../../"$avm_repo"dist/lib/* . \
&& echo "Cleaning up the config directory for a local node..." \
&& cd ../config/avmtestnet/ \
&& rm config-e66d*.xml \
&& cd ../../../ \
&& echo "Building the avm-integrated kernel release..." \
&& rm aion-v0.3.2* \
&& tar -cvjSf "$release_name".tar.bz2 aion \
&& rm -rf aion/ \
&& cd .. \
&& echo "Avm testnet release successfully built at: ./"$pack_dir"/"


