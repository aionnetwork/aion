#!/bin/bash

date=`date "+%Y-%m-%d"`
TEMP_DIR='./temporary_seed_build_dir'
TEMP_CONFIG_DIR='./aion/config/avmtestnet'
BUILD='./pack'

if [ $# -ne 1 ]
then
	echo 'USAGE: ./build_seed.sh <seed number>'
	echo '    (where seed number is "1", "2", or "3")'
	exit 1
fi

function notice() {
	echo '=================================================================='
	echo 'This script assumes that the kernel has been built!'
	echo '(ie. this command has already been run: ./gradlew clean pack )'
	echo '=================================================================='
}

function build() {
	rm -rf $TEMP_DIR \
	&& mkdir $TEMP_DIR \
        && echo 'Setting up the config file for the seed node...' \
        && cp $BUILD/aion-v*.tar.bz2 $TEMP_DIR

	if [ $? -ne 0 ]
	then
		rm -rf $TEMP_DIR
		exit 1
	fi

	cd $TEMP_DIR \
        && tar -xvjf aion-v*.tar.bz2 \
        && mv $TEMP_CONFIG_DIR"/config-seed$1.xml" $TEMP_CONFIG_DIR'/config.xml' \
        && rm $TEMP_CONFIG_DIR/config-seed* \
        && echo 'Packaging the build back up...' \
        && tar -cvjSf "avmtestnet-seed$1-$date.tar.bz2" 'aion' \
	&& mv avmtestnet-seed*.tar.bz2 .. \
	&& cd .. \
	&& rm -rf $TEMP_DIR
}

if [ "$1" -eq 1 ]
then

	echo 'Producing the build for seed #1...'

	notice
	build 1

	echo '######################## FIN ########################'

elif [ "$1" -eq 2 ]
then

	echo 'Producing the build for seed #2...'

	notice
	build 2

        echo '######################## FIN ########################'
	
elif [ "$1" -eq 3 ]
then

	echo 'Producing the build for seed #3...'

	notice
	build 3

	echo '######################## FIN ########################'

else
	echo 'USAGE: ./build_seed.sh <seed number>'
        echo '    (where seed number is "1", "2", or "3")'
        exit 1
fi
