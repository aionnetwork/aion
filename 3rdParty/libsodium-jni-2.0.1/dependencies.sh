#!/bin/bash -ev

if [[ $TRAVIS_OS_NAME == 'osx' ]]; then
    ./dependencies-mac.sh
else
    . ./setenv.sh
    ./dependencies-linux.sh    
    #./android-emulator.sh
fi
