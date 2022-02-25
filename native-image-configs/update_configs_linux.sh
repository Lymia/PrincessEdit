#!/bin/bash

# Configuration
export JAVA_HOME=/usr/lib/jvm/java-17-graalvm/
export GRAAL_HOME=/usr/lib/jvm/java-17-graalvm/
export NATIVE_IMAGE_INSTALLED=true

TARGET_DIR="native-image-configs/linux"

rm -rfv "$TARGET_DIR"
sbt -java-home "$GRAAL_HOME" "set javaOptions += \"-agentlib:native-image-agent=config-merge-dir=$TARGET_DIR\"" "run --nativeImageGenConfig"
sbt -java-home "$GRAAL_HOME" "run --nativeImageProcessConfig"
