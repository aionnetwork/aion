#!/bin/bash

cd "$(dirname $(realpath $0))"

KERVER=$(uname -r | grep -o "^4\.")

if [ "$KERVER" != "4." ]; then
  echo "Warning! The linux kernel must be greater than or equal to version 4."
fi

HW=$(uname -m)

if [ "$HW" != "x86_64" ]; then
  echo "Warning! Aion blockchain platform must be running on 64 bits architecture"
fi

DIST=$(lsb_release -i | grep -o "Ubuntu")

if [ "$DIST" != "Ubuntu" ]; then
  echo "Warning! Aion blockchain is fully compatible with Ubuntu distribution. Your current system is not Ubuntu distribution. It may have some issues."
fi

MAJVER=$(lsb_release -r | grep -o "[0-9][0-9]" | sed -n 1p)
if [ "$MAJVER" -lt "16" ]; then
  echo "Warning! Aion blockchain is fully compatible with Ubuntu version 16.04. Your current system is older than Ubuntu 16.04. It may have some issues."
fi

ARG=$@

#if [ "$ARG" == "--close" ]; then
#    PID=$(<./tmp/aion.pid)
#    kill -2 $PID
#    rm -r ./tmp
#    exit 0
#fi

# add execute permission to rt
chmod +x ./rt/bin/*

#env EVMJIT="-cache=1" ./rt/bin/java -Xms4g \
#	-cp "./lib/*:./lib/libminiupnp/*:./mod/*" org.aion.Aion "$@" &


# To kill through command line:
# ps aux | egrep "aion.sh"

wait=300	# sec
sample=30	# sec
tolerance=60 	# sec
threadRate=3 	# rate
#wait=30
#sample=1
#tolerance=1
#threadRate=20

config=config/config.xml
logging=$(egrep -o "log-file.*log-file" $config | cut -d">" -f2 | cut -d"<" -f1)
logpath=$(egrep -o "log-path.*log-path" $config | cut -d">" -f2 | cut -d"<" -f1)
file=$logpath/aionCurrentLog.dat

noInterrupt=true
countRebounce=0
running=false
lastBoot=0

trap "exit" INT TERM
trap "interrupt" EXIT
function interrupt() {

	# Interrupts the Aion kernel and awaits shutdown complete
	if $running; then
	  kill $kPID
	  temp=$(top -n1 -p $kPID | egrep -o "$kPID")
	  while [[ $temp -eq $kPID ]] ; do
	    sleep 2s
	    temp=$(top -n1 -p $kPID | egrep -o "$kPID")
	  done
	  running=false
	  watching=false
	  noInterrupt=false
	fi

	# Removes remnant processes accessing kernel logfile
	if $logging; then
	  temp=$(lsof $file | egrep "java" | cut -c 9-13)
	  remnants=($temp)
	  for ((i=0; i<${#remnants[@]}; ++i)); do
	    kill ${remnants[i]}
	  done
	fi

	# Interrupts the watchguard (current process)
	kill $$

}

# Keep executing aion kernel until interrupted
while $noInterrupt; do

	# Reboot timer
	newBoot=$(date +"%s")
	let "duration=$newBoot-$lastBoot"
	if [[ $duration -lt $wait ]]; then
	  let "sleep=$wait-$duration"
	  echo "[Reboot Timer: $sleep]"
	  sleep $sleep
	fi
	lastBoot=$newBoot
	echo

	# Execute Java kernel
	env EVMJIT="-cache=1" ./rt/bin/java -Xms4g \
		-cp "./lib/*:./lib/libminiupnp/*:./mod/*" org.aion.Aion "$@" &
	kPID=$!
	running=true
	watching=true
	checkRate=0
	tPrev=0

	# Watchguard
	while $watching; do

		sleep $sample

		# [1] Log timestamp (last 60 sec) AND [2] PID process state ZOMBIE/DEAD
		if $logging; then
		  		
		  last=$(stat $file | egrep "Modify\:" | cut -d" " -f3 | cut -d"." -f1)
		  lastUTC=$(date --date="$last" +"%s")
		  nowUTC=$(date +"%s")
		  if [[ $lastUTC -lt $((nowUTC-$tolerance)) ]] || (ps $kPID | cut -c 16-22 | egrep -v "STAT" | egrep -q "Z"); then
		    echo "## KERNEL DEAD FOR $tolerance SEC ##"
		    watching=false
		  fi
		fi

		# [1] Thread runtime (last 60 sec) OR [2] PID thread state BLOCKED
		if [ $checkRate -eq $threadRate ]; then
		  let "duration=$sample*$threadRate"
		  temp='p2p-in sync-ib'
		  threads=($temp)
		  checkRate=0
		  for ((i=0; i<${#threads[@]}; ++i)); do
		    tTime=$(top -n1 -p $kPID -H | egrep -o "[0-9]{2}\.[0-9]{2} ${threads[i]}" | cut -d" " -f1)
		    tState=$(jstack -l $kPID | egrep -A1 "${threads[i]}" | egrep -o "State.*" | cut -d" " -f2)
		    if [[ $tTime == ${tPrev[i]} ]] || [[ $tState == "BLOCKED" ]]; then 
		      echo "## ${threads[i]} THREAD DEAD ##"
		      (jstack -l $kPID | egrep -A1 "${threads[i]}") > threadDump_$countRebounce.txt
		      watching=false
		    fi
		    tPrev[i]=$tTime
		  done
		else
		  ((checkRate++))
		fi

	done
	
	# Shutsdown Aion kernel
	echo "## Killing Kernel ##"
	kill $kPID
	temp=$(top -n1 -p $kPID | egrep -o "$kPID")
	while [[ $temp -eq $kPID ]] ; do
	  sleep 2s
	  temp=$(top -n1 -p $kPID | egrep -o "$kPID")
	done
	running=false

	((countRebounce++))
	echo
	echo "############################## REBOUNCE COUNT [$countRebounce] ##############################"

done
