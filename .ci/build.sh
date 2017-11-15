#!/usr/bin/env bash

# This script is a workaround for https://github.com/sbt/sbt/issues/3684

set -e
set -x

jdk_ver=`java -version 2>&1 >/dev/null | grep 'java version' | awk '{print $3}' | tr -d '"'`

if [[ ${jdk_ver} == 9* ]] ;
then
    sbt -Djava.specification.version=9.1 ++${TRAVIS_SCALA_VERSION} scalastyle test:scalastyle coverage test coverageReport
else
    sbt ++${TRAVIS_SCALA_VERSION} scalastyle test:scalastyle coverage test coverageReport
fi
