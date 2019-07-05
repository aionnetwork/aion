#!/bin/bash

branch="$(git rev-parse --abbrev-ref HEAD)"
master_branch=master-pre-merge
if [ "$branch" = "$master_branch" ]
then
    master_branch=master
fi

diff_count="$(git log --graph --oneline --date=relative $master_branch..$branch  | wc -l)"
java_files="$(git show HEAD~$diff_count..HEAD --name-only | grep 'mod.*java$' | sort -u | grep -v -E '3rdParty|modApiServer.src.org.aion.api.server.pb.Message.java' )"
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
