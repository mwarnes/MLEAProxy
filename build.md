# MLEAProxy Build Guide

## Overview

This document covers the complete build process for MLEAProxy, including compilation, packaging, testing, and deployment. The project has been modernized for Java 21 with comprehensive security hardening and Maven warning suppression.

## Prerequisites

### System Requirements
- **Java**: OpenJDK 21+ (LTS recommended)
- **Maven**: 3.9.11 or higher
- **Operating System**: macOS, Linux, or Windows

### Verification Commands
```bash
# Check Java version
java -version
# Should show: openjdk version "21.x.x" or higher

# Check Maven version  
mvn --version
# Should show: Apache Maven 3.9.11 or higher
```

## Project Structure

```
MLEAProxy/
├── src/main/java/           # Application source code
├── src/main/resources/      # Configuration files and static resources
├── src/test/java/          # Test source code
├── target/                 # Build output directory
├── .mvn/                   # Maven wrapper configuration
├── pom.xml                 # Maven project configuration
├── build.sh               # Clean build script (recommended)
├── dev-aliases.sh         # Development convenience aliases
└── mleaproxy.properties   # Application configuration
```

## Build Methods

### Method 1: Clean Build Script (Recommended)

The project includes a custom build script that filters out Maven/Guice compatibility warnings for a professional development experience.

```bash
# Make script executable (first time only)
chmod +x build.sh

# Clean compilation
./build.sh clean compile

# Full build with packaging
./build.sh clean package

# Run tests
./build.sh test

# Quiet mode (minimal output)
./build.sh package -q
```

**Advantages:**
- ✅ Filters out Maven/Guice `sun.misc.Unsafe` warnings
- ✅ Clean, professional output
- ✅ Same functionality as standard Maven
- ✅ Perfect for development workflow

### Method 2: Development Aliases

Load convenient aliases for faster development:

```bash
# Load development aliases (in current session)
source dev-aliases.sh

# Available commands:
mvn-clean    # Clean compilation
mvn-build    # Full clean build
mvn-test     # Run tests
mlproxy-run  # Start application with clean output
mlproxy-dev  # Complete development cycle

# Example usage:
mlproxy-dev  # Runs: clean → compile → package → report status
```

### Method 3: Standard Maven

Traditional Maven commands (with compatibility warnings visible):

```bash
# Clean and compile
mvn clean compile

# Full build
mvn clean package

# Run tests
mvn test

# Skip tests
mvn clean package -DskipTests

# Quiet mode
mvn clean package -q
```

## Build Phases Explained

### 1. Clean Phase
```bash
mvn clean
# or
./build.sh clean
```
- Removes `target/` directory
- Clears all compiled classes and artifacts
- Provides fresh build environment

### 2. Compilation Phase
```bash
mvn compile
# or  
./build.sh compile
```
- Compiles Java source files to bytecode
- Processes resources and copies to `target/classes/`
- Validates dependencies and configurations
- **Output**: Compiled classes in `target/classes/`

### 3. Test Phase
```bash
mvn test
# or
./build.sh test
```
- Compiles test source files
- Runs unit tests using JUnit/TestNG
- Generates test reports
- **Output**: Test results and reports

### 4. Package Phase
```bash
mvn package
# or
./build.sh package
```
- Creates JAR file from compiled classes
- Includes dependencies (Spring Boot fat JAR)
- Generates executable JAR with embedded server
- **Output**: `target/mlesproxy-2.0.0.jar`

### 5. Install Phase (Optional)
```bash
mvn install
```
- Installs JAR to local Maven repository
- Makes artifact available for other local projects

## Build Artifacts

### Primary Artifact
```
target/mlesproxy-2.0.0.jar
```
- **Type**: Executable Spring Boot JAR
- **Size**: ~50MB (includes all dependencies)
- **Contains**: Application code + embedded Undertow server + dependencies

### Additional Artifacts
```
target/mlesproxy-2.0.0.jar.original  # Original JAR before repackaging
target/classes/                       # Compiled classes
target/test-classes/                  # Compiled test classes  
target/maven-archiver/               # Maven metadata
```

## Running the Application

### Method 1: Using Build Script Alias
```bash
source dev-aliases.sh
mlproxy-run
```

### Method 2: Direct Java Execution
```bash
java -Dmleaproxy.properties=./mleaproxy.properties -jar target/mlesproxy-2.0.0.jar
```

### Method 3: Maven Spring Boot Plugin
```bash
mvn spring-boot:run
# or
./build.sh spring-boot:run
```

## Configuration

### Maven Configuration (`pom.xml`)

**Key Settings:**
- **Java Version**: 21 (LTS)
- **Spring Boot**: 3.3.5
- **Maven Compiler**: 3.13.0
- **Packaging**: JAR with embedded server

**Security Features:**
- Input validation dependencies
- XML processing security
- Modern encryption libraries
- Rate limiting capabilities

### JVM Configuration (`.mvn/jvm.config`)

Automatic JVM arguments for compatibility:
```
-XX:+IgnoreUnrecognizedVMOptions
-Djdk.module.illegalAccess.silent=true
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED
--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED
```

## Build Profiles and Options

### Development Profile (Default)
```bash
./build.sh clean package
```
- Includes debugging information
- Full logging enabled
- Development-friendly configurations

### Production Build
```bash
./build.sh clean package -Pproduction
```
- Optimized for deployment
- Minimal logging
- Security hardening enabled

### Quick Build (Skip Tests)
```bash
./build.sh package -DskipTests
# or
mvn package -DskipTests
```

## Troubleshooting

### Common Issues

#### 1. Java Version Mismatch
**Problem**: `error: invalid target release: 21`
**Solution**: 
```bash
# Check Java version
java -version
# Install OpenJDK 21 if needed
# Update JAVA_HOME environment variable
```

#### 2. Maven Warnings About sun.misc.Unsafe
**Problem**: Maven displays Guice compatibility warnings
**Solution**: Use the build script (warnings are filtered automatically)
```bash
./build.sh package  # Clean output
```

#### 3. Out of Memory During Build
**Problem**: `java.lang.OutOfMemoryError`
**Solution**: Increase Maven memory
```bash
export MAVEN_OPTS="-Xmx2g -XX:MaxMetaspaceSize=512m"
mvn clean package
```

#### 4. Dependency Resolution Issues
**Problem**: Dependencies not downloading
**Solution**: 
```bash
# Clear local repository cache
rm -rf ~/.m2/repository/com/marklogic
mvn clean package -U  # Force updates
```

#### 5. Port Already in Use
**Problem**: Application fails to start (port 8080 occupied)
**Solution**: 
```bash
# Check what's using port 8080
lsof -i :8080
# Kill process or change port in application.properties
```

### Build Logs

#### Successful Build Indicators
```
[INFO] BUILD SUCCESS
[INFO] Total time: X.XXX s
[INFO] Final Memory: XXM/XXM
```

#### Failed Build Indicators
```
[ERROR] BUILD FAILURE
[ERROR] Compilation failure
[ERROR] Test failures: X
```

## Performance Optimization

### Build Speed Tips

1. **Use Build Script**: Faster output processing
```bash
./build.sh package -q
```

2. **Skip Tests** (development only):
```bash
./build.sh package -DskipTests
```

3. **Parallel Builds**:
```bash
mvn clean package -T 1C  # Use 1 thread per CPU core
```

4. **Offline Mode** (when dependencies cached):
```bash
mvn clean package -o
```

## Security Considerations

### Build Security Features
- **Input Validation**: All user inputs validated and sanitized
- **LDAP Injection Prevention**: Pattern detection and filtering  
- **XML Security**: XXE attack prevention and size validation
- **Rate Limiting**: DoS protection mechanisms
- **Credential Protection**: Automatic masking in logs

### Secure Build Practices
```bash
# Verify JAR integrity
sha256sum target/mlesproxy-2.0.0.jar

# Check for vulnerabilities (if OWASP plugin enabled)
mvn org.owasp:dependency-check-maven:check

# Security scan (if available)
mvn clean package sonar:sonar
```

## CI/CD Integration

### GitHub Actions Example
```yaml
name: Build MLEAProxy
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
    - name: Build with Maven
      run: ./build.sh clean package
    - name: Upload artifacts
      uses: actions/upload-artifact@v4
      with:
        name: mlesproxy-jar
        path: target/*.jar
```

### Jenkins Pipeline Example
```groovy
pipeline {
    agent any
    tools {
        jdk 'OpenJDK-21'
        maven 'Maven-3.9'
    }
    stages {
        stage('Build') {
            steps {
                sh './build.sh clean package'
            }
        }
        stage('Test') {
            steps {
                sh './build.sh test'
            }
        }
        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/*.jar'
            }
        }
    }
}
```

## Version History

### Current Version: 2.0.0
- **Java 21 Compatibility**: Upgraded from Java 17
- **Spring Boot 3.3.5**: Latest stable version
- **Security Hardening**: Comprehensive input validation
- **Maven Warning Suppression**: Clean build experience
- **Performance Improvements**: Optimized dependencies

### Previous Versions
- **1.x**: Legacy Java 11/17 versions
- **0.x**: Development versions

## Quick Reference

### Essential Commands
```bash
# Development workflow
./build.sh clean package     # Full clean build
source dev-aliases.sh        # Load convenience aliases
mlproxy-dev                  # Complete development cycle

# Production deployment  
./build.sh clean package -Pproduction
java -jar target/mlesproxy-2.0.0.jar

# Troubleshooting
mvn dependency:tree          # Show dependency tree
mvn clean package -X         # Debug mode
mvn help:effective-pom       # Show effective POM
```

### File Locations
- **Main JAR**: `target/mlesproxy-2.0.0.jar`
- **Configuration**: `mleaproxy.properties`
- **Logs**: Console output (configurable in application.properties)
- **Build Logs**: Maven console output

---

## Summary

The MLEAProxy build system provides a modern, secure, and efficient development experience with Java 21 compatibility, comprehensive security features, and clean build output. Use the provided build scripts for the best development experience, or fall back to standard Maven commands when needed.

For questions or issues, refer to the troubleshooting section or check the project documentation.