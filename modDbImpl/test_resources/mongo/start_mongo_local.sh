#!/usr/bin/env bash
set -e

# This is a script which can be run to spin up a Mongo DB instance on your local machine using docker

# First, start up a docker image running Mongo
docker run --name MONGO_LOCAL -d -p 37017:27017 library/mongo:3.6 --replSet rs0

# Wait 5 seconds after this finishes for mongo to start itself
sleep 5s

# Initiatlize the replica set
docker exec -it MONGO_LOCAL mongo --eval 'rs.initiate()'

echo "Successfully started mongo"
echo "Change config.xml database section with <path>mongodb://localhost:37017</path> and <vendor>mongodb</vendor>"
