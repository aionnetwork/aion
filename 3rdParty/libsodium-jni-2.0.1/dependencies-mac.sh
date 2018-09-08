#!/bin/bash -ev

brew update

packages='libtool autoconf automake swig caskroom/versions/java8 caskroom/cask/android-sdk caskroom/cask/android-ndk gradle libsodium maven'

#https://stackoverflow.com/questions/20802320/detect-if-homebrew-package-is-installed/20802381#20802381
for pkg in ${packages}; do
    if brew list -1 | grep -q "^${pkg}\$"; then
        echo "Package '$pkg' is installed"            
        #brew upgrade $pkg
    else
        echo "Package '$pkg' is not installed"            
        brew install $pkg
    fi
done

brew upgrade ${packages} || true

#accept licenses
yes | sdkmanager --licenses

./update-android.sh &> updateandroid.log
