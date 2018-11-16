#!/bin/bash -ev

. ./setenv.sh

echo y | sdkmanager "platform-tools" | tee sdkmanager.log
#echo y | sdkmanager "tools"  | tee -a sdkmanager.log 


echo y | sdkmanager "extras;android;m2repository"  | tee -a  sdkmanager.log 
echo y | sdkmanager "extras;google;m2repository"  | tee -a sdkmanager.log 

echo y | sdkmanager "build-tools;23.0.2"  | tee -a sdkmanager.log 
echo y | sdkmanager "build-tools;23.0.3"  | tee -a sdkmanager.log 
echo y | sdkmanager "build-tools;25.0.2"   | tee -a sdkmanager.log 
echo y | sdkmanager "build-tools;27.0.3"   | tee -a sdkmanager.log

echo y | sdkmanager "platforms;android-16"  | tee -a sdkmanager.log 
echo y | sdkmanager "platforms;android-21"  | tee -a sdkmanager.log 
echo y | sdkmanager "platforms;android-23"  | tee -a sdkmanager.log
echo y | sdkmanager "platforms;android-24"  | tee -a sdkmanager.log 
echo y | sdkmanager "platforms;android-25"  | tee -a sdkmanager.log 
echo y | sdkmanager "platforms;android-27"  | tee -a sdkmanager.log

#sdkmanager --update
