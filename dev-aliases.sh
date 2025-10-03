#!/bin/bash
# MLEAProxy Development Aliases
# Source this file in your shell profile (.zshrc, .bashrc) for convenient commands

# Clean Maven build without warnings
alias mvn-clean="./build.sh clean compile"
alias mvn-build="./build.sh clean package"
alias mvn-test="./build.sh test"
alias mvn-run="./build.sh spring-boot:run"

# Function for running application with clean output
mlproxy-run() {
    echo "Starting MLEAProxy with clean output..."
    java -Dmleaproxy.properties=./mleaproxy.properties -jar target/mlesproxy-2.0.0.jar 2>&1 | grep -v -E "(WARNING.*sun.misc|WARNING.*staticFieldBase)"
}

# Function for development build cycle
mlproxy-dev() {
    echo "MLEAProxy Development Build Cycle"
    echo "================================="
    ./build.sh clean compile && \
    echo "✅ Compilation successful" && \
    ./build.sh package -q && \
    echo "✅ Packaging successful" && \
    echo "📦 JAR ready: target/mlesproxy-2.0.0.jar"
}

echo "MLEAProxy development aliases loaded!"
echo "Available commands:"
echo "  mvn-clean   - Clean compilation" 
echo "  mvn-build   - Full build"
echo "  mvn-test    - Run tests"
echo "  mlproxy-run - Start application with clean output"
echo "  mlproxy-dev - Complete development cycle"