# OneControl OpenHAB 5 Binding

An OpenHAB 5 binding for controlling IDS MyRV / OneControl devices over the CAN bus. Control lights, relays, sensors, and other RV systems from OpenHAB with a pure Java implementation.

## What Is This?

This binding lets you integrate IDS MyRV (OneControl) RV control systems with OpenHAB. If your RV uses the IDS-CAN protocol for lighting, HVAC, tanks, or other devices, this binding connects OpenHAB to that CAN bus so you can control and monitor devices through rules, dashboards, and voice assistants.

## What It Does

- **Controls devices** – Dimmable lights, RGB lights, tank sensors, latching relays, HVAC, and more
- **Auto-discovers devices** – Scans the CAN bus and presents discovered devices in OpenHAB
- **Two connection modes**:
  - **TCP gateway** – Connect via a CAN-to-Ethernet gateway on your network
  - **SocketCAN** – Connect directly with a USB CAN adapter on Linux
- **Session-based control** – Handles IDS-CAN session authentication for secure device control

## Supported Devices

| Type | Description |
|------|-------------|
| Dimmable Light | On/off, brightness, modes (blink, swell) |
| RGB Light | Color, modes, speed |
| Tank Sensor | Level reporting |
| Latching Relay | Switch, status, fault reporting |
| Momentary H-Bridge | Motor control (awnings, etc.) |
| HVAC | Climate zone control |

## Quick Start

1. **Build:** `mvn clean package`
2. **Deploy:** Copy `target/org.openhab.binding.idsmyrv-5.0.2-SNAPSHOT.jar` to your OpenHAB `addons/` directory
3. **Configure bridge** – Add an IDS MyRV Gateway thing (TCP or SocketCAN), then add device things
4. **Link items** – Create switches, dimmers, etc. and link to channels

## Requirements

- Java 21+
- OpenHAB 5.0+
- Maven 3.8+
- CAN bus access (via TCP gateway or SocketCAN on Linux)

## Documentation

All setup, deployment, and troubleshooting guides are in the `docs/` folder.

### Getting Started

| Document | Purpose |
|----------|---------|
| [SETUP_GUIDE.md](docs/guides/SETUP_GUIDE.md) | Install Java 21, Maven, and build environment |
| [QUICK_START.md](docs/guides/QUICK_START.md) | Add gateway and devices, first-time configuration |
| [DEPLOYMENT_GUIDE.md](docs/guides/DEPLOYMENT_GUIDE.md) | Build JAR, deploy to OpenHAB, hot deploy |

### Connection Options

| Document | Purpose |
|----------|---------|
| [SOCKETCAN_QUICKSTART.md](docs/guides/SOCKETCAN_QUICKSTART.md) | Use a USB CAN adapter (direct connection) |
| [CAN_ADAPTER_SUPPORT.md](docs/guides/CAN_ADAPTER_SUPPORT.md) | TCP vs SocketCAN comparison and setup |

### Troubleshooting

| Document | Purpose |
|----------|---------|
| [GATEWAY_TROUBLESHOOTING.md](docs/guides/GATEWAY_TROUBLESHOOTING.md) | Connection and gateway issues |

### Full Index

See **[DOCS_INDEX.md](DOCS_INDEX.md)** for the complete list of documentation and scripts (including protocol reference and development notes).

**Using an AI coding assistant?** See **[AGENTS.md](AGENTS.md)** for project context (structure, conventions, key files).

## Architecture

```
OpenHAB 5
    └── IDS MyRV Binding
        ├── CAN Protocol (CANMessage, CANID, Address)
        ├── COBS Protocol (Encoder/Decoder with CRC8)
        ├── Gateway / SocketCAN Clients
        ├── IDS-CAN Protocol (Messages, Sessions, Commands)
        └── Thing Handlers (Bridge, Lights, Sensors, etc.)
```

## Configuration Example

### Bridge (TCP Gateway)

```
Thing idsmyrv:gateway:mybridge "RV CAN" [
    connectionType="tcp",
    ipAddress="192.168.1.100",
    port=8888,
    sourceAddress=1
]
```

### Bridge (SocketCAN)

```
Thing idsmyrv:gateway:mybridge "RV CAN" [
    connectionType="socketcan",
    canInterface="can0",
    sourceAddress=1
]
```

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/setup-and-test.sh` | Install dependencies and run unit tests |
| `scripts/set-java21.sh` | Set JAVA_HOME for Java 21 |
| `scripts/troubleshoot.sh` | Check deployment and logs |

## License

Apache License 2.0
