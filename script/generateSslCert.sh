#!/bin/bash

SSL_KEYSTORE='ssl_keystore'

if [ ! -d "$SSL_KEYSTORE" ]
then
    echo ""
    echo "The keystore directory $SSL_KEYSTORE does not exist!"
    exit 1
fi

if [ $# -eq 2 ]
then
    cd "$SSL_KEYSTORE"
    keytool -genkeypair -keyalg RSA -alias $1 -keystore $1.p12 -storepass $2 -validity 365000 -keysize 2048 -ext san=dns:localhost,ip:127.0.0.1 -storetype PKCS12

    if [ $? -eq 0 ]
    then
        echo "Certificate created!"
    fi
    cd ..
elif [ $# -eq 4 ]
then
    cd "$SSL_KEYSTORE"
    keytool -genkey -keyalg RSA -alias $1 -keystore $1.p12 -storepass $2 -validity 365000 -keysize 2048 -ext SAN=DNS:$3,IP:$4 -storetype PKCS12
    if [ $? -eq 0 ]
    then
        echo "Certificate created!"
    fi
    cd ..
else
    echo "Usage: ./generateSslCert.sh [cert_name] [password] [opt [host] [ip]]"
    exit 1
fi
