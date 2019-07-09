#!/bin/bash

# this is not working on Jenkins but may be a useful idea later to reduce the check duration
#branch="$(git rev-parse --abbrev-ref HEAD)"
#master_branch=master-pre-merge
#if [ "$branch" = "$master_branch" ]
#then
#    master_branch=master
#fi
#
#echo "Checking out '$master_branch' branch for comparison:"
#git checkout $master_branch; git checkout $branch
#
#diff_count="$(git log --graph --oneline --date=relative $master_branch..$branch  | wc -l)"
#if [ $diff_count -eq 1 ]
#then
#    echo "There is $diff_count commit in addition to reference branch."
#else
#    echo "There are $diff_count commits in addition to reference branch."
#fi
#
#java_files="$(git show HEAD~$diff_count..HEAD --name-only | grep 'mod.*java$' | sort -u | grep -v -E '3rdParty|modApiServer.src.org.aion.api.server.pb.Message.java' )"

java_files="$(find $(pwd) -maxdepth 100 -type f -not -path '*/\.*' | grep 'mod.*java$' | grep -v -E 'aion_fastvm|aion_gui|aion_vm_api|3rdParty|modApiServer.src.org.aion.api.server.pb.Message.java' )"
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
