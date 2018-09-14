#!/bin/bash

export GRADLE_OPTS="-Dorg.gradle.native=false -Dorg.gradle.daemon=true"
export NDK_VERSION=r17
export ANDROID_SDK_VERSION=r26.0.2
export CLANG_VERSION=5.0

if uname -a | grep -q -i darwin; then
    export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home)}
    export ANDROID_NDK=/usr/local/share/android-ndk
    export ANDROID_NDK_HOME=/usr/local/share/android-ndk
    export ANDROID_SDK=/usr/local/share/android-sdk
    export ANDROID_HOME=/usr/local/share/android-sdk
else
    export MAVEN_VERSION=3.5.3
    export GRADLE_VERSION=4.4
    export NDK_TOOLCHAIN_PLATFORM=16
    export NDK_TOOLCHAIN_ARCHITECTURE=arm
    export NDK_ROOT=`pwd`/installs/android-ndk-${NDK_VERSION}
    export PATH=${NDK_ROOT}:$PATH
    #export JAVA_HOME=/usr/lib/jvm/java-8-oracle
    export ANDROID_NDK_HOME=${NDK_ROOT}
    export ANDROID_HOME=`pwd`/installs/android-sdk
    export PATH=`pwd`/installs/apache-maven-${MAVEN_VERSION}:`pwd`/installs/gradle-${GRADLE_VERSION}/bin:`pwd`/installs/android-toolchain/bin:$PATH
fi

#export PATH=/usr/lib/llvm-${CLANG_VERSION}/bin:$PATH
export PATH=${ANDROID_HOME}/emulator:$ANDROID_HOME/bin:${ANDROID_HOME}/tools:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools:${ANDROID_NDK_HOME}:$PATH

export LIBSODIUM_FULL_BUILD="true"
