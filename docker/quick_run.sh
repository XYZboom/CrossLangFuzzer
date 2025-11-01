#!/bin/bash

export JDK_1_8=/usr/lib/jvm/zulu-8-amd64/
export JDK_17_0=/usr/lib/jvm/zulu-17-amd64/
cd CrossLangFuzzer
./gradlew :runner:kotlin-runner:run --args="-s"

echo "See bugs at CrossLangFuzzer/out/min"