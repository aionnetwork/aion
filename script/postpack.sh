# setup paths
PACK_PATH="pack"
BIN_NAME="aion.tar.bz2"

cd ${PACK_PATH}
tar xvjf ${BIN_NAME}
VER=$(./aion/aion.sh -v)
echo "Aion kernel build ver - $VER"
mv ${BIN_NAME} "aion-v${VER}-$(date +%Y-%m-%d).tar.bz2"
rm -fr aion

cd ..
git checkout -- ./modAionImpl/src/org/aion/zero/impl/AionHub.java
