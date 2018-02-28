# setup paths
PACK_PATH="pack"
BIN_NAME="aion.tar.bz2"

GITVER=$(git log -1 --format="%h")

cd ${PACK_PATH}
tar xvjf ${BIN_NAME}
VER=$(./aion/aion.sh --version)
mv ${BIN_NAME} "aion-v${VER}-$(date +%Y-%m-%d)-${GITVER}.tar.bz2"
rm -fr aion
