# Maven Warning Suppression Guide

## Problem

Maven 3.9.11 uses Google Guice internally, which accesses deprecated `sun.misc.Unsafe` methods. This causes warnings on Java 21+ that are not directly fixable from application code.

## Solutions Implemented

### 1. Build Script with Output Filtering (Recommended)

```bash
# Use the provided build script
./build.sh clean compile
./build.sh package
./build.sh test
```

The script filters out these specific warnings:

- `WARNING.*sun.misc.Unsafe`
- `WARNING.*staticFieldBase`
- `WARNING.*HiddenClassDefiner`
- `WARNING.*Please consider reporting`

### 2. Maven JVM Configuration

The `.mvn/jvm.config` file contains JVM arguments that attempt to suppress the warnings at the JVM level:

```java
-XX:+IgnoreUnrecognizedVMOptions
-Djdk.module.illegalAccess.silent=true
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED
--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED
```

### 3. POM Configuration

The `pom.xml` includes compiler and surefire plugin configurations with appropriate JVM arguments for builds and tests.

## Usage

### Clean Build (Recommended)

```bash
./build.sh clean package
```

### Standard Maven (with warnings)

```bash
mvn clean package
```

### Environment Variable (Alternative)

```bash
export MAVEN_OPTS="-Djdk.module.illegalAccess.silent=true"
mvn clean package
```

## Root Cause

This is a known issue with Maven 3.9.x and Google Guice when running on Java 17+. The warnings are harmless but verbose. The issue will be resolved when:

1. Maven updates to a newer version of Google Guice
2. Google Guice removes usage of deprecated Unsafe methods
3. A newer Maven version is released

## Recommendation

Use the `build.sh` script for clean output during development. The warnings don't affect functionality and the application runs correctly on Java 21.
