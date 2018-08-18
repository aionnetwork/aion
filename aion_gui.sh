#!/bin/bash 
JAVA_CMD=java
if [ -d "./rt" ]; then
        JAVA_CMD="./rt/bin/java"
elif [ -d "./pack/rt" ]; then
        JAVA_CMD="./pack/rt/bin/java"
elif [ -d "$JAVA_HOME" ]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
fi

STORAGE_DIR=${HOME}/.aion
env EVMJIT="-cache=1" $JAVA_CMD -Xms4g \
    --add-opens=javafx.graphics/javafx.scene.text=ALL-UNNAMED \
    --add-opens=javafx.graphics/com.sun.javafx.text=ALL-UNNAMED \
    --add-opens=java.base/java.nio=ALL-UNNAMED \
    -cp "./lib/*:./lib/libminiupnp/*:./mod/*:aion_api/pack/modAionApi.jar:aion_api/lib/*" \
    -Dlocal.storage.dir=${STORAGE_DIR} \
    org.aion.AionGraphicalFrontEnd "$@"
