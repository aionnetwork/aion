#!/bin/bash 

#env EVMJIT="-cache=1" java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5055 -Xms4g  \
STORAGE_DIR=${HOME}/.aion
env EVMJIT="-cache=1" java -Xms4g  \
        -cp "./lib/*:./lib/libminiupnp/*:./mod/*:aion_api/pack/modAionApi.jar:aion_api/lib/*" -Dlocal.storage.dir=${STORAGE_DIR} org.aion.AionGraphicalFrontEnd "$@"
