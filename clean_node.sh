#!/bin/bash

# Kill the running kernel
pid="$(ps -aux | grep "avmtestnet" | grep "rt/bin/java" | awk '{print $2}')"
kill -9 $pid &>/dev/null

# clean the old build
rm aion-v0.3.2*.tar.bz2
rm -rf aion
