#!/bin/bash

docker exec -it kaliumjni_kalium-jni_1 /bin/bash cat /installs/kalium-jni/libs/armeabi/libkaliumjni.so > /tmp/libkaliumjni.so
docker exec -it kaliumjni_kalium-jni_1 /bin/bash cat /root/.m2/repository/org/abstractj/kalium/kalium-jni/1.0.0-SNAPSHOT/kalium-jni-1.0.0-SNAPSHOT.jar > /tmp/kalium-jni-1.0.0-SNAPSHOT.jar

