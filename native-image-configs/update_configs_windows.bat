rem Configuration
rem
rem You'll probably have to edit these paths on most systems.
set JAVA_HOME=X:\windows-programs\graalvm-ce-java17-22.0.0.2
set GRAAL_HOME=X:\windows-programs\graalvm-ce-java17-22.0.0.2
set NATIVE_IMAGE_INSTALLED=true

set TARGET_DIR=native-image-configs/windows

rd /s /q "%TARGET_DIR%"
cmd /c sbt -java-home "%GRAAL_HOME%" "set javaOptions += ""-agentlib:native-image-agent=config-merge-dir=%TARGET_DIR%""" "run --nativeImageGenConfig"
cmd /c sbt -java-home "%GRAAL_HOME%" "run --nativeImageProcessConfig"
