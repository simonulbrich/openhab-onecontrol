# AI Agent Context

This file provides context for AI coding assistants (Cursor, Copilot, etc.) working on the IDS MyRV OpenHAB binding.

---

## Project Overview

**What it is:** An OpenHAB 5 binding for controlling IDS MyRV / OneControl RV systems over CAN bus. Controls lights, relays, tank sensors, HVAC, and other devices using the IDS-CAN protocol.

**Tech stack:** Java 21, Maven, OpenHAB 5 (OSGi bundle)

**Connection modes:** TCP gateway (CAN-to-Ethernet) or SocketCAN (USB CAN adapter on Linux)

---

## Project Structure

```
openhab-binding/
├── pom.xml                    # Maven build (OpenHAB 5.0.2)
├── src/main/java/.../internal/
│   ├── can/                   # CAN layer: CANMessage, CANID, Address
│   ├── protocol/              # COBS encoder/decoder, CRC8
│   ├── gateway/               # CANConnection, GatewayClient, SocketCANClient
│   ├── idscan/                # IDS-CAN: IDSMessage, SessionManager, CommandBuilder, Device
│   ├── handler/               # Bridge + device handlers (LightHandler, etc.)
│   └── discovery/             # Device + gateway discovery
├── src/main/resources/OH-INF/
│   ├── thing/thing-types.xml   # Thing definitions, channels
│   └── binding/binding.xml
├── src/test/java/             # Unit tests (~111 tests)
├── docs/guides/               # User guides (SETUP, DEPLOYMENT, QUICK_START)
├── docs/reference/            # Protocol reference (MESSAGE_TYPES, SESSION_FLOW)
└── scripts/                   # setup-and-test.sh, set-java21.sh, troubleshoot.sh
```

---

## Protocol Layers

1. **CAN** (`can/`) — Raw CAN frames: CANID, Address, payload
2. **COBS** (`protocol/`) — Framing: COBSEncoder, COBSDecoder, CRC8 checksum
3. **IDS-CAN** (`idscan/`) — Application protocol: messages, sessions, commands, device types
4. **OpenHAB** (`handler/`, `discovery/`) — Thing handlers, channels, discovery

When changing protocol behavior, check `docs/reference/` and existing unit tests.

---

## Key Files to Reference

| Need | File |
|------|------|
| Thing types, channels | `src/main/resources/OH-INF/thing/thing-types.xml` |
| Bridge config (TCP vs SocketCAN) | `BridgeConfiguration.java`, `thing-types.xml` |
| CAN connection interface | `CANConnection.java` (implemented by GatewayClient, SocketCANClient) |
| IDS-CAN message format | `IDSMessage.java`, `docs/reference/MESSAGE_TYPES_GUIDE.md` |
| Session/auth flow | `SessionManager.java`, `docs/reference/SESSION_FLOW_ANALYSIS.md` |
| Constants | `IDSMyRVBindingConstants.java` |

---

## Conventions

- **Null safety:** Use `@NonNullByDefault` on classes; prefer `@Nullable` for nullable params/returns
- **Logging:** SLF4J via `LoggerFactory.getLogger(...)`
- **Tests:** JUnit 5, Mockito; run with `mvn clean test`
- **Java:** 21+ only (no older language features)
- **License header:** See `LICENSE_HEADER.txt` for new files

---

## Common Tasks

- **Add a device type:** Update `thing-types.xml`, add handler extending `BaseIDSMyRVDeviceHandler`, add `DeviceType` enum, update discovery
- **Change a channel:** Edit `thing-types.xml` and the corresponding handler
- **Protocol change:** Update `idscan/` or `protocol/`, add/update tests in `src/test/`
- **Build:** `mvn clean package` — JAR in `target/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar`

---

## Docs Quick Links

- **Setup:** `docs/guides/SETUP_GUIDE.md`
- **Deploy:** `docs/guides/DEPLOYMENT_GUIDE.md`
- **Quick start (users):** `docs/guides/QUICK_START.md`
- **SocketCAN:** `docs/guides/SOCKETCAN_QUICKSTART.md`
- **Full catalog:** `DOCS_INDEX.md`

---

## Scripts

- `./scripts/setup-and-test.sh` — Install deps (macOS/Linux) + run tests
- `source scripts/set-java21.sh` — Set JAVA_HOME (must be sourced)
- `./scripts/troubleshoot.sh` — Check deployment; set `OPENHAB_ADDONS`, `OPENHAB_LOG`, `OPENHAB_EVENTS_LOG` if needed
