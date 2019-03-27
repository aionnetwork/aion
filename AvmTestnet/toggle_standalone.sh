#!/bin/bash

CONFIG='config/avmtestnet/config.xml'

if [ $# -ne 1 ]
then
	echo 'USAGE:'
	echo '(turn on standalone mode) -->    ./toggle_standalone.sh ON'
	echo '(turn off standalone mode) -->   ./toggle_standalone.sh OFF'
	exit 1
fi

if [ "$1" == 'ON' ]
then

	echo 'Changing config file from non-standalone mode to standalone mode...'

	# All we are doing here is commenting out the 3 seed nodes.
	sed -i -e 's#<node>p2p://1#<!--<node>p2p://1#g' $CONFIG
	sed -i -e 's#13.91.127.35:30303</node>#13.91.127.35:30303</node>-->#g' $CONFIG

	echo '######################## FIN ########################'

elif [ "$1" == 'OFF' ]
then

	echo 'Changing config file from standalone mode to non-standalone mode...'

	# All we are doing here is uncommenting the 3 seed nodes.
	sed -i -e 's#<!--<node>p2p://1#<node>p2p://1#g' $CONFIG
	sed -i -e 's#13.91.127.35:30303</node>-->#13.91.127.35:30303</node>#g' $CONFIG

	echo '######################## FIN ########################'

else
	echo 'USAGE:'
        echo '(turn on standalone mode) -->    ./toggle_standalone.sh ON'
        echo '(turn off standalone mode) -->   ./toggle_standalone.sh OFF'
	exit 1
fi
