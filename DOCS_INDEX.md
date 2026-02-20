# Documentation & Scripts Index

Inventory of all markdown documentation and scripts used during the IDS MyRV OpenHAB binding build. Use this as the master reference when pushing to remote or onboarding.

---

## 📁 Folder Structure

```
openhab-binding/
├── README.md                 # Main project readme
├── DOCS_INDEX.md             # This file - master catalog
├── AGENTS.md                 # AI agent context for Cursor/Copilot
├── docs/
│   ├── guides/              # User-facing setup & deployment
│   ├── reference/           # Protocol & technical reference
│   └── development/         # Build notes, fix logs, analysis
└── scripts/
    ├── README.md            # Scripts reference (env vars, usage)
    └── *.sh                 # Build, test, troubleshoot
```

---

## 📚 Markdown Files (30 total)

### Primary / Keep at Root
| File | Purpose |
|------|---------|
| `README.md` | Main project overview, architecture, quick start |
| `DOCS_INDEX.md` | This index - catalog of all docs & scripts |
| `AGENTS.md` | AI agent context (Cursor, Copilot, etc.) — project structure, conventions, reference |

### User Guides → `docs/guides/`
| File | Purpose |
|------|---------|
| `SETUP_GUIDE.md` | Build environment setup (Homebrew, Java 21, Maven) |
| `QUICK_START.md` | Add gateway thing, first-time configuration |
| `DEPLOYMENT_GUIDE.md` | Build JAR, deploy to addons, hot deploy |
| `SOCKETCAN_QUICKSTART.md` | SocketCAN adapter setup & config |
| `CAN_ADAPTER_SUPPORT.md` | Dual-mode (TCP vs SocketCAN) documentation |
| `UPDATE_SUMMARY.md` | Summary of SocketCAN feature addition |
| `CONTAINER_DEPLOYMENT.md` | OpenHAB container deployment |
| `GATEWAY_TROUBLESHOOTING.md` | Gateway connection troubleshooting |
| `UI_TROUBLESHOOTING.md` | OpenHAB UI binding visibility |
| `README_JAVA_VERSION.md` | Java 21 requirements |

### Reference → `docs/reference/`
| File | Purpose |
|------|---------|
| `MESSAGE_TYPES_GUIDE.md` | IDS-CAN message types reference |
| `SESSION_FLOW_ANALYSIS.md` | Session/auth flow documentation |

### Development Notes → `docs/development/`
| File | Purpose |
|------|---------|
| `COBS_DECODER_FIX.md` | COBS decoder fix notes |
| `CRITICAL_FIX_CRC8.md` | CRC8 checksum fix |
| `CAN_MESSAGE_FORMAT_FIX.md` | CAN message format fix |
| `READER_THREAD_FIX.md` | Reader thread reconnection fix |
| `FIXED_SOCKET_TIMEOUT.md` | Socket timeout handling |
| `GO_CONNECTION_PARITY.md` | Gateway connection parity fix |
| `FIX_SESSION_MESSAGE_ROUTING.md` | Session message routing fix |
| `SEND_ERROR_HANDLING_FIX.md` | Send error handling |
| `MESSAGE_TYPES_FIX.md` | Message types correction |
| `CLEAN_LOGGING_UPDATE.md` | Logging cleanup |
| `FINAL_SOLUTION.md` | Final solution notes |
| `WORKAROUND.md` | Workaround documentation |
| `PHASE2_COMPLETE.md` | Phase 2 completion notes |
| `GATEWAY_CLIENT_TESTING.md` | Gateway client testing notes |
| `TEST_SUMMARY.md` | Test run summary |
| `TEST_COVERAGE_STATUS.md` | Coverage status |
| `COVERAGE_ANALYSIS.md` | Coverage analysis |

---

## 📜 Scripts (3 total)

| Script | Purpose |
|--------|---------|
| `set-java21.sh` | Set JAVA_HOME to Java 21 for Maven builds |
| `setup-and-test.sh` | Install Homebrew/Maven/Java 21, run `mvn clean test` |
| `troubleshoot.sh` | Check JAR deployment, bundle status, logs |

→ All in `scripts/`

---

## Script Usage

Run from project root. All scripts support `--help`:

```bash
source scripts/set-java21.sh          # Must be sourced; sets JAVA_HOME to Java 21
./scripts/setup-and-test.sh          # Install deps + run tests (macOS) or tests only (Linux)
./scripts/troubleshoot.sh            # Check deployment and logs
```

**Environment variables** (`troubleshoot.sh`): `OPENHAB_ADDONS`, `OPENHAB_LOG`, `OPENHAB_EVENTS_LOG` — override if your paths differ. See `scripts/README.md`.

---

## .gitignore

See project root `.gitignore` for Java/Maven/IDE exclusions (target/, *.jar, .idea/, .vscode/, etc.).
