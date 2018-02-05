#!/bin/bash
# bash generate random alphanumeric string formatted for Aion node ID
#

random-string()
{
    cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w ${1:-32} | head -n 1
}

text=`random-string 32`
echo ${text:0:8}-${text:8:4}-${text:12:4}-${text:16:4}-${text:16:12}

