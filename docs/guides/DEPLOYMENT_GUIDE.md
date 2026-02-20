# OpenHAB Binding Deployment Guide

This guide covers building the binding JAR and deploying it to a local OpenHAB instance. For build environment setup (Java, Maven), see [SETUP_GUIDE.md](SETUP_GUIDE.md).

---

## Prerequisites

- **Java 21** and **Maven 3.8+** installed ([SETUP_GUIDE.md](SETUP_GUIDE.md))
- **OpenHAB 5** installed and running
- Project source code (cloned or downloaded)

---

## Step 1: Build the JAR

From the project root:

```bash
cd /path/to/openhab-binding
mvn clean package -DskipTests
```

**Output:** `target/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar`

*(Use `mvn clean package` without `-DskipTests` to run tests.)*

---

## Step 2: Find Your OpenHAB Addons Directory

| Install method | Typical addons path |
|----------------|---------------------|
| **Linux (manual/zip)** | `/usr/share/openhab/addons/` or `$OPENHAB_HOME/addons/` |
| **Linux (Debian/apt)** | `/usr/share/openhab/addons/` |
| **macOS (Homebrew)** | `/usr/local/var/lib/openhab/addons/` or `/opt/homebrew/var/lib/openhab/addons/` |
| **Docker** | `/openhab/addons/` (inside container) or mounted volume |
| **Windows** | `%USERPROFILE%\openHAB-addons\` or your OpenHAB install path |

---

## Step 3: Deploy the JAR

### Option A: Copy and Restart (Recommended for first install)

1. **Stop OpenHAB** (optional): `sudo systemctl stop openhab` or `brew services stop openhab`
2. **Copy JAR** (replace path with yours):
   ```bash
   cp target/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar /usr/share/openhab/addons/
   ```
3. **If upgrading**, remove old JAR first: `rm .../addons/org.openhab.binding.idsmyrv-*.jar`
4. **Start OpenHAB**: `sudo systemctl start openhab` or `brew services start openhab`
5. **Wait 20–30 seconds** for the bundle to load

### Option B: Hot Deploy (No restart, for upgrades)

1. Copy JAR to addons directory
2. **OpenHAB console**: `ssh -p 8101 openhab@localhost`
3. **Find bundle ID**: `bundle:list | grep idsmyrv`
4. **Uninstall old**: `bundle:uninstall <bundle-id>`
5. **Install new** (use absolute path, three slashes after `file:`):
   ```
   bundle:install file:///usr/share/openhab/addons/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar
   ```
6. **Start**: `bundle:start <bundle-id>`

### Option C: Docker

```bash
docker cp target/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar openhab:/openhab/addons/
```

---

## Step 4: Verify Deployment

- **Console:** `bundle:list | grep idsmyrv` → should show `Active`
- **UI:** Settings → Things → + → search "IDS" → binding should appear
- **Logs:** Look for `[INFO] Bundle org.openhab.binding.idsmyrv started`

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails | Install Java 21 & Maven ([SETUP_GUIDE.md](SETUP_GUIDE.md)) |
| Binding not in UI | Clear cache, restart OpenHAB, wait 30–60 seconds |
| Hot deploy path | Must be absolute: `file:///usr/share/.../file.jar` (three slashes) |
| Bundle Installed not Active | Run `bundle:diag <id>` for dependency errors |

---

## Next Steps

- [QUICK_START.md](QUICK_START.md) – Add gateway and devices
- [SOCKETCAN_QUICKSTART.md](SOCKETCAN_QUICKSTART.md) – Direct CAN adapter setup
