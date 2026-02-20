#!/bin/bash
#
# Setup and Test Script for IDS MyRV OpenHAB Binding
# Installs prerequisites (Homebrew, Java 21, Maven) and runs unit tests.
#
# Platform: macOS (Homebrew) and Linux (apt/dnf)
#
# Usage:
#   ./scripts/setup-and-test.sh        # Run from project root
#   ./scripts/setup-and-test.sh --help # Show usage
#
# Requires: Run from project root (directory containing pom.xml)
#

set -e

print_usage() {
    echo "Usage: $0 [--help]"
    echo ""
    echo "Run from the project root (openhab-binding directory containing pom.xml)."
    echo ""
    echo "Installs Java 21 and Maven (apt/dnf on Linux, Homebrew on macOS), then runs mvn clean test."
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    print_usage
    exit 0
fi

# Resolve project root (parent of scripts/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

if [[ ! -f "$PROJECT_ROOT/pom.xml" ]]; then
    echo "Error: pom.xml not found. Run this script from the project root."
    echo "  Expected: $PROJECT_ROOT/pom.xml"
    echo "  Usage: ./scripts/setup-and-test.sh"
    exit 1
fi

cd "$PROJECT_ROOT"

# Linux: use apt or dnf to install deps
if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "=========================================="
    echo "IDS MyRV Binding - Setup and Test (Linux)"
    echo "=========================================="
    echo ""

    # Detect package manager
    install_cmd=""
    if command -v apt-get &>/dev/null; then
        install_cmd="sudo apt-get update && sudo apt-get install -y openjdk-21-jdk maven"
        java_home_path="/usr/lib/jvm/java-21-openjdk-amd64"
        [[ "$(uname -m)" == "aarch64" ]] && java_home_path="/usr/lib/jvm/java-21-openjdk-arm64"
    elif command -v dnf &>/dev/null; then
        install_cmd="sudo dnf install -y java-21-openjdk-devel maven"
        java_home_path="/usr/lib/jvm/java-21-openjdk"
    elif command -v yum &>/dev/null; then
        install_cmd="sudo yum install -y java-21-openjdk-devel maven"
        java_home_path="/usr/lib/jvm/java-21-openjdk"
    else
        echo "Error: No supported package manager (apt, dnf, yum) found."
        echo "Install manually: Java 21 JDK and Maven. See docs/guides/SETUP_GUIDE.md"
        exit 1
    fi

    # Install or validate Java 21 and Maven
    need_install=false
    if ! command -v java &>/dev/null || ! command -v mvn &>/dev/null; then
        need_install=true
    else
        _jver=$(java -version 2>&1 | head -n 1 | grep -oE '[0-9]+' | head -1)
        [[ -z "$_jver" || "$_jver" -lt 21 ]] && need_install=true
    fi

    if [[ "$need_install" == true ]]; then
        echo "Installing Java 21 and Maven..."
        eval "$install_cmd" || {
            echo "Install failed. Run manually:"
            echo "  $install_cmd"
            exit 1
        }
        echo "✅ Java 21 and Maven installed"
    else
        echo "✅ Java $(java -version 2>&1 | head -n 1)"
        echo "✅ Maven $(mvn -version 2>&1 | head -n 1)"
    fi

    # Set JAVA_HOME if not set or wrong
    if [[ -d "${java_home_path}" ]]; then
        export JAVA_HOME="$java_home_path"
    else
        # Fallback: find any java-21* dir in /usr/lib/jvm
        for _p in /usr/lib/jvm/java-21*; do
            [[ -d "$_p" ]] && { export JAVA_HOME="$_p"; break; }
        done
    fi
    [[ -n "${JAVA_HOME:-}" ]] && export PATH="$JAVA_HOME/bin:$PATH"

    echo ""
    echo "=========================================="
    echo "Running Unit Tests"
    echo "=========================================="
    echo ""

    mvn clean test

    if [[ $? -eq 0 ]]; then
        echo ""
        echo "=========================================="
        echo "✅ ALL TESTS PASSED!"
        echo "=========================================="
        echo ""
        echo "Next steps: docs/guides/DEPLOYMENT_GUIDE.md"
    else
        echo ""
        echo "❌ TESTS FAILED - Review errors above."
        exit 1
    fi
    exit 0
fi

echo "=========================================="
echo "IDS MyRV Binding - Setup and Test"
echo "=========================================="
echo ""

# Check if Homebrew is installed (macOS)
if ! command -v brew &> /dev/null; then
    echo "❌ Homebrew not found. Installing Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    echo "✅ Homebrew installed"
else
    echo "✅ Homebrew already installed"
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Installing Maven..."
    brew install maven
    echo "✅ Maven installed"
else
    echo "✅ Maven already installed: $(mvn -version | head -n 1)"
fi

# Check Java version
echo ""
echo "Checking Java version..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
    echo "Current Java version: $JAVA_VERSION"
    
    if [[ "$JAVA_VERSION" < "21" ]]; then
        echo "⚠️  Java 21 required, but found $JAVA_VERSION"
        echo "Installing OpenJDK 21..."
        brew install openjdk@21
        
        # Set JAVA_HOME for this script
        export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "/opt/homebrew/opt/openjdk@21")
        export PATH="$JAVA_HOME/bin:$PATH"
        
        echo "✅ Java 21 installed"
        echo "   Note: You may need to set JAVA_HOME permanently in your shell profile"
        echo "   Add to ~/.zshrc or ~/.bash_profile:"
        echo "   export JAVA_HOME=$JAVA_HOME"
        echo "   export PATH=\"\$JAVA_HOME/bin:\$PATH\""
    else
        echo "✅ Java version is sufficient"
    fi
else
    echo "❌ Java not found. Installing OpenJDK 21..."
    brew install openjdk@21
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "✅ Java 21 installed"
fi

echo ""
echo "=========================================="
echo "Running Unit Tests"
echo "=========================================="
echo ""

# Run Maven tests (PROJECT_ROOT set at top)
echo "Running: mvn clean test"
echo ""

mvn clean test

# Check exit code
if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "✅ ALL TESTS PASSED!"
    echo "=========================================="
    echo ""
    echo "Test Summary:"
    echo "  - CAN Protocol: AddressTest, CANIDTest, CANMessageTest"
    echo "  - COBS Protocol: COBSTest"
    echo "  - IDS-CAN Protocol: MessageTypeTest, DeviceTypeTest, IDSMessageTest"
    echo "  - Commands: CommandBuilderTest"
    echo "  - Session: SessionManagerTest"
    echo ""
    echo "Total: ~111 unit tests"
    echo ""
    echo "Next steps:"
    echo "  1. Build and deploy: see docs/guides/DEPLOYMENT_GUIDE.md"
    echo "  2. Optional: mvn jacoco:report  (coverage report)"
else
    echo ""
    echo "=========================================="
    echo "❌ TESTS FAILED"
    echo "=========================================="
    echo ""
    echo "Please review the errors above and fix them."
    exit 1
fi


