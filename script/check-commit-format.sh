#!/bin/bash

java_files="$(git show --name-only | grep '^mod.*java$' | grep -v 'modApiServer.src.org.aion.api.server.pb.Message.java' )"
jar="$(find $(pwd) -maxdepth 2 -type f -not -path '*/\.*' | grep 'google-java-format-1.7-all-deps.jar$')"
for file in $java_files
do
    if [ -f $file ]
    then
        echo formatting $file
        java -jar $jar --replace --aosp $file
        count="$(git diff $file | wc -l)"
        if [ $count -gt 0 ]
        then 
            echo "($file) is incorrectly formatted: $count lines in diff"
            return 1
        fi
    fi
done
