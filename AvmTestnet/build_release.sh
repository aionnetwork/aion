#!/bin/bash

date=`date "+%Y-%m-%d"`
BUILD='./pack'
TEMP_DIR='./temporary_release_build_dir'

echo ' '
echo '=================================================================='
echo 'This script assumes that the kernel has been built!'
echo '(ie. this command has already been run: ./gradlew clean pack )'
echo '=================================================================='
echo ' '

rm -rf $TEMP_DIR \
&& mkdir $TEMP_DIR \
&& echo 'Unpacking the built kernel' \
&& cp $BUILD/aion-v*.tar.bz2 $TEMP_DIR

if [ $? -ne 0 ]
then
	rm -rf $TEMP_DIR
	exit 1
fi

cd $TEMP_DIR \
&& tar -xvjf aion-v*.tar.bz2

if [ $? -ne 0 ]
then
	cd ..
	rm -rf $TEMP_DIR
	exit 1
fi

cd ./aion \
&& echo ' ' \
&& echo 'Adding the default dApp to the build' \
&& cp '../../dapp.jar' . \
&& echo 'Adding the rpc.sh script to the build' \
&& cp '../../rpc.sh' . \
&& echo 'Adding the compile.sh script to the build' \
&& cp '../../compile.sh' . \
&& echo 'Setting up the resources required by the compile.sh & rpc.sh scripts' \
&& mkdir lib \
&& cp ../../lib/org-aion-avm-userlib.jar lib \
&& cp ../../lib/org-aion-avm-api.jar lib \
&& cp ../../lib/avm.jar lib \
&& echo 'Removing the seed config files' \
&& rm config/avmtestnet/config-seed* \
&& cd .. \
&& rm aion-v*.tar.bz2 \
&& echo ' ' \
&& echo 'Packaging the build back up' \
&& tar -cvjSf "avmtestnet-$date.tar.bz2" aion \
&& mv avmtestnet-$date.tar.bz2 .. \
&& cd .. \
&& rm -rf $TEMP_DIR \
&& echo ' ' \
&& echo '######################## FIN ########################'
