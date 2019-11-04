# setup paths
PACK_PATH="pack"
BIN_NAME="oan.tar.bz2"

cd ${PACK_PATH}
tar xvjf ${BIN_NAME}
VER=$(./oan/aion.sh --version)
echo "OAN kernel build ver - $VER"
cp ${BIN_NAME} "oan-v${VER}-$(date +%Y-%m-%d).tar.bz2"
rm -fr oan

cd ..
