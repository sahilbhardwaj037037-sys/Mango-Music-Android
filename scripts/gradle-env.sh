#!/bin/sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export GRADLE_USER_HOME="$TASK_ROOT/.local/gradle"
export ANDROID_USER_HOME="$TASK_ROOT/.local/android"
export ANDROID_HOME="$TASK_ROOT/.tools/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
cd "$TASK_ROOT"
exec ./gradlew "$@"
