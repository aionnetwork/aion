#!/bin/bash -ev

jnilib=libsodiumjni.so
destlib=/usr/lib
if uname -a | grep -q -i darwin; then
  jnilib=libsodiumjni.jnilib
  destlib=/Library/Java/Extensions
  if [ ! -d $destlib ]; then
      sudo mkdir $destlib
  fi
else
  sudo ldconfig
fi
echo $jnilib
echo $destlib
echo $destlib/$jnilib 

#sudo cp /usr/local/lib/libsodium.* /usr/lib

SODIUM_LIB_DIR=../libsodium/libsodium-host/lib

gcc -I../libsodium/src/libsodium/include -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -I${JAVA_HOME}/include/darwin sodium_wrap.c -shared -fPIC -L${SODIUM_LIB_DIR} -L/usr/local/lib -L/usr/lib -lsodium -o $jnilib
sudo rm -f $destlib/$jnilib
sudo cp $jnilib $destlib
sudo cp ${SODIUM_LIB_DIR}/libsodium.so /usr/lib
