#!/bin/bash
# MLEAProxy Build Script
# Filters out Maven/Guice compatibility warnings for cleaner output

echo "Building MLEAProxy (filtering Maven compatibility warnings)..."

# Run Maven and filter out the specific Unsafe warnings
mvn "$@" 2>&1 | grep -v -E "(WARNING.*sun.misc.Unsafe|WARNING.*staticFieldBase|WARNING.*HiddenClassDefiner|WARNING.*Please consider reporting)"