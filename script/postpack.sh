# setup paths
PACK_PATH="pack"
BIN_NAME="aion.tar.bz2"

cd ${PACK_PATH}
tar xvjf ${BIN_NAME}
VER=$(./aion/aion.sh --version)
echo "Aion kernel build ver - $VER"
cp ${BIN_NAME} "aion-v${VER}-$(date +%Y-%m-%d).tar.bz2"
rm -fr aion

cd ..
