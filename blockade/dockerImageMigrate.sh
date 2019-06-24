#!/bin/bash
echo "user:" $1
echo "password:" $2
echo "ip:" $3

echo "Step1. save docker image to tar file"
docker save aionnetwork/aion:latest > aionjimage.tar
echo "done!"

echo "Step2. copy docker image to the deploy server"
scp aionjimage.tar $1@$3:~
echo "done!"

echo "Step3. import the exported docker image"
sshpass -p $2 ssh $1@$3 docker import aionjimage.tar aionnetwork/aion:latest
echo "done!"

echo "Step4. remove the exported docker image in local"
rm aionjimage.tar
echo "all done!"
