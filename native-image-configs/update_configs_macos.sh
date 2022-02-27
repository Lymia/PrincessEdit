#!/bin/bash

# Configuration
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-java17-22.0.0.2/Contents/Home
export GRAAL_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-java17-22.0.0.2/Contents/Home
export NATIVE_IMAGE_INSTALLED=true

TARGET_DIR="native-image-configs/macos"

rm -rfv "$TARGET_DIR"
sbt -java-home "$GRAAL_HOME" "set javaOptions += \"-agentlib:native-image-agent=config-merge-dir=$TARGET_DIR\"" "run --nativeImageGenConfig"
sbt -java-home "$GRAAL_HOME" "run --nativeImageProcessConfig"
