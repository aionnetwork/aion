In the aion project, we use some pre-compiled jar as our dependency. You can download these binery from the following link:

Java dependency library

BIP39-0.1.9.jar
https://mvnrepository.com/artifact/io.github.novacrypto/BIP39/0.1.9

byte-buddy-1.8.17.jar
https://mvnrepository.com/artifact/net.bytebuddy/byte-buddy/1.8.17

byte-buddy-agent-1.8.17.jar
https://mvnrepository.com/artifact/net.bytebuddy/byte-buddy-agent/1.8.17

commons-codec-1.10.jar
https://mvnrepository.com/artifact/commons-codec/commons-codec/1.10

commons-collections4-4.0.jar
https://mvnrepository.com/artifact/org.apache.commons/commons-collections4/4.0

commons-lang3-3.4.jar
https://mvnrepository.com/artifact/org.apache.commons/commons-lang3/3.4

core-3.3.3.jar
https://mvnrepository.com/artifact/com.google.zxing/core/3.3.3

diffutils-1.3.0.jar
https://mvnrepository.com/artifact/com.googlecode.java-diff-utils/diffutils/1.3.0

google-java-format-1.5-all-deps.jar
https://mvnrepository.com/artifact/com.google.googlejavaformat/google-java-format/1.5

gson-2.7.jar
https://mvnrepository.com/artifact/com.google.code.gson/gson/2.7

guava-25.1-jre.jar
https://mvnrepository.com/artifact/com.google.guava/guava/25.1-jre

h2-mvstore-1.4.196.jar
https://mvnrepository.com/artifact/com.h2database/h2-mvstore/1.4.196

hamcrest/hamcrest-all-1.3.jar
https://mvnrepository.com/artifact/org.hamcrest/hamcrest-all/1.3

hamcrest/hamcrest-core-1.3.jar
https://mvnrepository.com/artifact/org.hamcrest/hamcrest-core/1.3

jboss-logging-3.3.0.Final.jar
https://mvnrepository.com/artifact/org.jboss.logging/jboss-logging/3.3.0.Final

jctools-core-1.2.1.jar
https://mvnrepository.com/artifact/org.jctools/jctools-core/1.2.1

jsr305-3.0.2.jar
https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305/3.0.2

junit_4/junit-4.12.jar
https://mvnrepository.com/artifact/junit/junit/4.12

junit_4/system-rules-1.16.0.jar
https://mvnrepository.com/artifact/com.github.stefanbirkner/system-rules/1.16.0

JUnitParams-1.1.1.jar
https://mvnrepository.com/artifact/pl.pragmatists/JUnitParams/1.1.1

leveldb-api-0.9.jar
https://mvnrepository.com/artifact/org.iq80.leveldb/leveldb-api/0.9

leveldbjni-all-1.18.3.jar
https://mvnrepository.com/artifact/org.ethereum/leveldbjni-all/1.18.3

libJson.jar
aion/3rdParty/libJson
or you can find the source code from http://www.json.org/

libminiupnp/miniupnpc_linux.jar
libminiupnp/libminiupnpc.so
aion/3rdParty/libminiupnpc (please read build_upnp_for_aion.txt inside the project folder before you build the jar)
or you can find the source code from https://github.com/miniupnp/miniupnp

libnsc.jar
aion/3rdParty/libnsc
or you can find the source code from http://www.bouncycastle.org

libnzmq.jar
aion/3rdParty/libnzmq
or you can find the source code from http://zeromq.github.com/jzmq

logback-classic-1.2.3.jar
https://mvnrepository.com/artifact/ch.qos.logback/logback-classic/1.2.3

logback-core-1.2.3.jar
https://mvnrepository.com/artifact/ch.qos.logback/logback-core/1.2.3

mockito-core-2.21.0.jar
https://mvnrepository.com/artifact/org.mockito/mockito-core/2.21.0

nanohttpd-2.3.1-with-deps.jar
https://mvnrepository.com/artifact/org.nanohttpd/nanohttpd/2.3.1

netlib-java-0.9.3.jar
https://mvnrepository.com/artifact/netlib/netlib-java/0.9.3

objenesis-2.6.jar
https://mvnrepository.com/artifact/org.objenesis/objenesis/2.6

openjfx-monocle-jdk-9+181.jar
https://mvnrepository.com/artifact/org.testfx/openjfx-monocle/jdk-9+181

protobuf-java-3.5.0.jar
https://mvnrepository.com/artifact/com.google.protobuf/protobuf-java/3.5.0

richtextfx-fat-0.9.0.jar
https://github.com/FXMisc/RichTextFX/releases/tag/v0.9.0

rocksdbjni-5.11.3.jar
https://mvnrepository.com/artifact/org.rocksdb/rocksdbjni/5.11.3

SHA256-0.0.1.jar
https://mvnrepository.com/artifact/io.github.novacrypto/SHA256/0.0.1

slf4j-api-1.7.25.jar
https://mvnrepository.com/artifact/org.slf4j/slf4j-api/1.7.25

testfx-core-4.0.13-alpha.jar
https://mvnrepository.com/artifact/org.testfx/testfx-core/4.0.13-alpha

testfx-junit-4.0.13-alpha.jar
https://mvnrepository.com/artifact/org.testfx/testfx-junit/4.0.13-alpha

ToRuntime-0.9.0.jar
https://mvnrepository.com/artifact/io.github.novacrypto/ToRuntime/0.9.0

truth-0.42.jar
https://mvnrepository.com/artifact/com.google.truth/truth/0.42

undertow-core-2.0.10.Final.jar
https://mvnrepository.com/artifact/io.undertow/undertow-core/2.0.10.Final

xnio-api-3.3.8.Final.jar
https://mvnrepository.com/artifact/org.jboss.xnio/xnio-api/3.3.8.Final

xnio-nio-3.3.8.Final.jar
https://mvnrepository.com/artifact/org.jboss.xnio/xnio-nio/3.3.8.Final



Native Library

libblake2b.so
aion/modCrypto/src_native
will be built when executing the ant build

equiMiner.so
aion/modAionImpl/src/org/aion/equihash/native
will be built when executing the ant build

libevmjit.so
aion/aion_fastvm/libevmjit
will be built when executing the release.sh in the aion_fastvm repo or execute the ant full_build in the aion repo

libfastvm.so
aion/aion_fastvm/jni
will be built when executing the release.sh in the aion_fastvm repo or execute the ant full_build in the aion repo

libsodiumjni.so
libsodium.so
https://github.com/joshjdevl/libsodium-jni

solc
aion/aion_fastvm/solidity
will be built when executing the release.sh in the aion_fastvm repo or execute the ant full_build in the aion repo

libzmq.so.5
linux kernel package, can be obtained by apt install libzmq5
https://github.com/zeromq/jzmq
