#!/bin/bash 

#env EVMJIT="-cache=1" java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5055 -Xms4g  \

env EVMJIT="-cache=1" java -Xms4g  \
        -cp "./lib/*:./lib/libminiupnp/*:./mod/*:aion_api/pack/modAionApi.jar:aion_api/lib/*" org.aion.AionGraphicalFrontEnd "$@"
