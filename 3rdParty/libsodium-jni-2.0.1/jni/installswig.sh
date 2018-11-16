#!/bin/bash

set -e

if uname -a | grep -q -i darwin; then
  brew install swig
else
    rm -rf swig*
    VERSION=3.0.12
    test -e "swig-${VERSION}.tar.gz" || wget --quiet http://prdownloads.sourceforge.net/swig/swig-${VERSION}.tar.gz
    tar -xf swig-${VERSION}.tar.gz
    cd swig-${VERSION}
    ./configure --quiet
    make --quiet -j 5
    sudo make --quiet install
fi

