#!/bin/bash 

STORAGE_DIR=${HOME}/.aion
env EVMJIT="-cache=1" java -Xms4g \
    --add-opens=javafx.graphics/javafx.scene.text=ALL-UNNAMED \
    --add-opens=javafx.graphics/com.sun.javafx.text=ALL-UNNAMED \
    --add-opens=java.base/java.nio=ALL-UNNAMED \
    -cp "./lib/*:./lib/libminiupnp/*:./mod/*:aion_api/pack/modAionApi.jar:aion_api/lib/*" \
    -Dlocal.storage.dir=${STORAGE_DIR} \
    org.aion.AionGraphicalFrontEnd "$@"
