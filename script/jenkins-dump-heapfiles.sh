#!/bin/bash
# A simple script that Jenkins CI can call to print out the hs_err_pid 
# Java heap dump files.  Intention is so that if kernel crashes during
# node_test_harness, we can see the heap dump from the Jenkins UI.

if compgen -G "FunctionalTests/Tests/aion/hs_err_pid*.log" > /dev/null; then
    echo "Found Java heap dump file(s) after FunctionalTests execution."

    for file in FunctionalTests/Tests/aion/hs_err_pid*.log; do
        echo "=== $file ==="
        cat $file
    done
fi

