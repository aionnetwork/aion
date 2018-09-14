#!/bin/bash -ev

echo ${TEST_VAR}
gradle uploadArchives 
mvn deploy --settings settings.xml
