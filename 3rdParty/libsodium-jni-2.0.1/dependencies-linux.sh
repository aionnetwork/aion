#!/bin/bash -ev

set -ev

. ./setenv.sh

sudo apt-get -qq update
sudo apt-get -y -qq install lsb-release

lsb_release -a

sudo apt-get -qq update && sudo apt-get -y -qq install python-software-properties software-properties-common
sudo add-apt-repository -y "deb http://archive.ubuntu.com/ubuntu $(lsb_release -sc) universe"
sudo apt-get -qq update

sudo add-apt-repository ppa:git-core/ppa -y
sudo apt-get -qq update

sudo add-apt-repository -y ppa:saiarcot895/myppa
sudo apt-get -qq update
echo debconf apt-fast/maxdownloads string 16 | sudo debconf-set-selections
echo debconf apt-fast/dlflag boolean true | sudo debconf-set-selections
echo debconf apt-fast/aptmanager string apt-get | sudo debconf-set-selections
sudo apt-get -y -qq install apt-fast 

sudo apt-fast -qq update

sudo apt-fast -y -qq install wget git autoconf autoconf automake build-essential autogen libtool gettext-base gettext vim bzip2 libpcre3-dev libpcre++-dev pkg-config unzip htop ntp
#gradle building issues
sudo apt-fast -y -qq install gcc-multilib lib32z1
sudo add-apt-repository --enable-source ppa:webupd8team/java -y 
sudo apt-fast -qq update 
echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections
sudo apt-fast -y -qq install oracle-java8-installer maven 

#http://apt.llvm.org/
wget -O - http://apt.llvm.org/llvm-snapshot.gpg.key | sudo apt-key add -
sudo apt-add-repository -y --enable-source "deb http://apt.llvm.org/$(lsb_release -sc)/ llvm-toolchain-$(lsb_release -sc)-${CLANG_VERSION} main"
#sudo apt-add-repository "deb-src http://apt.llvm.org/$(lsb_release -sc)/ llvm-toolchain-$(lsb_release -sc)-${CLANG_VERSION} main"
sudo add-apt-repository ppa:ubuntu-toolchain-r/test -y
sudo apt-fast -qq update
sudo apt-fast -y install clang-${CLANG_VERSION} lldb-${CLANG_VERSION}

mkdir -p ./installs
pushd ./installs
test -e "android-ndk-${NDK_VERSION}-linux-x86_64.zip" || wget --quiet http://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux-x86_64.zip
chmod 755 android-ndk-${NDK_VERSION}-linux-x86_64.zip
test -e "android-ndk-${NDK_VERSION}" || unzip -qq "android-ndk-${NDK_VERSION}"-linux-x86_64.zip
test -e `pwd`/android-toolchain || ${NDK_ROOT}/build/tools/make_standalone_toolchain.py -v --api=${NDK_TOOLCHAIN_PLATFORM} --arch=${NDK_TOOLCHAIN_ARCHITECTURE} --install-dir=`pwd`/android-toolchain --stl=libc++ #--unified-headers
popd

pushd ./installs
test -e "apache-maven-${MAVEN_VERSION}-bin.tar.gz" || wget --quiet http://www-us.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz
test -e "apache-maven-${MAVEN_VERSION}" || tar -xf apache-maven-${MAVEN_VERSION}-bin.tar.gz
popd

pushd jni
./installswig.sh
popd

./download-gradle.sh 

./update-android.sh
