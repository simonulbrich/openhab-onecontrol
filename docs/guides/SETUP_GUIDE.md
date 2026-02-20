# Build Environment Setup Guide

This guide sets up Java 21 and Maven so you can build the IDS MyRV binding from source. After completing this, you can build the JAR and deploy to OpenHAB ([DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)).

---

## Prerequisites

- A terminal (Terminal.app, iTerm, or Linux terminal)
- Internet connection for downloads
- Admin/sudo access (for installing packages)
- Project source code (cloned or downloaded)

---

## Choose Your Platform

- **macOS** → [Step 1: Install Homebrew](#step-1-install-homebrew-macos), then [Step 2–3](#step-2-install-java-21-macos)
- **Linux** → [Step 1: Linux Packages](#step-1-linux-packages), then skip to [Step 4](#step-4-build-and-test)
- **Automated** → Run `./scripts/setup-and-test.sh` from the project root (installs deps on macOS or Linux, then runs tests)

---

## Step 1: Install Homebrew (macOS)

Homebrew is a package manager for macOS. Run this in your terminal:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

**What will happen:**
- It will ask for your password (sudo access)
- It will download and install Homebrew
- Takes about 2-3 minutes
- You'll see progress messages

**After installation completes**, you may need to add Homebrew to your PATH. The installer will tell you if needed. It will look like:

```bash
# For Apple Silicon (M1/M2/M3):
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"

# For Intel Macs:
echo 'eval "$(/usr/local/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/usr/local/bin/brew shellenv)"
```

**Verify:** `brew --version` should show Homebrew 4.x or newer.

---

## Step 1: Linux Packages

On Debian/Ubuntu:
```bash
sudo apt update
sudo apt install openjdk-21-jdk maven
```

On Fedora/RHEL:
```bash
sudo dnf install java-21-openjdk-devel maven
```

**Set JAVA_HOME** (add to `~/.bashrc` or `~/.zshrc`):
```bash
# Debian/Ubuntu: typically /usr/lib/jvm/java-21-openjdk-amd64
# Fedora: typically /usr/lib/jvm/java-21-openjdk
# Or auto-detect: export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
source ~/.bashrc   # or source ~/.zshrc
```

**Verify:** `java -version` and `mvn -version` — then skip to [Step 4: Build and Test](#step-4-build-and-test).

---

## Step 2: Install Java 21 (macOS)

Once Homebrew is installed, run:

```bash
brew install openjdk@21
```

**What will happen:**
- Downloads and installs OpenJDK 21
- Takes about 2-3 minutes
- ~200MB download

**After installation**, link Java so the system can find it:

```bash
# Apple Silicon (M1/M2/M3):
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# Intel Macs:
sudo ln -sfn /usr/local/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

**Set JAVA_HOME** (add to your shell profile):

```bash
# For zsh (default on modern macOS):
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# For bash:
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.bash_profile
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.bash_profile
source ~/.bash_profile
```

**Verify Java is installed:**
```bash
java -version
```

You should see: `openjdk version "21.0.x"`

---

## Step 3: Install Maven

```bash
brew install maven
```

**What will happen:**
- Downloads and installs Apache Maven
- Takes about 1-2 minutes
- Maven will automatically use Java 21

**Verify Maven is installed:**
```bash
mvn -version
```

You should see:
```
Apache Maven 3.x.x
Java version: 21.0.x
```

---

## Step 4: Build and Test

Now you're ready to run the tests:

```bash
cd /path/to/openhab-binding    # replace with your actual project path
mvn clean test
```

**What will happen:**
- Maven downloads dependencies (~50MB, first time only)
- Compiles the Java code
- Runs all 111 unit tests
- Takes about 30-60 seconds total

**Expected output:**
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running org.openhab.binding.idsmyrv.internal.can.AddressTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.openhab.binding.idsmyrv.internal.can.CANIDTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.openhab.binding.idsmyrv.internal.can.CANMessageTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.openhab.binding.idsmyrv.internal.protocol.COBSTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.openhab.binding.idsmyrv.internal.idscan.MessageTypeTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.openhab.binding.idsmyrv.internal.idscan.DeviceTypeTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.openhab.binding.idsmyrv.internal.idscan.IDSMessageTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.openhab.binding.idsmyrv.internal.idscan.CommandBuilderTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.openhab.binding.idsmyrv.internal.idscan.SessionManagerTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] -------------------------------------------------------
[INFO] Results:
[INFO]
[INFO] Tests run: 111, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] -------------------------------------------------------
[INFO] BUILD SUCCESS ✅
[INFO] -------------------------------------------------------
```

---

## Quick Reference - All Commands in Order (macOS)

```bash
# 1. Install Homebrew
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 2. Install Java 21
brew install openjdk@21
# Apple Silicon: use /opt/homebrew/opt/openjdk@21  |  Intel: use /usr/local/opt/openjdk@21
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# 3. Set JAVA_HOME (for zsh)
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 4. Install Maven
brew install maven

# 5. Verify installations
brew --version
java -version
mvn -version

# 6. Build and run tests
cd /path/to/openhab-binding
mvn clean test
```

---

## Troubleshooting

| Problem | Platform | Solution |
|--------|----------|----------|
| `Command not found: brew` | macOS | Run the `eval` command the installer showed you, or open a new terminal |
| `Command not found: java` / `mvn` | Linux | Add `JAVA_HOME` and `PATH` to `~/.bashrc` or `~/.zshrc`, then `source` it |
| Java version is still 17 or older | macOS | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` then `java -version` |
| Java version is still 17 or older | Linux | Verify `JAVA_HOME` points to Java 21; use `update-alternatives --config java` (Debian) if needed |
| Tests fail to compile | Any | Ensure you're in the project root: `cd /path/to/openhab-binding` |
| Cannot find dependencies | Any | Maven downloads them on first run; check internet connection |

---

## Estimated Total Time

| Platform | Steps | Time |
|----------|-------|------|
| macOS (fresh) | Homebrew + Java + Maven + tests | ~10 minutes |
| macOS (Homebrew already installed) | Java + Maven + tests | ~5 minutes |
| Linux | apt/dnf install + tests | ~5 minutes |

---

## What's Next After Tests Pass

Once all 111 tests pass, you'll know:
- ✅ CAN protocol implementation is correct
- ✅ COBS framing works properly
- ✅ IDS-CAN message encoding/decoding is accurate
- ✅ Light commands build correctly
- ✅ Session management works as designed

**Next step:** Build the JAR and deploy to OpenHAB. Follow the **[Deployment Guide](DEPLOYMENT_GUIDE.md)** for:
1. Building the binding JAR (`mvn clean package`)
2. Copying it to your OpenHAB addons folder
3. Restarting OpenHAB and verifying the binding loads
4. Configuring your CAN gateway (or SocketCAN) and discovering devices

Then use the **[Quick Start](QUICK_START.md)** to add the bridge, discover devices, and control your lights.

---

**Ready?** Choose your platform in [Step 1](#choose-your-platform) above!


