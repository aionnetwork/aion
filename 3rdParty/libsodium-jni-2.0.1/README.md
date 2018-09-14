This project is entirely maintained in my spare time. Donations are appreciated.

Bitcoin address: 1EC6j1f2sDGy9L8ma8FFfQyxt9mb9a6Xxy

Bitcoin Cash address: 1PSxB3DRCkeaZK7nSbJ1hoxbsWAXwM8Hyx

Ethereum address: 2f30c73e8d643356ebbcfee7013ccd03c05097fb

Peercoin address: PQUavHtRCLtevq75GhLCec41nvDtmM4wvf

Monero address: 48btz6nV4SjWyhDpkXrVVXAtgN6aStdnz8weMyB6qAMhhBVqiy1v3HC6XL1j7K27ZfFRhpw3Y4A4uE8o2PXMxFxY1Q5gGvW

Raiblocks address: xrb_1dxetbqeo38gcxejt8n6utajorrntbfrr1qftpw7qwarw6d8kp74fwmcuqi9

[![Build Status](https://travis-ci.org/joshjdevl/libsodium-jni.svg)](https://travis-ci.org/joshjdevl/libsodium-jni)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.joshjdevl.libsodiumjni/libsodium-jni/badge.svg)](https://oss.sonatype.org/#nexus-search;quick~libsodium)

# libsodium-jni - (Android) Java JNI binding to the Networking and Cryptography (NaCl) library 

A Java JNI binding (to allow for Java and Android integration) to [Networking and Cryptography](http://nacl.cr.yp.to/) library by [Daniel J. Bernstein](http://cr.yp.to/djb.html).

Why JNI and not JNA? JNI is much faster than JNA.

If you do use this project in your research project, please do cite this repo. Thanks!

Credits to:
* [**Libsodium**](https://github.com/jedisct1/libsodium): author [Frank Denis](https://github.com/jedisct1) and [Contributors](https://github.com/jedisct1/libsodium/graphs/contributors)
* [**Kalium**](https://github.com/abstractj/kalium): author [abstractj](https://github.com/abstractj) and [Contributors](https://github.com/abstractj/kalium/graphs/contributors)
* [**Robosodium**](https://github.com/GerardSoleCa/Robosodium): author [GerardSoleCa](https://github.com/GerardSoleCa)
* [**libstodium**](https://github.com/ArteMisc/libstodium): author [ArteMisc](https://github.com/ArteMisc)


## Installation

* Java package is under org.libsodium.jni
* Maven coordinates are in the Sonatype OSS [repository](https://oss.sonatype.org/#nexus-search;quick~libsodium)

### Android Archive (AAR)
    <dependency>
        <groupId>com.github.joshjdevl.libsodiumjni</groupId>
        <artifactId>libsodium-jni-aar</artifactId>
        <version>2.0.0</version>
        <type>aar</type>
    </dependency>

### Java Archive (JAR)

    <dependency>
        <groupId>com.github.joshjdevl.libsodiumjni</groupId>
        <artifactId>libsodium-jni</artifactId>
        <version>2.0.0</version>
        <type>jar</type>
    </dependency>

### Usage

Example [invocations](src/test/java/org/libsodium/jni/publickey/AuthenticatedEncryptionTest.java)

* import org.libsodium.jni.NaCl; (this calls System.loadLibrary("sodiumjni");)
* call NaCl.sodium(). {whatever_method_you_want}
* Note that Android [allowBackup is set to false](src/main/AndroidManifest.xml). WARNING Your application can override the allow backup, just be sure that there is no sensitive data or secrets that might be backed up. Option can be used with  `tools:replace="android:allowBackup"`

## Manual Compilation and Installation

### MacOS Manual Compilation and Installation

Install brew

Run [./dependencies-mac.sh](dependencies-mac.sh)

Run [./build-mac.sh](build-mac.sh)

### Linux Manual Compilation and Installation

Run [./dependencies-linux.sh](dependencies-linux.sh)

Run [./build-linux.sh](build-linux.sh)

## Docker Container

The docker container is available from [libsodium-jni](https://hub.docker.com/r/joshjdevl/libsodium-jni/) which is a Automated Build.

### Manual compilation and installation

Please refer to the [docker build](https://github.com/joshjdevl/libsodium-jni/blob/master/Dockerfile) for the commands used to build.

### Notes

[Docker container](https://hub.docker.com/r/joshjdevl/libsodium-jni/)

## Vagrant

A [Vagrantfile](Vagrantfile) is available for those that would like to set up a virtual machine.


## Example application
Clone the repo and import project from folder example/Sodium in Android studio (Android studio 2.1). Android studio will handle the rest.
Compile and run. Tested to emulators down to Android Version 16.

## Manual AAR usage
To use the AAR project as is (No .SO file imports needed).

It is also possible to build the AAR library yourself using the provided scripts [linux](build-linux.sh) or [mac](build-mac.sh). After building the library open module settings and add the libsodium-jni-release.aar and/or libsodium-jni-debug.aar as a dependency.

### Custom code usage
To use the library with your own custom code, skip the aar file and add

1. The native .SO libraries in your project (Create jnilibs folder and make the required changes to the gradle file)
2. Add the source code from the src folder and add your own additional code.


### Issues / Improvements / Help Seeked

libsodium-jni is currently being used in production. Feedback, bug reports and patches are always welcome.

Everything has been tested and working on ubuntu 12.04 32bit and 64 bit, macos, and Android

gpg2 --keyserver hkp://pool.sks-keyservers.net --recv-keys 4524D716

### SWIG Extensions

SWIG is used to generate the Java JNI bindings. This means that the same interface definition can be used to generate bindings for all languages supported by SWIG. The interface can be found [here](jni/sodium.i)
