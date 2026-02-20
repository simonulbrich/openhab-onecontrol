# CAN Adapter Support

This document describes the dual-mode CAN bus connectivity options available in the IDS MyRV OpenHAB binding.

## Connection Modes

The binding now supports two connection modes:

### 1. TCP Gateway Mode (Original)
Connect to the CAN bus via a TCP/IP gateway (CAN-to-Ethernet bridge).

**Use when:**
- Your OpenHAB server is not directly connected to the CAN bus
- You want to access the CAN bus over the network
- You're using the existing gateway infrastructure

**Configuration:**
```
Thing idsmyrv:gateway:mybridge "My RV CAN Bridge" [
    connectionType="tcp",
    ipAddress="192.168.1.100",
    port=8888,
    sourceAddress=1,
    verbose=false
]
```

### 2. SocketCAN Mode (New)
Connect directly to the CAN bus via a SocketCAN interface on Linux.

**Use when:**
- Your OpenHAB server has a direct CAN adapter (USB CAN adapter, etc.)
- You want lower latency and no network dependency
- You're running OpenHAB on Linux

**Configuration:**
```
Thing idsmyrv:gateway:mybridge "My RV CAN Bridge" [
    connectionType="socketcan",
    canInterface="can0",
    sourceAddress=1,
    verbose=false
]
```

## Hardware Requirements for SocketCAN Mode

### Supported CAN Adapters
Any Linux-compatible CAN adapter that supports SocketCAN, including:
- **USB CAN adapters** (e.g., PEAK PCAN-USB, Kvaser USBcan, CANable)
- **Embedded CAN controllers** (e.g., on Raspberry Pi with MCP2515 HAT)
- **PCIe CAN cards**

### Setting Up Your CAN Adapter on Linux

1. **Load the CAN kernel modules:**
   ```bash
   sudo modprobe can
   sudo modprobe can_raw
   ```

2. **Configure your CAN interface:**
   ```bash
   # Set the bitrate (adjust to match your bus, typically 250000 or 500000)
   sudo ip link set can0 type can bitrate 250000
   
   # Bring the interface up
   sudo ip link set can0 up
   ```

3. **Make it persistent across reboots:**
   Create `/etc/network/interfaces.d/can0`:
   ```
   auto can0
   iface can0 can static
       bitrate 250000
       up ip link set $IFACE up
       down ip link set $IFACE down
   ```

4. **Verify the interface is working:**
   ```bash
   ip -details link show can0
   candump can0  # Monitor traffic
   ```

### Testing with Virtual CAN (Development/Testing)

For development or testing without physical hardware:

```bash
# Load the vcan module
sudo modprobe vcan

# Create a virtual CAN interface
sudo ip link add dev vcan0 type vcan
sudo ip link set up vcan0

# Test with
candump vcan0  # In one terminal
cansend vcan0 123#DEADBEEF  # In another
```

## Configuration Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `connectionType` | String | Yes | `"tcp"` | Connection mode: `"tcp"` or `"socketcan"` |
| `ipAddress` | String | TCP only | - | IP address of the TCP gateway |
| `port` | Integer | TCP only | 8888 | TCP port of the gateway |
| `canInterface` | String | SocketCAN only | `"can0"` | SocketCAN interface name |
| `sourceAddress` | Integer | Yes | 1 | Source address on the CAN bus (0-255) |
| `verbose` | Boolean | No | false | Enable verbose logging |

## Migration from TCP to SocketCAN

If you're migrating from TCP gateway mode to SocketCAN:

1. Install and configure your CAN adapter hardware
2. Update your Thing configuration:
   - Change `connectionType` from `"tcp"` to `"socketcan"`
   - Set `canInterface` to your CAN interface name (e.g., `"can0"`)
   - Remove or ignore `ipAddress` and `port` (not used in SocketCAN mode)
3. Restart OpenHAB or reinitialize the bridge
4. Verify connectivity in the OpenHAB logs

## Performance Considerations

### TCP Gateway Mode
- **Latency:** Network round-trip time + gateway processing
- **Reliability:** Depends on network stability
- **Flexibility:** Can be accessed from multiple clients on the network

### SocketCAN Mode
- **Latency:** Direct access, minimal overhead (microseconds)
- **Reliability:** Direct hardware connection, no network dependencies
- **Flexibility:** Only accessible from the local system

## Troubleshooting

### SocketCAN: "Failed to open interface"
- Verify the interface exists: `ip link show can0`
- Check if it's up: `ip link show can0 | grep UP`
- Ensure proper permissions (OpenHAB may need elevated permissions)

### SocketCAN: "No such device"
- The CAN adapter may not be detected
- Check `dmesg` for hardware detection messages
- Verify kernel modules are loaded: `lsmod | grep can`

### TCP: "Connection refused"
- Verify the gateway is running and accessible
- Check firewall rules
- Ensure the IP address and port are correct

### Messages not being received
- Verify the correct bitrate is set (must match the bus)
- Check CAN bus termination (120Ω resistors at both ends)
- Monitor the interface with `candump` to see if traffic is flowing

## Architecture

The binding uses a `CANConnection` interface that abstracts the underlying connection mechanism:

```
CANConnection (interface)
├── GatewayClient (TCP/IP)
│   ├── Socket connection
│   ├── COBS encoding/decoding
│   └── CRC8 validation
└── SocketCANClient (Direct)
    ├── JavaCAN library
    ├── SocketCAN interface
    └── Native CAN frames
```

Both implementations provide:
- Automatic reconnection on errors
- Thread-safe message sending
- Callback-based message handling
- Periodic keepalive messages (NETWORK broadcast)

## Dependencies

The SocketCAN support requires:
- **JavaCAN library** (tel.schich:javacan:3.3.0)
- **Linux with SocketCAN support** (kernel 2.6.25+)
- **CAN hardware** (physical or virtual interface)

TCP mode has no additional dependencies beyond Java networking.

## License

Same as the main binding license.

