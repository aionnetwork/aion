 #!/bin/bash

 PACK_PATH='pack'

 cd $PACK_PATH
 BIN_ARCHIVE=$(ls aion-*tar*)
 tar xvjf $BIN_ARCHIVE
 VERSION=$(./aion/aion.sh --version)

 docker push centrys/aion-core:latest
 docker push centrys/aion-core:$VERSION

 rm -rf aion/

 cd ..


