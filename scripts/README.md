# Scripts Reference

Helper scripts for building, testing, and troubleshooting the IDS MyRV binding. Run from the **project root**.

---

## Scripts Overview

| Script | Purpose |
|--------|---------|
| `setup-and-test.sh` | Install deps (macOS) and run `mvn clean test` |
| `set-java21.sh` | Set `JAVA_HOME` to Java 21 (must be sourced) |
| `troubleshoot.sh` | Check JAR deployment, bundle status, OpenHAB logs |

---

## setup-and-test.sh

**Usage:** `./scripts/setup-and-test.sh [--help]`

- **Arguments:** `--help` / `-h` — Show usage
- **Required:** Run from project root (directory containing `pom.xml`)
- **Platform:** macOS (Homebrew) and Linux (apt/dnf/yum)

**What it does:**
- Verifies `pom.xml` exists (fails with clear error if not)
- **macOS:** Installs Homebrew, Java 21, Maven if missing; runs `mvn clean test`
- **Linux:** Installs Java 21 and Maven via apt (Debian/Ubuntu) or dnf/yum (Fedora/RHEL) if missing; runs tests

**Errors:**
- `pom.xml not found` → Run from project root, e.g. `./scripts/setup-and-test.sh`

---

## set-java21.sh

**Usage:** `source scripts/set-java21.sh` (or `. scripts/set-java21.sh`)

- **Must be sourced** — Running `./scripts/set-java21.sh` prints usage and exits; exports only persist when sourced
- **Environment:** `JAVA_HOME` — Optional. If set and points to Java 21, uses it. Otherwise auto-detects (Homebrew, Linux paths)

**What it does:**
- Auto-detects Java 21 (macOS: `java_home`, Homebrew; Linux: common JDK paths)
- Exports `JAVA_HOME` and updates `PATH`
- Prints `java -version` on success

**Errors:**
- `Java 21 not found` → Install via `brew install openjdk@21` (macOS) or apt/dnf (Linux)
- `must be sourced` → Use `source scripts/set-java21.sh`, not `./scripts/set-java21.sh`

---

## troubleshoot.sh

**Usage:** `./scripts/troubleshoot.sh [--help]`

- **Arguments:** `--help` / `-h` — Show usage and environment variables
- **Environment variables (all optional):**

| Variable | Default | Purpose |
|----------|---------|---------|
| `OPENHAB_ADDONS` | `/usr/local/var/lib/openhab/addons` | Addons directory |
| `OPENHAB_LOG` | `/usr/local/var/log/openhab/openhab.log` | Main log file |
| `OPENHAB_EVENTS_LOG` | `/usr/local/var/log/openhab/events.log` | Events log |

**Example:**
```bash
OPENHAB_ADDONS=/opt/openhab/addons ./scripts/troubleshoot.sh
```

**What it does:**
- Prints paths in use (so you can see if overrides are needed)
- Checks for deployed JAR, suggests copy command if missing
- Shows recent log entries and errors
- Suggests setting env vars when paths don't exist

---

## See Also

- [SETUP_GUIDE.md](../docs/guides/SETUP_GUIDE.md) — Full environment setup
- [DEPLOYMENT_GUIDE.md](../docs/guides/DEPLOYMENT_GUIDE.md) — Build and deploy JAR
