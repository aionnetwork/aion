# setup paths
PACK_PATH="pack"
JDK_PATH="${PACK_PATH}/jdk"
JDK_RT="${PACK_PATH}/rt"
WEB3JS_PATH="${PACK_PATH}/web3"
CONFIG_PATH="${PACK_PATH}/config"
DOCS_PATH="${PACK_PATH}/docs"

if [ ! -d "$PACK_PATH" ]; then
  mkdir $PACK_PATH
fi

# download jre9 if can't find the jre env
if [ ! -d "$JDK_PATH" ]; then
  wget -nc --no-check-certificate --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie" "http://download.oracle.com/otn-pub/java/jdk/9.0.4+11/c2514751926b4512b076cc82f959763f/jdk-9.0.4_linux-x64_bin.tar.gz" -O $PACK_PATH/jdk-9.0.4_linux-x64_bin.tar.gz
  tar -xf $PACK_PATH/jdk-9.0.4_linux-x64_bin.tar.gz -C $PACK_PATH
  mv $PACK_PATH/jdk-9.0.4 $JDK_PATH
fi

# generate aion runtime
if [ ! -d "$JDK_RT" ]; then
  $JDK_PATH/bin/jlink --module-path $JDK_PATH/jmods --add-modules java.base,java.xml,java.logging,java.management --output $JDK_RT
fi

# download the web3.js if can't find the web3.js env
AION_WEB3_TAR="aion_web3_0.0.2_2018-02-05.tar.gz"
if [ ! -d "$WEB3JS_PATH" ]; then
  wget -nc "https://github.com/aionnetwork/aion_web3/releases/download/v0.0.2/aion_web3_0.0.2_2018-02-05.tar.gz" -O $PACK_PATH/aion_web3_0.0.2_2018-02-05.tar.gz
  mkdir $WEB3JS_PATH
  tar -xf $PACK_PATH/aion_web3_0.0.2_2018-02-05.tar.gz -C $WEB3JS_PATH
fi

# copy the config files if can't find the config env
if [ ! -d "$CONFIG_PATH" ]; then
  mkdir $CONFIG_PATH
  cp ./modBoot/resource/** $CONFIG_PATH
  mv $CONFIG_PATH/testnet.json $CONFIG_PATH/genesis.json
fi

# copy the doc files if can't find the docs env
if [ ! -d "$DOCS_PATH" ]; then
  mkdir $DOCS_PATH
  cp -r ./docs/** $DOCS_PATH
fi
