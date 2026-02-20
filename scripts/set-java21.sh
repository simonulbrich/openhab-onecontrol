#!/bin/bash
#
# Set JAVA_HOME to Java 21 for Maven builds.
#
# MUST be sourced, not run directly (exports won't persist otherwise):
#   source scripts/set-java21.sh
#   . scripts/set-java21.sh
#
# Environment variables (optional):
#   JAVA_HOME  - If set and points to Java 21, uses it. Otherwise tries to auto-detect.
#
# Usage:
#   source scripts/set-java21.sh
#   source scripts/set-java21.sh 2>/dev/null  # Suppress version output
#

# Check if we're being sourced (required for export to affect caller)
if [[ "${BASH_SOURCE[0]}" != "${0}" ]]; then
    _SET_JAVA21_SOURCED=1
else
    echo "Usage: source $0   (must be sourced, not run)"
    echo "Run: source scripts/set-java21.sh"
    exit 1
fi

_detect_java21() {
    # macOS: use java_home if available
    if [[ -x /usr/libexec/java_home ]]; then
        local _home
        _home=$(/usr/libexec/java_home -v 21 2>/dev/null)
        if [[ -n "$_home" ]]; then
            echo "$_home"
            return
        fi
    fi

    # Homebrew Apple Silicon
    if [[ -d /opt/homebrew/opt/openjdk@21 ]]; then
        echo "/opt/homebrew/opt/openjdk@21"
        return
    fi

    # Homebrew Intel
    if [[ -d /usr/local/opt/openjdk@21 ]]; then
        echo "/usr/local/opt/openjdk@21"
        return
    fi

    # Linux: common paths
    for _path in /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/java-21-openjdk; do
        if [[ -d "$_path" ]]; then
            echo "$_path"
            return
        fi
    done

    return 1
}

# Use existing JAVA_HOME if it's Java 21, otherwise auto-detect
_fail() {
    echo "Error: Java 21 not found. Install it first:"
    echo "  macOS:  brew install openjdk@21"
    echo "  Debian: sudo apt install openjdk-21-jdk"
    echo "  Fedora: sudo dnf install java-21-openjdk-devel"
    echo ""
    echo "Or set JAVA_HOME before sourcing: export JAVA_HOME=/path/to/jdk21"
    return 1 2>/dev/null || exit 1
}

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    _ver=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)
    if [[ "$_ver" != *"21"* ]]; then
        _detected=$(_detect_java21)
        [[ -n "$_detected" ]] && export JAVA_HOME="$_detected" || _fail
    fi
else
    _detected=$(_detect_java21)
    [[ -n "$_detected" ]] && export JAVA_HOME="$_detected" || _fail
fi

export PATH="$JAVA_HOME/bin:$PATH"

echo "JAVA_HOME set to: $JAVA_HOME"
java -version
