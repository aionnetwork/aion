#!/bin/bash

if [ ! -d "sslKeystore" ]
then
    echo ""
    echo "The sslKeystore directory does not exist!"
    exit 1
fi

if [ $# -eq 2 ]
then
    cd sslKeystore/
    keytool -genkey -keyalg RSA -alias $1 -keystore $1.jks -storepass $2 -validity 365 -keysize 2048 -ext SAN=DNS:localhost,IP:127.0.0.1
    if [ $? -eq 0 ]
    then
        echo "Certificate created!"
    fi
    cd ..
elif [ $# -eq 4 ]
then
    cd sslKeystore/
    keytool -genkey -keyalg RSA -alias $1 -keystore $1.jks -storepass $2 -validity 365 -keysize 2048 -ext SAN=DNS:$3,IP:$4
    if [ $? -eq 0 ]
    then
        echo "Certificate created!"
        cd ..
    fi
else
    echo "Usage: ./create_cert.sh [cert_name] [password] [opt [host] [ip]]"
    exit 1
fi
