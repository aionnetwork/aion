#!/bin/bash

RESOURCES='../../function_testing'
PASSWORD='test'
PASSWORD_FILE='password.txt'
SANDBOX='sandbox'

echo 'Clearing and re-creating sandbox directory to launch kernel within' \
&& rm -rf $SANDBOX \
&& mkdir $SANDBOX \
&& echo 'Moving the built kernel into the sandbox' \
&& cp ./pack/aion-v*.tar.bz2 $SANDBOX \
&& cd $SANDBOX \
&& echo 'Unpacking the built kernel' \
&& tar -xvjf aion-v*.tar.bz2 \
&& echo 'Jumping inside the unpacked kernel build' \
&& cd aion \
&& mkdir lib \
&& echo 'Moving the avm.jar into the lib directory for the rpc script' \
&& cp ../../lib/avm.jar lib \
&& echo 'Fetching the resources in ./function_testing' \
&& cp $RESOURCES/auto_rpc.sh $RESOURCES/setup_account.sh $RESOURCES/test.jar $RESOURCES/test.sh . \
&& echo 'Wiping any prior avmtestnet run history' \
&& rm -rf avmtestnet \
&& echo $PASSWORD > $PASSWORD_FILE \
&& echo $PASSWORD >> $PASSWORD_FILE \
&& echo 'Setting up the premined account' \
&& cat $PASSWORD_FILE | ./setup_account.sh "$1" \
&& echo 'Clearing and re-creating the jenkins_build directory' \
&& touch log.txt

status=$?
if [ $status -ne 0 ]
then
	# clean up.
	echo 'FAILED TO BUILD'
	cd ../../
	rm -rf $SANDBOX
	exit 1
fi

echo 'Specifying the correct JDK to launch the kernel with'
export JAVA_HOME='./rt'
export PATH=$JAVA_HOME/bin:$PATH
echo 'The current JDK is:'
echo $(java -version)

echo 'Starting up the kernel'
./aion.sh -n avmtestnet > log.txt &
sleep 15
echo 'Running the tests'
./test.sh $PASSWORD

status=$?
if [ $status -ne 0 ]
then
	# clean up
	echo 'FAILED TESTS'
	cd ../../
	rm -rf $SANDBOX
	exit 1
fi

echo 'Cleaning up' \
&& cd ../../ \
&& rm -rf $SANDBOX
