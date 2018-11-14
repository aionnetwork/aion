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
if [ ! -d "$JAVAFX_PATH" ]; then
  wget -c http://gluonhq.com/download/javafx-11-jmods-linux -O openjfx-11_linux-x64_bin-jmods.zip
  unzip openjfx-11_linux-x64_bin-jmods.zip -d $PACK_PATH
  mv "${PACK_PATH}/${JAVAFX_VER}" $JAVAFX_PATH
fi

# generate aion runtime
if [ ! -d "$JDK_RT" ]; then
  $JDK_PATH/bin/jlink --module-path $JAVAFX_PATH:$JDK_PATH/jmods --add-modules java.base,java.xml,java.logging,java.management,jdk.unsupported,javafx.graphics,javafx.controls,javafx.base,jdk.sctp,javafx.fxml,javafx.swing \
  --output $JDK_RT --compress 2 --no-man-pages
  cp $JDK_PATH/bin/jstack $JDK_RT/bin
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
