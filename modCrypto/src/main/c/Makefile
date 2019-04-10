AION_HOME=../../../..

JAVA_HOME=/usr/lib/jvm/jdk-11.0.1

SONAME=libblake2b.so
TARGET=${AION_HOME}/native/linux/blake2b/$(SONAME)

CC=gcc
CFLAGS=-O3 -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux -shared -fpic  -Wall -Wextra -msse4.2 -mpclmul -maes
LFLAGS=-Wl,-soname=$(SONAME) -shared

all:
	$(CC) $(CFLAGS) \
blake2b.c \
org_aion_crypto_hash_Blake2bNative.c \
$(LFLAGS) -o ${TARGET}

clean:
	rm -rf ${TARGET}
