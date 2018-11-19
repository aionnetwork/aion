#!/bin/bash

PACK_PATH="pack"
JDK_PATH="${PACK_PATH}/jdk"
JDK_RT="${PACK_PATH}/rt"
WEB3JS_PATH="${PACK_PATH}/web3"
CONFIG_PATH="${PACK_PATH}/config"
DOCS_PATH="${PACK_PATH}/docs"
API_PATH="${PACK_PATH}/clientAPI"
SCRIPT_PATH="${PACK_PATH}/script"
JDK_VER="11.0.1"
JDK_TYPE="openjdk"
JAVAFX_PATH="${PACK_PATH}/javafx"
JAVAFX_VER="javafx-jmods-11"

if [ ! -d "$PACK_PATH" ]; then
  mkdir $PACK_PATH
fi

# download jre11 if can't find the jdk env
if [ ! -d "$JDK_PATH" ]; then
  wget -c  https://download.java.net/java/GA/jdk11/13/GPL/${JDK_TYPE}-${JDK_VER}_linux-x64_bin.tar.gz
  tar -xf "${JDK_TYPE}-${JDK_VER}_linux-x64_bin.tar.gz" -C $PACK_PATH
  mv "${PACK_PATH}/jdk-${JDK_VER}" $JDK_PATH
fi

# download javafx if can't find the javafx env
if [ "$noGui" != "true" ] || [ ! -d "$JAVAFX_PATH" ]; then
  wget -c http://gluonhq.com/download/javafx-11-jmods-linux -O openjfx-11_linux-x64_bin-jmods.zip
  unzip openjfx-11_linux-x64_bin-jmods.zip -d $PACK_PATH
  mv "${PACK_PATH}/${JAVAFX_VER}" $JAVAFX_PATH
fi

module_path=$JDK_PATH/jmods 
add_modules="java.base,java.xml,java.logging,java.management,jdk.unsupported,jdk.sctp"
# generate aion runtime
if [ "$noGui" != "true" ]; then
    module_path="$module_path:$JAVAFX_PATH/jmods"
    add_modules="$add_modules,javafx.graphics,javafx.controls,javafx.base,javafx.fxml,javafx.swing"
fi
if [ ! -d "$JDK_RT" ]; then
  $JDK_PATH/bin/jlink --module-path $module_path --add-modules $add_modules \
  --output $JDK_RT --compress 2 --no-man-pages
  cp $JDK_PATH/bin/jstack $JDK_RT/bin
fi

# download the web3.js if can't find the web3.js env
AION_WEB3_TAR="aion_web3_0.0.4_2018-07-17.tar.gz"
if [ ! -d "$WEB3JS_PATH" ]; then
  wget -nc "https://github.com/aionnetwork/aion_web3/releases/download/0.0.4/${AION_WEB3_TAR}" -O "${PACK_PATH}/${AION_WEB3_TAR}"

  mkdir $WEB3JS_PATH
  tar -xf "${PACK_PATH}/${AION_WEB3_TAR}" -C $WEB3JS_PATH
fi

# copy the config files if can't find the config env
if [ ! -d "$CONFIG_PATH" ]; then
  mkdir $CONFIG_PATH
  cp -r ./modBoot/resource/** $CONFIG_PATH
fi

# copy the doc files if can't find the docs env
if [ ! -d "$DOCS_PATH" ]; then
  mkdir $DOCS_PATH
  cp -r ./docs/** $DOCS_PATH
fi

# copy the script files if can't find the script env
if [ ! -d "$SCRIPT_PATH" ]; then
  mkdir $SCRIPT_PATH
  cp -r ./script/generateSslCert.sh $SCRIPT_PATH
  cp -r ./script/nohup_wrapper.sh $SCRIPT_PATH
fi

# copy the client API files if can't find the client API env
if [ ! -d "$API_PATH" ]; then
  mkdir $API_PATH
  cp aion_api/pack/libAionApi-*.tar.gz $API_PATH
fi

cp aion_api/pack/Java-API*-doc.zip $DOCS_PATH
