#!/bin/sh

# TODO redirect to sane location
nohup $* > /tmp/aion-kernel-nohup &
echo $! 
